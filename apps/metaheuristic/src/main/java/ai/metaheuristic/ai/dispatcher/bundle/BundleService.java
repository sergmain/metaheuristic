/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.data.BundleData;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.exceptions.BundleProcessingException;
import ai.metaheuristic.ai.exceptions.ExecContextTooManyInstancesException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYaml;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import static ai.metaheuristic.ai.Consts.YAML_EXT;
import static ai.metaheuristic.ai.Consts.YML_EXT;

/**
 * @author Serge
 * Date: 7/26/2021
 * Time: 10:46 PM
 */
@SuppressWarnings({"DuplicatedCode"})
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class BundleService {

    private static final Pattern ZIP_CHARS_PATTERN = Pattern.compile("^[/\\\\A-Za-z0-9._-]*$");
    public static final Function<ZipEntry, ZipUtils.ValidationResult> VALIDATE_ZIP_FUNCTION = BundleService::isZipEntityNameOk;
    public static final Function<ZipEntry, ZipUtils.ValidationResult> VALIDATE_ZIP_ENTRY_SIZE_FUNCTION = BundleService::isZipEntitySizeOk;

    private record BundleLocation(Path dir, Path zipFile) {}

    private final FunctionService functionService;
    private final SourceCodeService sourceCodeService;

    public BundleData.UploadingStatus uploadFromFile(final MultipartFile file, final DispatcherContext dispatcherContext) {
        if (Consts.ID_1.equals(dispatcherContext.getCompanyId())) {
            return new BundleData.UploadingStatus("971.030 Batch can't be created in company #1");
        }
        if (file.getSize()==0) {
            return new BundleData.UploadingStatus("971.035 Can't upload bundle because uploaded file has a zero length");
        }

        log.info("971.055 Staring of uploadFromFile(), file: {}, size: {}", file.getOriginalFilename(), file.getSize());

        BundleLocation bundleLocation;
        try {
            // TODO 2021.03.13 add a support of
            //  CleanerInfo resource = new CleanerInfo();
            Path tempDir = DirUtils.createMhTempPath("uploaded-bundle-");
            if (tempDir==null) {
                return new BundleData.UploadingStatus( "971.090 Can't create a temporary dir");
            }
            Path tempFile = tempDir.resolve("zip.zip");
            file.transferTo(tempFile);
            if (file.getSize()!=Files.size(tempFile)) {
                return new BundleData.UploadingStatus( "971.125 System error while preparing data. The sizes of files are different");
            }
            List<String> errors = ZipUtils.validate(tempFile, VALIDATE_ZIP_ENTRY_SIZE_FUNCTION);
            if (!errors.isEmpty()) {
                errors.add(0, "971.144 Batch can't be created because of following errors:");
                return new BundleData.UploadingStatus( errors);
            }
            bundleLocation = new BundleLocation(tempDir, tempFile);
        } catch (IOException e) {
            return new BundleData.UploadingStatus("971.140 Can't create a new temp zip file");
        }

        try {
            BundleData.UploadingStatus status = processBundle(bundleLocation, dispatcherContext);
            return status;
        }
        catch (ExecContextTooManyInstancesException e) {
            String es = S.f("971.255 Too many instances of SourceCode '%s', max allowed: %d, current count: %d", e.sourceCodeUid, e.max, e.curr);
            log.warn(es);
            return new BundleData.UploadingStatus(es);
        }
        catch (Throwable th) {
            String es = "971.260 can't load bundle file, error: " + th.getMessage() + ", class: " + th.getClass();
            log.error(es, th);
            return new BundleData.UploadingStatus(es);
        }
        finally {
            DirUtils.deletePathAsync(bundleLocation.dir);
        }
    }

    private BundleData.UploadingStatus processBundle(BundleLocation bundleLocation, DispatcherContext dispatcherContext) throws IOException {
        Path data = bundleLocation.dir.resolve("data");
        Files.createDirectories(data);
        ZipUtils.unzipFolder(bundleLocation.zipFile, data);

        Path bundleCfg = data.resolve(CommonConsts.BUNDLE_CFG_YAML);
        if (Files.notExists(bundleCfg)) {
            throw new BundleProcessingException(S.f("File %s wasn't found in bundle archive", CommonConsts.BUNDLE_CFG_YAML));
        }

        String yaml = Files.readString(bundleCfg);
        BundleCfgYaml bundleCfgYaml = BundleCfgYamlUtils.UTILS.to(yaml);
        BundleData.UploadingStatus status = new BundleData.UploadingStatus();

        processFunctions(bundleCfgYaml, data, status);
        processSourceCodes(bundleCfgYaml, data, status, dispatcherContext);

        return status;
    }

    private void processSourceCodes(BundleCfgYaml bundleCfgYaml, Path data, BundleData.UploadingStatus status, DispatcherContext dispatcherContext) throws IOException {
        for (BundleCfgYaml.BundleConfig bundleConfig : bundleCfgYaml.bundleConfig) {
            if (bundleConfig.type!= EnumsApi.BundleItemType.sourceCode) {
                continue;
            }
            Path p = data.resolve(bundleConfig.path);
            if (Files.notExists(p)) {
                log.error("invalid record in bundle-cfg.yaml, path {} doesn't exist", bundleConfig.path);
                continue;
            }

            Files.walkFileTree(p, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) throws IOException {
                    final String filename = p.getFileName().toString();
                    String ext = StrUtils.getExtension(filename);
                    if (ext == null || !StringUtils.equalsAny(ext.toLowerCase(), YAML_EXT, YML_EXT)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String yaml = Files.readString(p);
                    SourceCodeApiData.SourceCodeResult codeResult = sourceCodeService.createSourceCode(yaml, dispatcherContext.getCompanyId());
                    status.addErrorMessages(codeResult.getErrorMessagesAsList());
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void processFunctions(BundleCfgYaml bundleCfgYaml, Path data, BundleData.UploadingStatus status) {
        for (BundleCfgYaml.BundleConfig bundleConfig : bundleCfgYaml.bundleConfig) {
            if (bundleConfig.type!= EnumsApi.BundleItemType.function) {
                continue;
            }
            Path f = data.resolve(bundleConfig.path);
            if (Files.notExists(f)) {
                log.error("invalid record in bundle-cfg.yaml, path {} doesn't exist", bundleConfig.path);
                continue;
            }
            functionService.loadFunction(f, status);
        }
    }

    private static ZipUtils.ValidationResult isZipEntityNameOk(ZipEntry zipEntry) {
        Matcher m = ZIP_CHARS_PATTERN.matcher(zipEntry.getName());
        return m.matches() ? ZipUtils.VALIDATION_RESULT_OK : new ZipUtils.ValidationResult("971.010 Wrong name of file in zip file. Name: "+zipEntry.getName());
    }

    private static ZipUtils.ValidationResult isZipEntitySizeOk(ZipEntry zipEntry) {
        if (zipEntry.isDirectory()) {
            return ZipUtils.VALIDATION_RESULT_OK;
        }
        return zipEntry.getSize()>0 ? ZipUtils.VALIDATION_RESULT_OK : new ZipUtils.ValidationResult(
                "971.013 File "+zipEntry.getName()+" has a zero length.");
    }

}
