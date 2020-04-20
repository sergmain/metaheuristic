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
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
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
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
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
    private final DispatcherParamsService dispatcherParamsService;

    public SourceCodeApiData.SourceCodeValidationResult checkConsistencyOfSourceCode(SourceCodeImpl sourceCode) {
        if (sourceCode==null) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.NO_ANY_PROCESSES_ERROR, "#177.020 SourceCode is null");
        }
        if (StringUtils.isBlank(sourceCode.uid)) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_UID_EMPTY_ERROR, "#177.040 UID can't be blank");
        }
        if (StringUtils.isBlank(sourceCode.getParams())) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_PARAMS_EMPTY_ERROR, "#177.060 SourceCode is blank");
        }
        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
        SourceCodeParamsYaml.SourceCodeYaml sourceCodeYaml = sourceCodeParamsYaml.source;
        if (sourceCodeYaml.getProcesses().isEmpty()) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.NO_ANY_PROCESSES_ERROR, "#177.080 At least one process must be defined");
        }

        SourceCodeParamsYaml.Process lastProcess = null;
        List<SourceCodeParamsYaml.Process> processes = sourceCodeYaml.getProcesses();
        List<String> processCodes = new ArrayList<>();
        for (int i = 0; i < processes.size(); i++) {
            SourceCodeParamsYaml.Process process = processes.get(i);
            String code = validateProcessCode(processCodes, process);
            if (code!=null) {
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.PROCESS_CODE_NOT_UNIQUE_ERROR,
                        "#177.100 There are at least two processes with the same code '" + process.code+"'");
            }
            if (MetaUtils.isTrue(process.metas, ConstsApi.META_MH_OUTPUT_IS_DYNAMIC)) {
                if (process.function.context!= EnumsApi.FunctionExecContext.internal) {
                    return new SourceCodeApiData.SourceCodeValidationResult(
                            EnumsApi.SourceCodeValidateStatus.DYNAMIC_OUTPUT_SUPPORTED_ONLY_FOR_INTERNAL_ERROR,
                            "#177.120 Dynamic output variables are supported only for internal functions. Process: " + process.code);
                }
            }
            else {
                if (i + 1 < processes.size()) {
                    if (process.outputs.isEmpty()) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.PROCESS_PARAMS_EMPTY_ERROR,
                                "#177.140 At least one output variable must be defined in process " + process.code);
                    }
                    for (SourceCodeParamsYaml.Variable variable : process.outputs) {
                        if (S.b(variable.name)) {
                            return new SourceCodeApiData.SourceCodeValidationResult(
                                    EnumsApi.SourceCodeValidateStatus.OUTPUT_VARIABLE_NOT_DEFINED_ERROR,
                                    "#177.160 Output variable in process " + process.code + " must have a name");
                        }
                        if (variable.getSourcing() == null) {
                            return new SourceCodeApiData.SourceCodeValidationResult(
                                    EnumsApi.SourceCodeValidateStatus.SOURCING_OF_VARIABLE_NOT_DEFINED_ERROR,
                                    "#177.180 Output variable " + variable.name + " in process " + process.code + " must have a defined sourcing");
                        }
                    }
                }
            }
            if (process.code.equals(Consts.MH_FINISH_FUNCTION) && !process.function.code.equals(Consts.MH_FINISH_FUNCTION)) {
                // test that there isn't any mh.finish process which isn't actually for function mn.finish
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.WRONG_CODE_OF_PROCESS_ERROR, "#177.200 There is process with code mh.finish but function is " + process.function.code);
            }
            lastProcess = process;
            if (S.b(process.code) || !StrUtils.isCodeOk(process.code)){
                log.error("#177.210 Error while validating sourceCode {}", sourceCodeYaml);
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR, "#177.220 Code of process with illegal chars: " + process.code);
            }
            SourceCodeApiData.SourceCodeValidationResult status = checkFunctions(sourceCode, process);
            if (status.status!=OK) {
                return status;
            }
        }

        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
    }

    private @Nullable String validateProcessCode(List<String> processCodes, SourceCodeParamsYaml.Process process) {
        if (processCodes.contains(process.code)) {
            return process.code;
        }
        processCodes.add(process.code);

        if (process.subProcesses==null) {
            return null;
        }
        for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {
            String code = validateProcessCode(processCodes, subProcess);
            if (code!=null) {
                return code;
            }
        }
        return null;
    }

    public SourceCodeApiData.SourceCodeValidation validate(SourceCodeImpl sourceCode) {
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = getSourceCodesValidation(sourceCode);
        sourceCodeStateService.setValidTo(sourceCode, sourceCodeValidation.status.status == EnumsApi.SourceCodeValidateStatus.OK );
        if (sourceCode.isValid() || sourceCodeValidation.status.status==OK) {
            dispatcherParamsService.registerSourceCode(sourceCode);
            if (sourceCode.isValid() && sourceCodeValidation.status.status!=OK) {
                log.error("#177.240 Need to investigate: (sourceCode.isValid() && sourceCodeValidation.status!=OK)");
            }
            sourceCodeValidation.infoMessages = Collections.singletonList("Validation result: OK");
        }
        else {
            // we don't need to know is this sourceCode for experiment or not. Just unregister this sourceCode because it's broken
            dispatcherParamsService.unregisterExperiment(sourceCode.uid);
            final String es = "#177.260 Validation error: " + sourceCodeValidation.status.status+", sourceCode UID: " + sourceCode.uid;
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
            sourceCodeValidation.addErrorMessage("#177.280 Error while parsing yaml config, " + e.toString());
            sourceCodeValidation.status = new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.YAML_PARSING_ERROR, "#177.300 Error while parsing yaml config");
        }
        return sourceCodeValidation;
    }

    private SourceCodeApiData.SourceCodeValidationResult checkRequiredVersionOfTaskParams(int sourceCodeYamlAsStr, SourceCodeParamsYaml.Process process, SourceCodeParamsYaml.FunctionDefForSourceCode snDef) {
        if (StringUtils.isNotBlank(snDef.code)) {
            Long functionId = functionRepository.findIdByCode(snDef.code);
            if (functionId == null) {
                String es = S.f("#177.320 Function wasn't found for code: %s, process: %s", snDef.code, process.code);
                log.error(es);
                return new SourceCodeApiData.SourceCodeValidationResult(EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR, es);
            }
        }
        else {
            String es = S.f("#177.340 function wasn't found for code: %s, process: %s", snDef.code, process.code);
            log.error(es);
            return new SourceCodeApiData.SourceCodeValidationResult(EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR, es);
        }
        if (!checkRequiredVersion(sourceCodeYamlAsStr, snDef)) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.VERSION_OF_FUNCTION_IS_TOO_LOW_ERROR, "#177.3600 Version of function is too low error");
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
                            "#177.380 Unknown internal function '"+snDef.code+"'"
                    );
                }
            }
            else {
                SourceCodeApiData.SourceCodeValidationResult x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x.status != OK) {
                    log.error("#177.400 Function wasn't found for code: {}, process: {}", snDef.code, process);
                    return x;
                }
            }
        }
        if (process.preFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.preFunctions) {
                SourceCodeApiData.SourceCodeValidationResult x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x.status != OK) {
                    log.error("#177.420 Pre-function {} wasn't found", snDef.code);
                    return x;
                }
            }
        }
        if (process.postFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.postFunctions) {
                SourceCodeApiData.SourceCodeValidationResult x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x.status != OK) {
                    log.error("#177.440 Post-function {} wasn't found", snDef.code);
                    return x;
                }
            }
        }

        if (process.subProcesses!=null) {
            for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {
                SourceCodeApiData.SourceCodeValidationResult result = checkFunctions(sourceCode, subProcess);
                if (result.status != OK) {
                    return result;
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
            log.error("#175.460 Version of function {} is too low, required version: {}", snDef.code, taskParamsYamlVersion);
            return false;
        }
        return true;
    }

    private boolean isFunctionVersionOk(int requiredVersion, SourceCodeParamsYaml.FunctionDefForSourceCode snDef) {
        TaskParamsYaml.FunctionConfig sc = functionService.getFunctionConfig(snDef);
        return sc != null && (sc.skipParams || requiredVersion <= FunctionCoreUtils.getTaskParamsVersion(sc.metas));
    }


}
