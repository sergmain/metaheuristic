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
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
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
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final InternalFunctionProcessor internalFunctionProcessor;

    public SourceCodeApiData.SourceCodeValidationResult checkConsistencyOfSourceCode(SourceCodeImpl sourceCode) {
        if (sourceCode==null) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.NO_ANY_PROCESSES_ERROR, "SourceCode is null");
        }
        if (StringUtils.isBlank(sourceCode.uid)) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_UID_EMPTY_ERROR, "UID can't be blank");
        }
        if (StringUtils.isBlank(sourceCode.getParams())) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_PARAMS_EMPTY_ERROR, "SourceCode is blank");
        }
        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
        SourceCodeParamsYaml.SourceCodeYaml sourceCodeYaml = sourceCodeParamsYaml.source;
        if (sourceCodeYaml.getProcesses().isEmpty()) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.NO_ANY_PROCESSES_ERROR, "At least one process must be defined");
        }

        SourceCodeParamsYaml.Process lastProcess = null;
        List<SourceCodeParamsYaml.Process> processes = sourceCodeYaml.getProcesses();
        for (int i = 0; i < processes.size(); i++) {
            SourceCodeParamsYaml.Process process = processes.get(i);
            if (i + 1 < processes.size()) {
                if (process.outputs.isEmpty()) {
                    return new SourceCodeApiData.SourceCodeValidationResult(
                            EnumsApi.SourceCodeValidateStatus.PROCESS_PARAMS_EMPTY_ERROR,
                            "At least one output variable must be defined in process " + process.code);
                }
                for (SourceCodeParamsYaml.Variable variable : process.outputs) {
                    if (S.b(variable.name)) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.OUTPUT_VARIABLE_NOT_DEFINED_ERROR,
                                "Output variable in process "+ process.code+" must have a name");
                    }
                    if (variable.getSourcing()==null) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.SOURCING_OF_VARIABLE_NOT_DEFINED_ERROR,
                                "Output variable "+variable.name+" in process "+ process.code+" must have a defined sourcing");
                    }
                }
            }
            if (process.code.equals(Consts.MH_FINISH_FUNCTION) && !process.function.code.equals(Consts.MH_FINISH_FUNCTION)) {
                // test that there isn't any mh.finish process which isn't actually for function mn.finish
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.WRONG_CODE_OF_PROCESS_ERROR, "There is process with code mh.finish but function is " + process.function.code);
            }
            lastProcess = process;
            if (S.b(process.code) || !StrUtils.isCodeOk(process.code)){
                log.error("Error while validating sourceCode {}", sourceCodeYaml);
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR, "Code of process with illegal chars: " + process.code);
            }
            SourceCodeApiData.SourceCodeValidationResult status = checkFunctions(sourceCode, process);
            if (status.status!=OK) {
                return status;
            }
        }

        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
    }

    public SourceCodeApiData.SourceCodeValidation validate(SourceCodeImpl sourceCode) {
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = getSourceCodesValidation(sourceCode);
        sourceCodeStateService.setValidTo(sourceCode, sourceCodeValidation.status.status == EnumsApi.SourceCodeValidateStatus.OK );
        if (sourceCode.isValid() || sourceCodeValidation.status.status==OK) {
            if (sourceCode.isValid() && sourceCodeValidation.status.status!=OK) {
                log.error("#701.097 Need to investigate: (sourceCode.isValid() && sourceCodeValidation.status!=OK)");
            }
            sourceCodeValidation.infoMessages = Collections.singletonList("Validation result: OK");
        }
        else {
            final String es = "#701.100 Validation error: " + sourceCodeValidation.status.status;
            log.error(es);
            sourceCodeValidation.addErrorMessage(es);
            sourceCodeValidation.addErrorMessage(sourceCodeValidation.status.error);
        }
        return sourceCodeValidation;
    }

    private SourceCodeApiData.SourceCodeValidation getSourceCodesValidation(SourceCodeImpl sourceCode) {
        final SourceCodeApiData.SourceCodeValidation sourceCodeValidation = new SourceCodeApiData.SourceCodeValidation();
        try {
            sourceCodeValidation.status = checkConsistencyOfSourceCode(sourceCode);
        } catch (YAMLException e) {
            sourceCodeValidation.addErrorMessage("#701.090 Error while parsing yaml config, " + e.toString());
            sourceCodeValidation.status = new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.YAML_PARSING_ERROR, "#701.090 Error while parsing yaml config");
        }
        return sourceCodeValidation;
    }

    private SourceCodeApiData.SourceCodeValidationResult checkRequiredVersionOfTaskParams(int sourceCodeYamlAsStr, SourceCodeParamsYaml.Process process, SourceCodeParamsYaml.FunctionDefForSourceCode snDef) {
        if (StringUtils.isNotBlank(snDef.code)) {
            Long functionId = functionRepository.findIdByCode(snDef.code);
            if (functionId == null) {
                String es = S.f("#177.030 Function wasn't found for code: %s, process: %s", snDef.code, process.code);
                log.error(es);
                return new SourceCodeApiData.SourceCodeValidationResult(EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR, es);
            }
        }
        else {
            String es = S.f("#177.060 function wasn't found for code: %s, process: %s", snDef.code, process.code);
            log.error(es);
            return new SourceCodeApiData.SourceCodeValidationResult(EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR, es);
        }
        if (!checkRequiredVersion(sourceCodeYamlAsStr, snDef)) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.VERSION_OF_FUNCTION_IS_TOO_LOW_ERROR, "Version of function is too low error");
        }
        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
    }

    private SourceCodeApiData.SourceCodeValidationResult checkFunctions(SourceCode sourceCode, SourceCodeParamsYaml.Process process) {
        YamlVersion v = YamlForVersioning.getYamlVersion(sourceCode.getParams());

        if (process.function !=null) {
            SourceCodeParamsYaml.FunctionDefForSourceCode snDef = process.function;
            if (snDef.context== EnumsApi.FunctionExecContext.internal) {
                if (!internalFunctionProcessor.isRegistered(snDef.code)) {
                    return new SourceCodeApiData.SourceCodeValidationResult(
                            EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR,
                            "Unknown internal function '"+snDef.code+"'"
                    );
                }
            }
            else {
                SourceCodeApiData.SourceCodeValidationResult x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x.status != OK) {
                    log.error("#177.030 Function wasn't found for code: {}, process: {}", snDef.code, process);
                    return x;
                }
            }
        }
        if (process.preFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.preFunctions) {
                SourceCodeApiData.SourceCodeValidationResult x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x.status != OK) {
                    log.error("#177.030 Pre-function {} wasn't found", snDef.code);
                    return x;
                }
            }
        }
        if (process.postFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.postFunctions) {
                SourceCodeApiData.SourceCodeValidationResult x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x.status != OK) {
                    log.error("#177.030 Post-function {} wasn't found", snDef.code);
                    return x;
                }
            }
        }

        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
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
        TaskParamsYaml.FunctionConfig sc = functionService.getFunctionConfig(snDef);
        return sc != null && (sc.skipParams || requiredVersion <= FunctionCoreUtils.getTaskParamsVersion(sc.metas));
    }


}
