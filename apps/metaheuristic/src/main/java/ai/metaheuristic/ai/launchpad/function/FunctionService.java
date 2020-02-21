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
package ai.metaheuristic.ai.launchpad.function;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.ai.launchpad.beans.Function;
import ai.metaheuristic.ai.launchpad.data.FunctionData;
import ai.metaheuristic.ai.launchpad.repositories.FunctionRepository;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.SimpleSelectOption;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.Checksum;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
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
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class FunctionService {

    private final Globals globals;
    private final FunctionRepository functionRepository;
    private final FunctionCache functionCache;
    private final FunctionDataService functionDataService;

    public Function findByCode(String functionCode) {
        Long id = functionRepository.findIdByCode(functionCode);
        if (id==null) {
            return null;
        }
        return functionCache.findById(id);
    }

    public boolean isFunctionVersionOk(int requiredVersion, SourceCodeParamsYaml.FunctionDefForSourceCode snDef) {
        TaskParamsYaml.FunctionConfig sc = getFunctionConfig(snDef);
        return sc != null && (sc.skipParams || requiredVersion <= FunctionCoreUtils.getTaskParamsVersion(sc.metas));
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

    public List<Function> getFunctionsForCodes(List<String> functionCodes) {
        if (functionCodes==null || functionCodes.isEmpty()) {
            return List.of();
        }
        //noinspection UnnecessaryLocalVariable
        List<Function> list = functionRepository.findIdsByCodes(functionCodes).stream()
                .map(functionCache::findById)
                .sorted(FunctionService::experimentFunctionComparator)
                .collect(Collectors.toList());
        return list;
    }

    public TaskParamsYaml.FunctionConfig getFunctionConfig(SourceCodeParamsYaml.FunctionDefForSourceCode functionDef) {
        TaskParamsYaml.FunctionConfig functionConfig = null;
        if(StringUtils.isNotBlank(functionDef.code)) {
            Function function = findByCode(functionDef.code);
            if (function != null) {
                functionConfig = TaskParamsUtils.toFunctionConfig(function.getFunctionConfig(true));
                boolean paramsAsFile = MetaUtils.isTrue(functionConfig.metas, ConstsApi.META_MH_FUNCTION_PARAMS_AS_FILE_META);
                if (paramsAsFile) {
                    throw new NotImplementedException("mh.function-params-as-file==true isn't supported right now");
                }
                if (!functionConfig.skipParams) {
                    // TODO 2019-10-09 need to handle a case when field 'params'
                    //  contains actual code (mh.function-params-as-file==true)
                    if (functionConfig.params!=null && functionDef.params!=null) {
                        functionConfig.params = functionConfig.params + ' ' + functionDef.params;
                    }
                    else if (functionConfig.params == null) {
                        if (functionDef.params != null) {
                            functionConfig.params = functionDef.params;
                        }
                    }
                }
            } else {
                log.warn("#295.010 Can't find function for code {}", functionDef.code);
            }
        }
        return functionConfig;
    }

    public boolean hasType(List<Function> experimentFunctions, String type) {
        if (experimentFunctions ==null || experimentFunctions.isEmpty()) {
            return false;
        }
        for (Function function : experimentFunctions) {
            if (type.equals(function.getType())) {
                return true;
            }
        }
        return false;
    }

    public List<SimpleSelectOption> getSelectOptions(Iterable<Function> functions, List<FunctionData.FunctionCode> functionCodes,
                                                     java.util.function.Function<Function, Boolean> skip) {
        List<SimpleSelectOption> selectOptions = new ArrayList<>();
        for (Function function : functions) {
            boolean isExist=false;
            for (FunctionData.FunctionCode functionCode : functionCodes) {
                if (function.getCode().equals(functionCode.getFunctionCode()) ) {
                    isExist = true;
                    break;
                }
            }
            if (!isExist) {
                if (skip.apply(function)) {
                    continue;
                }
                selectOptions.add( new SimpleSelectOption(function.getCode(), String.format("Type: %s; Code: %s", function.getType(), function.getCode())));
            }
        }
        return selectOptions;
    }

    void loadFunctionsRecursively(List<FunctionApiData.FunctionConfigStatus> statuses, File startDir) throws IOException {
        final File[] dirs = startDir.listFiles(File::isDirectory);

        if (dirs!=null) {
            for (File dir : dirs) {
                log.info("Load functions from {}", dir.getPath());
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
            log.error("#295.020 File 'functions.yaml' wasn't found in dir {}", srcDir.getAbsolutePath());
            return Collections.emptyList();
        }

        String cfg = FileUtils.readFileToString(yamlConfigFile, StandardCharsets.UTF_8);
        FunctionConfigListYaml functionConfigList = FunctionConfigListYamlUtils.BASE_YAML_UTILS.to(cfg);
        List<FunctionApiData.FunctionConfigStatus> statuses = new ArrayList<>();
        for (FunctionConfigListYaml.FunctionConfig functionConfig : functionConfigList.functions) {
//            FunctionConfigYaml functionConfig = FunctionCoreUtils.to(scTemp);
            FunctionApiData.FunctionConfigStatus status = null;
            try {
                status = FunctionCoreUtils.validate(functionConfig);
                if (!status.isOk) {
                    log.error(status.error);
                    continue;
                }
                String sum=null;
                File file = null;
                if (globals.isFunctionChecksumRequired) {
                    switch(functionConfig.sourcing) {
                        case launchpad:
                            file = new File(srcDir, functionConfig.file);
                            if (!file.exists()) {
                                final String es = "#295.030 Function has a sourcing as 'launchpad' but file " + functionConfig.file + " wasn't found.";
                                status = new FunctionApiData.FunctionConfigStatus(false, es);
                                log.warn(es+" Temp dir: " + srcDir.getAbsolutePath());
                                continue;
                            }
                            try (InputStream inputStream = new FileInputStream(file)) {
                                sum = Checksum.getChecksum(EnumsApi.Type.SHA256, inputStream);
                            }
                            functionConfig.info.length = file.length();
                            break;
                        case station:
                        case git:
                            String s = FunctionCoreUtils.getDataForChecksumWhenGitSourcing(functionConfig);
                            sum = Checksum.getChecksum(EnumsApi.Type.SHA256, new ByteArrayInputStream(s.getBytes()));
                            break;
                    }
                }

                Function function = functionRepository.findByCodeForUpdate(functionConfig.code);
                // there is a function with the same code
                if (function !=null) {
                    status = new FunctionApiData.FunctionConfigStatus(false,
                            "#295.040 Updating of function isn't supported any more, need to upload a function as a new version. Function code: "+ function.code);
                    //noinspection UnnecessaryContinue
                    continue;
                }
                else {
                    function = new Function();
                    storeFunction(functionConfig, sum, file, function);
                }
            }
            catch(VariableSavingException e) {
                status = new FunctionApiData.FunctionConfigStatus(false, e.getMessage());
            }
            catch(Throwable th) {
                final String es = "#295.050 Error " + th.getClass().getName() + " while processing function '" + functionConfig.code + "': " + th.toString();
                log.error(es, th);
                status = new FunctionApiData.FunctionConfigStatus(false, es);
            }
            finally {
                statuses.add(status!=null
                        ? status
                        : new FunctionApiData.FunctionConfigStatus(false,
                        "#295.060 MetricsStatus of function "+ functionConfig.code+" is unknown, this status needs to be investigated"));
            }
        }
        return statuses;
    }

    private void storeFunction(FunctionConfigListYaml.FunctionConfig functionConfig, String sum, File file, Function function) throws IOException {
        setChecksum(functionConfig, sum);
        function.code = functionConfig.code;
        function.type = functionConfig.type;
        FunctionConfigYaml scy = FunctionCoreUtils.to(functionConfig);
        function.params = FunctionConfigYamlUtils.BASE_YAML_UTILS.toString(scy);
        functionCache.save(function);
        if (file != null) {
            try (InputStream inputStream = new FileInputStream(file)) {
                String functionCode = function.getCode();
                functionDataService.save(inputStream, functionConfig.info.length, functionCode);
            }
        }
    }

    private void setChecksum(FunctionConfigListYaml.FunctionConfig functionConfig, String sum) {
        if (sum==null) {
            functionConfig.checksum = null;
            functionConfig.info.setSigned(false);
            return;
        }

        if (functionConfig.checksumMap != null) {
            // already defined checksum in functions.yaml
            Checksum checksum = new Checksum();
            checksum.checksums.putAll(functionConfig.checksumMap);
            functionConfig.checksum = checksum.toJson();
            boolean isSigned = false;
            for (Map.Entry<EnumsApi.Type, String> entry : functionConfig.checksumMap.entrySet()) {
                if (entry.getKey().isSign) {
                    isSigned = true;
                    break;
                }
            }
            functionConfig.info.setSigned(isSigned);
        } else {
            // set the new checksum
            functionConfig.checksum = new Checksum(EnumsApi.Type.SHA256, sum).toJson();
            functionConfig.info.setSigned(false);
        }
    }
}
