/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.function;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.data.FunctionData;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.function.SimpleFunctionDefinition;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.*;
import ai.metaheuristic.commons.utils.checksum.CheckSumAndSignatureStatus;
import ai.metaheuristic.commons.utils.checksum.ChecksumWithSignatureUtils;
import ai.metaheuristic.commons.yaml.YamlSchemeValidator;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import ai.metaheuristic.commons.yaml.function_list.FunctionConfigListYaml;
import ai.metaheuristic.commons.yaml.function_list.FunctionConfigListYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.*;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FunctionTopLevelService {

    private static final String SEE_MORE_INFO = "See https://docs.metaheuristic.ai/p/function#configuration.\n";
    public static final YamlSchemeValidator<String> FUNCTION_CONFIG_LIST_YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
            "functions",
            List.of("code", "env", "file", "git", "params", "metas", "skipParams", "sourcing", "type", "checksumMap"),
            List.of(),
            SEE_MORE_INFO, List.of("1"),
            "the config file functions.yaml",
            (es)-> es
    );

    private final Globals globals;
    private final FunctionRepository functionRepository;
    private final FunctionCache functionCache;
    private final FunctionService functionService;
    private final FunctionDataService functionDataService;

    public static String produceFinalCommandLineParams(@Nullable String functionConfigParams, @Nullable String functionDefParams) {
        String s;
        if (!S.b(functionConfigParams) && !S.b(functionDefParams)) {
            s = functionConfigParams + ' ' + functionDefParams;
        }
        else if (S.b(functionConfigParams) && !S.b(functionDefParams)) {
            s = functionDefParams;
        }
        else {
            s = functionConfigParams;
        }
        return S.b(s) ? "" : s;
    }

    public FunctionData.FunctionsResult getFunctions() {
        FunctionData.FunctionsResult result = new FunctionData.FunctionsResult();
        result.functions = functionRepository.findAll();
        result.functions.sort((o1, o2)->o2.getId().compareTo(o1.getId()));
        result.assetMode = globals.assetMode;
        return result;
    }

    public OperationStatusRest deleteFunctionById(Long id) {
        log.info("Start deleting function with id: {}", id );
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.005 Can't delete function while 'replicated' mode of asset is active");
        }
        final Function function = functionCache.findById(id);
        if (function == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.010 function wasn't found, functionId: " + id);
        }
        functionCache.delete(function.getId());
        functionDataService.deleteByFunctionCode(function.getCode());
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest uploadFunction(@Nullable final MultipartFile file) {
        if (globals.assetMode== EnumsApi.DispatcherAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.020 Can't upload function while 'replicated' mode of asset is active");
        }
        if (file==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.025 File with function wasn't selected");
        }
        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.030 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.040 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), ZIP_EXT, YAML_EXT, YML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.050 only '.zip', '.yml' and '.yaml' files are supported, filename: " + originFilename);
        }

        final String location = System.getProperty("java.io.tmpdir");

        File tempDir = null;
        try {
            tempDir = DirUtils.createTempDir("function-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#424.060 can't create temporary directory in " + location);
            }
            final File zipFile = new File(tempDir, "functions" + ext);
            log.debug("Start storing an uploaded function to disk");
            try(OutputStream os = new FileOutputStream(zipFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            List<FunctionApiData.FunctionConfigStatus> statuses;
            if (ZIP_EXT.equals(ext)) {
                log.debug("Start unzipping archive");
                ZipUtils.unzipFolder(zipFile, tempDir);
                log.debug("Start loading function data to db");
                statuses = new ArrayList<>();
                loadFunctionsRecursively(statuses, tempDir);
            }
            else {
                log.debug("Start loading function data to db");
                statuses = loadFunctionsFromDir(tempDir);
            }
            if (isError(statuses)) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        toErrorMessages(statuses));
            }
        }
        catch (Exception e) {
            log.error("Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#424.070 can't load functions, Error: " + e.toString());
        }
        finally {
            DirUtils.deleteAsync(tempDir);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private List<String> toErrorMessages(List<FunctionApiData.FunctionConfigStatus> statuses) {
        return statuses.stream().filter(o->!o.isOk).map(o->o.error).collect(Collectors.toList());
    }

    private boolean isError(List<FunctionApiData.FunctionConfigStatus> statuses) {
        return statuses.stream().filter(o->!o.isOk).findFirst().orElse(null)!=null;
    }

    private void loadFunctionsRecursively(List<FunctionApiData.FunctionConfigStatus> statuses, File startDir) throws IOException {
        final File[] dirs = startDir.listFiles(File::isDirectory);

        if (dirs!=null) {
            for (File dir : dirs) {
                log.info("#295.060 Load functions from {}", dir.getPath());
                statuses.addAll(loadFunctionsFromDir(dir));
                loadFunctionsRecursively(statuses, dir);
            }
        }
    }

    /**
     * load functions from directory
     *
     * @param srcDir File
     */
    @SuppressWarnings("Duplicates")
    private List<FunctionApiData.FunctionConfigStatus> loadFunctionsFromDir(File srcDir) throws IOException {
        File yamlConfigFile = new File(srcDir, "functions.yaml");
        if (!yamlConfigFile.exists()) {
            log.error("#295.080 File 'functions.yaml' wasn't found in dir {}", srcDir.getAbsolutePath());
            return Collections.emptyList();
        }

        String cfg = FileUtils.readFileToString(yamlConfigFile, StandardCharsets.UTF_8);
        String errorString = FUNCTION_CONFIG_LIST_YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        if (errorString!=null) {
            return List.of(new FunctionApiData.FunctionConfigStatus(false, errorString));
        }

        FunctionConfigListYaml functionConfigList = FunctionConfigListYamlUtils.BASE_YAML_UTILS.to(cfg);
        List<FunctionApiData.FunctionConfigStatus> statuses = new ArrayList<>();
        for (FunctionConfigListYaml.FunctionConfig functionConfig : functionConfigList.functions) {
            FunctionApiData.FunctionConfigStatus status = null;
            try {
                status = FunctionCoreUtils.validate(functionConfig);
                if (!status.isOk) {
                    statuses.add(status);
                    log.error(status.error);
                    continue;
                }
                String sum=null;
                File file = null;
                if (globals.isFunctionSignatureRequired) {
                    // at 2020-09-02, only HashAlgo.SHA256WithSignature is supported for signing
                    if (functionConfig.checksumMap==null || functionConfig.checksumMap.keySet().stream().noneMatch(o->o==EnumsApi.HashAlgo.SHA256WithSignature)) {
                        String es = S.f("#295.100 Global isFunctionSignatureRequired==true but function %s isn't signed with HashAlgo.SHA256WithSignature", functionConfig.code);
                        statuses.add(new FunctionApiData.FunctionConfigStatus(false, es));
                        log.error(es);
                        continue;
                    }
                    String data = functionConfig.checksumMap.entrySet().stream()
                            .filter(o -> o.getKey() == EnumsApi.HashAlgo.SHA256WithSignature)
                            .findFirst()
                            .map(Map.Entry::getValue).orElse(null);

                    if (S.b(data)) {
                        String es = S.f("#295.120 Global isFunctionSignatureRequired==true but function %s has empty SHA256WithSignature value", functionConfig.code);
                        status = new FunctionApiData.FunctionConfigStatus(false, es);
                        log.warn(es);
                        continue;
                    }
                    ChecksumWithSignatureUtils.ChecksumWithSignature checksumWithSignature = ChecksumWithSignatureUtils.parse(data);
                    if (S.b(checksumWithSignature.checksum) || S.b(checksumWithSignature.signature)) {
                        String es = S.f("#295.140 Global isFunctionSignatureRequired==true but function %s has empty checksum or signature", functionConfig.code);
                        status = new FunctionApiData.FunctionConfigStatus(false, es);
                        log.warn(es);
                        continue;
                    }

                    switch(functionConfig.sourcing) {
                        case dispatcher:
                            file = new File(srcDir, functionConfig.file);
                            if (!file.exists()) {
                                final String es = "#295.160 Function has a sourcing as 'dispatcher' but file " + functionConfig.file + " wasn't found.";
                                status = new FunctionApiData.FunctionConfigStatus(false, es);
                                log.warn(es+" Temp dir: " + srcDir.getAbsolutePath());
                                continue;
                            }
                            try (InputStream inputStream = new FileInputStream(file)) {
                                sum = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, inputStream);
                            }
                            break;
                        case processor:
                        case git:
                            String s = FunctionCoreUtils.getDataForChecksumWhenGitSourcing(functionConfig);
                            sum = Checksum.getChecksum(EnumsApi.HashAlgo.SHA256, new ByteArrayInputStream(s.getBytes()));
                            break;
                    }
                    if (!checksumWithSignature.checksum.equals(sum)) {
                        String es = S.f("#295.180 Function %s has wrong checksum", functionConfig.code);
                        status = new FunctionApiData.FunctionConfigStatus(false, es);
                        log.warn(es);
                        continue;
                    }
                    CheckSumAndSignatureStatus.Status st = ChecksumWithSignatureUtils.isValid(sum.getBytes(), checksumWithSignature.signature, globals.dispatcherPublicKey);
                    if (st!= CheckSumAndSignatureStatus.Status.correct) {
                        if (!checksumWithSignature.checksum.equals(sum)) {
                            String es = S.f("#295.200 Function %s has wrong signature", functionConfig.code);
                            status = new FunctionApiData.FunctionConfigStatus(false, es);
                            log.warn(es);
                            continue;
                        }
                    }
                }

                Function function = functionRepository.findByCodeForUpdate(functionConfig.code);
                // there is a function with the same code
                if (function !=null) {
                    status = new FunctionApiData.FunctionConfigStatus(false,
                            "#295.220 Replacing of existing function isn't supported any more, need to upload a function as a new one. Function code: "+ function.code);
                    //noinspection UnnecessaryContinue
                    continue;
                }
                else {
                    if (file != null) {
                        try (InputStream inputStream = new FileInputStream(file)) {
                            FunctionConfigYaml scy = FunctionCoreUtils.to(functionConfig);
                            functionService.createFunctionWithData(scy, inputStream, file.length());
                        }
                    }
                    else {
                        status = new FunctionApiData.FunctionConfigStatus(false,"#295.230 Fatal error - file is null ");

                    }
                }
            }
            catch(VariableSavingException e) {
                status = new FunctionApiData.FunctionConfigStatus(false, e.getMessage());
            }
            catch(Throwable th) {
                final String es = "#295.240 Error " + th.getClass().getName() + " while processing function '" + functionConfig.code + "': " + th.toString();
                log.error(es, th);
                status = new FunctionApiData.FunctionConfigStatus(false, es);
            }
            finally {
                statuses.add(status!=null
                        ? status
                        : new FunctionApiData.FunctionConfigStatus(false,
                        "#295.260 MetricsStatus of function "+ functionConfig.code+" is unknown, this status needs to be investigated"));
            }
        }
        return statuses;
    }

    @Nullable
    public TaskParamsYaml.FunctionConfig getFunctionConfig(SimpleFunctionDefinition functionDef) {
        TaskParamsYaml.FunctionConfig functionConfig = null;
        if(StringUtils.isNotBlank(functionDef.getCode())) {
            Function function = findByCode(functionDef.getCode());
            if (function != null) {
                FunctionConfigYaml temp = FunctionConfigYamlUtils.BASE_YAML_UTILS.to(function.params);
                functionConfig = TaskParamsUtils.toFunctionConfig(temp);
                if (!functionConfig.skipParams) {
                    boolean paramsAsFile = MetaUtils.isTrue(functionConfig.metas, ConstsApi.META_MH_FUNCTION_PARAMS_AS_FILE_META);
                    if (paramsAsFile) {
                        // TODO 2019-10-09 need to handle a case when field 'params'
                        //  contains actual code (mh.function-params-as-file==true)
                        //  2020-09-12 need to add a new field 'content' which will hold the content of file
                        // functionConfig.params = produceFinalCommandLineParams(null, functionDef.getParams());
                        if (!S.b(functionDef.getParams())) {
                            log.error("#295.035 defining parameters in SourceCode " +
                                    "and using FUnction.params as a holder for a code isn't supported right now. " +
                                    "Will be executed without a parameter from SourceCode");
                        }
                    }
                    else {
                        functionConfig.params = produceFinalCommandLineParams(functionConfig.params, functionDef.getParams());
                    }
                }
            } else {
                log.warn("#295.040 Can't find function for code {}", functionDef.getCode());
            }
        }
        return functionConfig;
    }

    @Nullable
    public Function findByCode(String functionCode) {
        Long id = functionRepository.findIdByCode(functionCode);
        if (id==null) {
            return null;
        }
        return functionCache.findById(id);
    }


}
