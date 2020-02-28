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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.YamlVersion;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Collections;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.SourceCodeValidateStatus.OK;

/**
 * @author Serge
 * Date: 2/23/2020
 * Time: 9:05 PM
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class SourceCodeValidationService {

    private final FunctionService functionService;
    private final FunctionRepository functionRepository;
    private final SourceCodeStateService sourceCodeStateService;

    public EnumsApi.SourceCodeValidateStatus checkConsistencyOfSourceCode(SourceCodeImpl sourceCode) {
        if (sourceCode==null) {
            return EnumsApi.SourceCodeValidateStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(sourceCode.uid)) {
            return EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_UID_EMPTY_ERROR;
        }
        if (StringUtils.isBlank(sourceCode.getParams())) {
            return EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_PARAMS_EMPTY_ERROR;
        }
        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
        SourceCodeParamsYaml.SourceCodeYaml sourceCodeYaml = sourceCodeParamsYaml.source;
        if (sourceCodeYaml.getProcesses().isEmpty()) {
            return EnumsApi.SourceCodeValidateStatus.NO_ANY_PROCESSES_ERROR;
        }

        SourceCodeParamsYaml.Process lastProcess = null;
        List<SourceCodeParamsYaml.Process> processes = sourceCodeYaml.getProcesses();
        for (int i = 0; i < processes.size(); i++) {
            SourceCodeParamsYaml.Process process = processes.get(i);
            if (i + 1 < processes.size()) {
                if (process.outputs.isEmpty()) {
                    return EnumsApi.SourceCodeValidateStatus.PROCESS_PARAMS_EMPTY_ERROR;
                }
                for (SourceCodeParamsYaml.Variable params : process.outputs) {
                    if (S.b(params.name)) {
                        return EnumsApi.SourceCodeValidateStatus.OUTPUT_VARIABLE_NOT_DEFINED_ERROR;
                    }
                    if (params.sourcing==null) {
                        return EnumsApi.SourceCodeValidateStatus.SOURCING_OF_VARIABLE_NOT_DEFINED_ERROR;
                    }
                }
            }
            lastProcess = process;
            if (S.b(process.code) || !StrUtils.isCodeOk(process.code)){
                log.error("Error while validating sourceCode {}", sourceCodeYaml);
                return EnumsApi.SourceCodeValidateStatus.PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            EnumsApi.SourceCodeValidateStatus status;
            status = checkFunctions(sourceCode, process);
            if (status!=OK) {
                return status;
            }
        }
        return EnumsApi.SourceCodeValidateStatus.OK;
    }

    public SourceCodeApiData.SourceCodeValidation validate(SourceCodeImpl sourceCode) {
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = getSourceCodesValidation(sourceCode);
        sourceCodeStateService.setValidTo(sourceCode, sourceCodeValidation.status == EnumsApi.SourceCodeValidateStatus.OK );
        if (sourceCode.isValid() || sourceCodeValidation.status==OK) {
            if (sourceCode.isValid() && sourceCodeValidation.status!=OK) {
                log.error("#701.097 Need to investigate: (sourceCode.isValid() && sourceCodeValidation.status!=OK)");
            }
            sourceCodeValidation.infoMessages = Collections.singletonList("Validation result: OK");
        }
        else {
            final String es = "#701.100 Validation error: " + sourceCodeValidation.status;
            log.error(es);
            sourceCodeValidation.addErrorMessage(es);
        }
        return sourceCodeValidation;
    }

    private SourceCodeApiData.SourceCodeValidation getSourceCodesValidation(SourceCodeImpl sourceCode) {
        final SourceCodeApiData.SourceCodeValidation sourceCodeValidation = new SourceCodeApiData.SourceCodeValidation();
        try {
            sourceCodeValidation.status = checkConsistencyOfSourceCode(sourceCode);
        } catch (YAMLException e) {
            sourceCodeValidation.addErrorMessage("#701.090 Error while parsing yaml config, " + e.toString());
            sourceCodeValidation.status = EnumsApi.SourceCodeValidateStatus.YAML_PARSING_ERROR;
        }
        return sourceCodeValidation;
    }

    private EnumsApi.SourceCodeValidateStatus checkRequiredVersionOfTaskParams(int sourceCodeYamlAsStr, SourceCodeParamsYaml.Process process, SourceCodeParamsYaml.FunctionDefForSourceCode snDef) {
        if (StringUtils.isNotBlank(snDef.code)) {
            Long functionId = functionRepository.findIdByCode(snDef.code);
            if (functionId == null) {
                log.error("#177.030 function wasn't found for code: {}, process: {}", snDef.code, process);
                return EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR;
            }
        }
        else {
            log.error("#177.060 function wasn't found for code: {}, process: {}", snDef.code, process);
            return EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR;
        }
        if (!checkRequiredVersion(sourceCodeYamlAsStr, snDef)) {
            return EnumsApi.SourceCodeValidateStatus.VERSION_OF_FUNCTION_IS_TOO_LOW_ERROR;
        }
        return OK;
    }

    private EnumsApi.SourceCodeValidateStatus checkFunctions(SourceCode sourceCode, SourceCodeParamsYaml.Process process) {
        YamlVersion v = YamlForVersioning.getYamlForVersion().load(sourceCode.getParams());

        if (process.function !=null) {
            SourceCodeParamsYaml.FunctionDefForSourceCode snDef = process.function;
            if (snDef.context== EnumsApi.FunctionExecContext.internal) {
                if (!Consts.MH_INTERNAL_FUNCTIONS.contains(snDef.code)) {
                    return EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR;
                }
            }
            else {
                EnumsApi.SourceCodeValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Function wasn't found for code: {}, process: {}", snDef.code, process);
                    return x;
                }
            }
        }
        if (process.preFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.preFunctions) {
                EnumsApi.SourceCodeValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Pre-function {} wasn't found", snDef.code);
                    return x;
                }
            }
        }
        if (process.postFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.postFunctions) {
                EnumsApi.SourceCodeValidateStatus x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x != OK) {
                    log.error("#177.030 Post-function {} wasn't found", snDef.code);
                    return x;
                }
            }
        }

        return OK;
    }

    // todo 2020-02-27 current version isn't good
    //  because there is duplication of code with ai.metaheuristic.ai.dispatcher.function.FunctionService.isFunctionVersionOk
    private boolean checkRequiredVersion(int sourceCodeParamsVersion, SourceCodeParamsYaml.FunctionDefForSourceCode snDef) {
        int taskParamsYamlVersion = SourceCodeParamsYamlUtils.getRequiredVertionOfTaskParamsYaml(sourceCodeParamsVersion);
        boolean ok = isFunctionVersionOk(taskParamsYamlVersion, snDef);
        if (!ok) {
            log.error("#175.030 Version of function {} is too low, required version: {}", snDef.code, taskParamsYamlVersion);
            return false;
        }
        return true;
    }

    private boolean isFunctionVersionOk(int requiredVersion, SourceCodeParamsYaml.FunctionDefForSourceCode snDef) {
        TaskParamsYaml.FunctionConfig sc = getFunctionConfig(snDef);
        return sc != null && (sc.skipParams || requiredVersion <= FunctionCoreUtils.getTaskParamsVersion(sc.metas));
    }

    private TaskParamsYaml.FunctionConfig getFunctionConfig(SourceCodeParamsYaml.FunctionDefForSourceCode functionDef) {
        TaskParamsYaml.FunctionConfig functionConfig = null;
        if(StringUtils.isNotBlank(functionDef.code)) {
            Function function = functionService.findByCode(functionDef.code);
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

}
