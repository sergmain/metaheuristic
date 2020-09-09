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
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.function.SimpleFunctionDefinition;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
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
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class FunctionService {

    private static final String SEE_MORE_INFO = "See https://docs.metaheuristic.ai/p/function#configuration.\n";
    private static final YamlSchemeValidator<String> YAML_SCHEME_VALIDATOR = new YamlSchemeValidator<> (
            "functions",
            List.of("code", "env", "file", "params", "metas", "skipParams", "sourcing", "type"),
            List.of(),
            SEE_MORE_INFO, List.of("1"),
            "the config file functions.yaml",
            (es)-> es
    );

    private final Globals globals;
    private final FunctionRepository functionRepository;
    private final FunctionCache functionCache;
    private final FunctionDataService functionDataService;

    public @Nullable Function findByCode(String functionCode) {
        Long id = functionRepository.findIdByCode(functionCode);
        if (id==null) {
            return null;
        }
        return functionCache.findById(id);
    }

    public static void sortExperimentFunctions(List<Function> functions) {
        functions.sort(FunctionService::experimentFunctionComparator);
    }

    private static int experimentFunctionComparator(Function o1, Function o2) {
        if (o1.getType().equals(o2.getType())) {
            return 0;
        }
        switch (o1.getType().toLowerCase()) {
            case CommonConsts.FIT_TYPE:
                return -1;
            case CommonConsts.PREDICT_TYPE:
                return CommonConsts.FIT_TYPE.equals(o2.getType().toLowerCase()) ? 1 : -1;
            case CommonConsts.CHECK_FITTING_TYPE:
                return 1;
            default:
                return 0;
        }
    }

    public @Nullable TaskParamsYaml.FunctionConfig getFunctionConfig(SimpleFunctionDefinition functionDef) {
        TaskParamsYaml.FunctionConfig functionConfig = null;
        if(StringUtils.isNotBlank(functionDef.getCode())) {
            Function function = findByCode(functionDef.getCode());
            if (function != null) {
                functionConfig = TaskParamsUtils.toFunctionConfig(function.getFunctionConfig(true));
                boolean paramsAsFile = MetaUtils.isTrue(functionConfig.metas, ConstsApi.META_MH_FUNCTION_PARAMS_AS_FILE_META);
                if (paramsAsFile) {
                    throw new NotImplementedException("#295.020 mh.function-params-as-file==true isn't supported right now");
                }
                if (!functionConfig.skipParams) {
                    // TODO 2019-10-09 need to handle a case when field 'params'
                    //  contains actual code (mh.function-params-as-file==true)
                    functionConfig.params = produceFinalCommandLineParams(functionConfig.params, functionDef.getParams());
                }
            } else {
                log.warn("#295.040 Can't find function for code {}", functionDef.getCode());
            }
        }
        return functionConfig;
    }

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

    void loadFunctionsRecursively(List<FunctionApiData.FunctionConfigStatus> statuses, File startDir) throws IOException {
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
    List<FunctionApiData.FunctionConfigStatus> loadFunctionsFromDir(File srcDir) throws IOException {
        File yamlConfigFile = new File(srcDir, "functions.yaml");
        if (!yamlConfigFile.exists()) {
            log.error("#295.080 File 'functions.yaml' wasn't found in dir {}", srcDir.getAbsolutePath());
            return Collections.emptyList();
        }

        String cfg = FileUtils.readFileToString(yamlConfigFile, StandardCharsets.UTF_8);
        String errorString = YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
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
                    function = new Function();
                    storeFunction(functionConfig, file, function);
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

    private void storeFunction(FunctionConfigListYaml.FunctionConfig functionConfig, @Nullable File file, Function function) throws IOException {
        function.code = functionConfig.code;
        function.type = functionConfig.type;
        FunctionConfigYaml scy = FunctionCoreUtils.to(functionConfig);
        function.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(scy);
        functionCache.save(function);
        if (file != null) {
            try (InputStream inputStream = new FileInputStream(file)) {
                String functionCode = function.getCode();
                functionDataService.save(inputStream, file.length(), functionCode);
            }
        }
    }
}
