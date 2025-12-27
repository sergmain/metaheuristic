/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.dispatcher.bundle;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.exceptions.ExecContextTooManyInstancesException;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.ai.mhbp.api.ApiService;
import ai.metaheuristic.ai.mhbp.auth.AuthService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.account.UserContext;
import ai.metaheuristic.commons.exceptions.BundleProcessingException;
import ai.metaheuristic.commons.utils.BundleUtils;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYaml;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYamlUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.file.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * @author Serge
 * Date: 7/26/2021
 * Time: 10:46 PM
 */
@SuppressWarnings({"DuplicatedCode"})
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class BundleService {

    private final Globals globals;
    private final FunctionService functionService;
    private final SourceCodeService sourceCodeService;
    private final ApiService apiService;
    private final AuthService authService;

    private static final Pattern ZIP_CHARS_PATTERN = Pattern.compile("^[/\\\\A-Za-z0-9._-]*$");
    public static final Function<ZipEntry, ZipUtils.ValidationResult> VALIDATE_ZIP_FUNCTION = BundleService::isZipEntityNameOk;
    public static final Function<ZipEntry, ZipUtils.ValidationResult> VALIDATE_ZIP_ENTRY_SIZE_FUNCTION = BundleService::isZipEntitySizeOk;

    public BundleData.UploadingStatus uploadFromGit(GitInfo gitInfo, UserContext context) {
//      --git-repo https://github.com/sergmain/metaheuristic-assets.git --git-branch master --git-commit HEAD --git-path common-bundle

        int j=11;

        try {
            BundleData.Cfg cfg = new BundleData.Cfg(null, globals.dispatcherGitRepoPath, gitInfo);
            BundleUtils.initRepo(cfg);
            Path tempBundleDir = DirUtils.createMhTempPath("bundle-");
            if (tempBundleDir==null) {
                return new BundleData.UploadingStatus("971.020 Can't create temporary dir");
            }
            Path uploadPath = tempBundleDir.resolve("upload");
            Files.createDirectories(uploadPath);
            cfg.initOtherPaths(uploadPath);

            BundleCfgYaml bundleCfgYaml = BundleUtils.readBundleCfgYaml(cfg, CommonConsts.MH_BUNDLE_YAML);
            Path bundleZipFile = BundleUtils.createBundle(cfg, bundleCfgYaml);

            BundleData.BundleLocation bundleLocation = new BundleData.BundleLocation(uploadPath, bundleZipFile);
            Path processingPath = tempBundleDir.resolve("processing");
            Files.createDirectories(processingPath);

            return processBundle(processingPath, bundleLocation, context);

        } catch (BundleProcessingException e) {
            return new BundleData.UploadingStatus("971.040 Error while processing git repo "+ gitInfo.repo+", error: " + e.message);
        } catch (Throwable e) {
            return new BundleData.UploadingStatus("971.060 Error while processing git repo "+ gitInfo.repo+", error: " + e.getMessage());
        }
    }

    public BundleData.UploadingStatus uploadFromFile(final MultipartFile file, final UserContext dispatcherContext) {
        if (Consts.ID_1.equals(dispatcherContext.getCompanyId())) {
            return new BundleData.UploadingStatus("971.080 Batch can't be created in company #1");
        }
        if (file.getSize()==0) {
            return new BundleData.UploadingStatus("971.100 Can't upload bundle because uploaded file has a zero length");
        }

        log.info("971.120 Staring of uploadFromFile(), file: {}, size: {}", file.getOriginalFilename(), file.getSize());

        BundleData.BundleLocation bundleLocation;
        try {
            // TODO 2021.03.13 add a support of
            //  CleanerInfo resource = new CleanerInfo();
            Path tempDir = DirUtils.createMhTempPath("uploaded-bundle-");
            if (tempDir==null) {
                return new BundleData.UploadingStatus( "971.140 Can't create a temporary dir");
            }
            Path uploadPath = tempDir.resolve("upload");
            Files.createDirectories(uploadPath);

            Path tempFile = uploadPath.resolve("zip.zip");
            file.transferTo(tempFile);
            if (file.getSize()!=Files.size(tempFile)) {
                return new BundleData.UploadingStatus( "971.160 System error while preparing data. The sizes of files are different");
            }
            List<String> errors = ZipUtils.validate(tempFile, VALIDATE_ZIP_ENTRY_SIZE_FUNCTION);
            if (!errors.isEmpty()) {
                errors.add(0, "971.180 Batch can't be created because of following errors:");
                return new BundleData.UploadingStatus( errors);
            }
            bundleLocation = new BundleData.BundleLocation(uploadPath, tempFile);
            Path processingPath = tempDir.resolve("processing");
            Files.createDirectories(processingPath);

            return processBundle(processingPath, bundleLocation, dispatcherContext);
        }
        catch (IOException e) {
            return new BundleData.UploadingStatus("971.200 Can't create a new temp zip file");
        }
    }

    private BundleData.UploadingStatus processBundle(Path processingPath, BundleData.BundleLocation bundleLocation, UserContext userContext) {
        try {
            BundleData.UploadingStatus status = processBundleInternal(processingPath, bundleLocation, userContext);
            return status;
        }
        catch (ExecContextTooManyInstancesException e) {
            String es = S.f("971.220 Too many instances of SourceCode '%s', max allowed: %d, current count: %d", e.sourceCodeUid, e.max, e.curr);
            log.warn(es);
            return new BundleData.UploadingStatus(es);
        }
        catch (Throwable th) {
            String es = "971.240 can't load bundle file, error: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            return new BundleData.UploadingStatus(es);
        }
        finally {
            DirUtils.deletePathAsync(bundleLocation.dir);
        }
    }

    private BundleData.UploadingStatus processBundleInternal(Path processingPath, BundleData.BundleLocation bundleLocation, UserContext userContext) throws IOException {
        ZipUtils.unzipFolder(bundleLocation.bundleZipFile, processingPath);

        Path bundleCfg = processingPath.resolve(CommonConsts.MH_BUNDLE_YAML);
        if (Files.notExists(bundleCfg)) {
            throw new BundleProcessingException(S.f("971.260 File %s wasn't found in bundle archive", CommonConsts.MH_BUNDLE_YAML));
        }

        String yaml = Files.readString(bundleCfg);
        BundleCfgYaml bundleCfgYaml = BundleCfgYamlUtils.UTILS.to(yaml);
        BundleData.UploadingStatus status = new BundleData.UploadingStatus();

        processFunctions(bundleCfgYaml, processingPath, status);
        processCommonType(EnumsApi.BundleItemType.sourceCode, bundleCfgYaml, processingPath, status, userContext, this::storeSourceCode);
        processCommonType(EnumsApi.BundleItemType.api, bundleCfgYaml, processingPath, status, userContext, apiService::createApi);
        processCommonType(EnumsApi.BundleItemType.auth, bundleCfgYaml, processingPath, status, userContext, authService::createAuth);

        return status;
    }

    private static void processCommonType(EnumsApi.BundleItemType type, BundleCfgYaml bundleCfgYaml,
                                          Path data, BundleData.UploadingStatus status, UserContext dispatcherContext,
                                          BiFunction<String, UserContext, BaseDataClass> storeCommonTypeFunc) throws IOException {
        for (BundleCfgYaml.BundleConfig bundleConfig : bundleCfgYaml.bundleConfig) {
            if (bundleConfig.type!= type) {
                continue;
            }
            Path p = data.resolve(bundleConfig.path);
            if (Files.notExists(p)) {
                log.error("971.300 invalid record in bundle-cfg.yaml, path {} doesn't exist", bundleConfig.path);
                continue;
            }

            PathUtils.walk(p, BundleUtils.YAML_SUFFIX_FILTER, Integer.MAX_VALUE, false, FileVisitOption.FOLLOW_LINKS)
                .forEach(f-> {
                    try {
                        String yaml = Files.readString(f);
                        BaseDataClass codeResult = storeCommonTypeFunc.apply(yaml, dispatcherContext);
                        status.addErrorMessages(codeResult.getErrorMessagesAsList());
                        status.addInfoMessages(codeResult.getInfoMessagesAsList());
                    } catch (IOException e) {
                        throw new RuntimeException("Error", e);
                    }
                });
        }
    }

    private SourceCodeApiData.SourceCodeResult storeSourceCode(String yaml, UserContext dispatcherContext) {
        return sourceCodeService.createSourceCode(yaml, dispatcherContext.getCompanyId());
    }

    private void processFunctions(BundleCfgYaml bundleCfgYaml, Path data, BundleData.UploadingStatus status) throws IOException {
        for (BundleCfgYaml.BundleConfig bundleConfig : bundleCfgYaml.bundleConfig) {
            if (bundleConfig.type!=EnumsApi.BundleItemType.function) {
                continue;
            }
            Path p = data.resolve(bundleConfig.path);
            if (Files.notExists(p)) {
                log.error("971.320 invalid record in bundle-cfg.yaml, path {} doesn't exist", bundleConfig.path);
                continue;
            }
            PathUtils.walk(p, BundleUtils.FUNCTION_YAML_FILTER, Integer.MAX_VALUE, false, FileVisitOption.FOLLOW_LINKS)
                .forEach(f-> loadFunction(f, status));
        }
    }

    private void loadFunction(Path yamlConfigFile, BundleData.UploadingStatus status) {
        try {
            Path srcDir = yamlConfigFile.getParent();

            String yaml = Files.readString(yamlConfigFile);
            // TODO p3 2023-10-16 after fixing validation of yaml with single element, enable this validation
            //  test about this problem - ai.metaheuristic.commons.yaml.TestYamlSchemeValidator.testOneElement
//            String errorString = FUNCTION_CONFIG_YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(yaml);
//            if (errorString!=null) {
//                return List.of(new FunctionApiData.FunctionConfigStatus(false, errorString));
//            }

            FunctionConfigYaml functionConfigList = FunctionConfigYamlUtils.UTILS.to(yaml);
            functionService.loadFunctionInternal(srcDir, status, functionConfigList);
        }
        catch (VariableSavingException e) {
            status.addErrorMessage(e.getMessage());
        }
        catch(Throwable th) {
            final String es = "971.340 Error " + th.getClass().getName() + " while uploading functions from bundle: " + th.getMessage();
            log.error(es, th);
            status.addErrorMessage(es);
        }
    }

    private static ZipUtils.ValidationResult isZipEntityNameOk(ZipEntry zipEntry) {
        Matcher m = ZIP_CHARS_PATTERN.matcher(zipEntry.getName());
        return m.matches() ? ZipUtils.VALIDATION_RESULT_OK : new ZipUtils.ValidationResult("971.360 Wrong name of file in zip file. Name: "+zipEntry.getName());
    }

    private static ZipUtils.ValidationResult isZipEntitySizeOk(ZipEntry zipEntry) {
        if (zipEntry.isDirectory()) {
            return ZipUtils.VALIDATION_RESULT_OK;
        }
        return zipEntry.getSize()>0 ? ZipUtils.VALIDATION_RESULT_OK : new ZipUtils.ValidationResult(
                "971.380 File "+zipEntry.getName()+" has a zero length.");
    }

}
