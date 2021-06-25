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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.function.FunctionTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionRegisterService;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
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

import java.util.*;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.SourceCodeValidateStatus.OK;

/**
 * @author Serge
 * Date: 2/23/2020
 * Time: 9:05 PM
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class SourceCodeValidationService {

    private final FunctionTopLevelService functionTopLevelService;
    private final FunctionRepository functionRepository;
    private final SourceCodeStateService sourceCodeStateService;
    private final DispatcherParamsService dispatcherParamsService;
    private final SourceCodeRepository sourceCodeRepository;

    public SourceCodeApiData.SourceCodeValidationResult checkConsistencyOfSourceCode(SourceCodeImpl sourceCode) {
        List<String> checkedUids = new ArrayList<>();
        return checkConsistencyOfSourceCodeInternal(sourceCode, checkedUids);
    }

    private SourceCodeApiData.SourceCodeValidationResult checkConsistencyOfSourceCodeInternal(SourceCodeImpl sourceCode, List<String> checkedUids) {
        if (checkedUids.contains(sourceCode.uid)) {
            return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
        }
        checkedUids.add(sourceCode.uid);
        try {
            SourceCodeApiData.SourceCodeValidationResult sourceCodeValidationResult = getSourceCodeValidationResult(sourceCode, checkedUids);
            return sourceCodeValidationResult;
        }
        finally {
            checkedUids.remove(sourceCode.uid);
        }
    }

    private SourceCodeApiData.SourceCodeValidationResult getSourceCodeValidationResult(SourceCodeImpl sourceCode, List<String> checkedUids) {
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
        SourceCodeApiData.SourceCodeValidationResult result = checkStrictNaming(sourceCodeYaml);
        if (result.status!=OK) {
            return result;
        }
        result = checkVariableNaming(sourceCodeYaml);
        if (result.status!=OK) {
            return result;
        }

        List<SourceCodeParamsYaml.Process> processes = sourceCodeYaml.getProcesses();
        List<String> processCodes = new ArrayList<>();
        for (int i = 0; i < processes.size(); i++) {
            SourceCodeParamsYaml.Process process = processes.get(i);
            if (S.b(process.code)) {
                final String msg = "#177.090 Code of process is blank";
                log.error(msg);
                return new SourceCodeApiData.SourceCodeValidationResult(EnumsApi.SourceCodeValidateStatus.PROCESS_CODE_NOT_FOUND_ERROR, msg);
            }
            String code = findDoublesOfProcessCode(processCodes, process);
            if (code != null) {
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.PROCESS_CODE_NOT_UNIQUE_ERROR,
                        "#177.100 There are at least two processes with the same code '" + code + "'");
            }
            code = validateProcessCode(process);
            if (code != null) {
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR,
                        "#177.105 The code of process contains not allowed chars: '" + process.code + "'");
            }
            code = validateSubProcessLogic(process);
            if (code != null) {
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.SUB_PROCESS_LOGIC_NOT_DEFINED,
                        "#177.107 The process '" + code + "' has sub processes but logic isn't defined");
            }
            if (process.function.context == EnumsApi.FunctionExecContext.internal && process.cache != null && process.cache.enabled) {
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.CACHING_ISNT_SUPPORTED_FOR_INTERNAL_FUNCTION_ERROR,
                        "#177.110 Caching isn't supported for internal functions. Process: " + process.code);
            }

            if (MetaUtils.isTrue(process.metas, ConstsApi.META_MH_OUTPUT_IS_DYNAMIC)) {
                if (process.function.context != EnumsApi.FunctionExecContext.internal) {
                    return new SourceCodeApiData.SourceCodeValidationResult(
                            EnumsApi.SourceCodeValidateStatus.DYNAMIC_OUTPUT_SUPPORTED_ONLY_FOR_INTERNAL_ERROR,
                            "#177.120 Dynamic output variables are supported only for internal functions. Process: " + process.code);
                }
            } else {
                boolean finish = process.function.code.equals(Consts.MH_FINISH_FUNCTION);
                if (!finish) {
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
                        EnumsApi.SourceCodeValidateStatus status = SourceCodeUtils.isVariableNameOk(variable.name);
                        if (status != OK) {
                            return new SourceCodeApiData.SourceCodeValidationResult(
                                    EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_VARIABLE_NAME_ERROR,
                                    "#177.183 Output variable in process " + process.code + " has a wrong chars in name");
                        }
                    }
                    for (SourceCodeParamsYaml.Variable variable : process.inputs) {
                        if (S.b(variable.name)) {
                            return new SourceCodeApiData.SourceCodeValidationResult(
                                    EnumsApi.SourceCodeValidateStatus.INPUT_VARIABLE_NOT_DEFINED_ERROR,
                                    "#177.185 Output variable in process " + process.code + " must have a name");
                        }
                        EnumsApi.SourceCodeValidateStatus status = SourceCodeUtils.isVariableNameOk(variable.name);
                        if (status != OK) {
                            return new SourceCodeApiData.SourceCodeValidationResult(
                                    EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_VARIABLE_NAME_ERROR,
                                    "#177.187 Input variable in process " + process.code + " has a wrong chars in name");
                        }
                    }
                }
            }
            if (process.code.equals(Consts.MH_FINISH_FUNCTION) && !process.function.code.equals(Consts.MH_FINISH_FUNCTION)) {
                // test that there isn't any mh.finish process which isn't actually for function mn.finish
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.WRONG_CODE_OF_PROCESS_ERROR, "#177.200 There is process with code mh.finish but function is " + process.function.code);
            }
            SourceCodeApiData.SourceCodeValidationResult status = checkFunctions(sourceCode, process, checkedUids);
            if (status.status != OK) {
                return status;
            }
        }

        for (Map.Entry e : sourceCodeParamsYaml.source.variables.inline.entrySet()) {
            if (!(e.getKey() instanceof String)) {
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
                        "#177.223 Inline variable at group level must be type of String, actual: " + e.getKey().getClass() + ", value: " + e.getKey());
            }

            Object o = e.getValue();
            if (!(o instanceof Map)) {
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
                        "#177.225 Inline variables group must be type of Map, actual: " + o.getClass());

            }

            Map<Object, Object> m = ((Map) o);
            for (Map.Entry entry : m.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    return new SourceCodeApiData.SourceCodeValidationResult(
                            EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
                            "#177.227 key in Inline variable must be type of String, actual: " + e.getKey().getClass() + ", value: " + entry.getKey());
                }

                Object obj = entry.getValue();
                if (!(obj instanceof String)) {
                    return new SourceCodeApiData.SourceCodeValidationResult(
                            EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
                            S.f("#177.229 Value of Inline variables with key '%s' must be type of String, actual: %s", entry.getKey(), o.getClass()));
                }
            }


        }
        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
    }

    private SourceCodeApiData.SourceCodeValidationResult checkVariableNaming(SourceCodeParamsYaml.SourceCodeYaml sourceCodeYaml) {
        if (!Boolean.TRUE.equals(sourceCodeYaml.strictNaming)) {
            return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
        }
        List<String> badNames = sourceCodeYaml.variables.inputs.stream().map(o->o.name).filter(o->!StrUtils.isVarNameOk(o)).collect(Collectors.toList());
        if (!badNames.isEmpty()) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
                    S.f("#177.230 SourceCode-level input variables have wrong names: %s", String.join(",", badNames)));
        }
        badNames = sourceCodeYaml.variables.outputs.stream().map(o->o.name).filter(o->!StrUtils.isVarNameOk(o)).collect(Collectors.toList());
        if (!badNames.isEmpty()) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
                    S.f("#177.231 SourceCode-level output variables have wrong names: %s", String.join(",", badNames)));
        }
        for (SourceCodeParamsYaml.Process p : sourceCodeYaml.processes) {
            SourceCodeApiData.SourceCodeValidationResult r = checkFunctionVariableNames(p);
            if (r.status!=OK) {
                return r;
            }
            if (p.subProcesses!=null) {
                for (SourceCodeParamsYaml.Process subProcess : p.subProcesses.processes) {
                    SourceCodeApiData.SourceCodeValidationResult r1 = checkFunctionVariableNames(subProcess);
                    if (r1.status!=OK) {
                        return r1;
                    }
                }
            }
        }
        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
    }

    private static SourceCodeApiData.SourceCodeValidationResult checkFunctionVariableNames(SourceCodeParamsYaml.Process process) {
        List<String> badNames = process.inputs.stream().map(o->o.name).filter(o->!StrUtils.isVarNameOk(o)).collect(Collectors.toList());
        if (!badNames.isEmpty()) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
                    S.f("#177.232 SourceCode-level input variables have wrong names: %s", String.join(",", badNames)));
        }
        badNames = process.outputs.stream().map(o->o.name).filter(o->!StrUtils.isVarNameOk(o)).collect(Collectors.toList());
        if (!badNames.isEmpty()) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
                    S.f("#177.233 SourceCode-level output variables have wrong names: %s", String.join(",", badNames)));
        }
        if (process.subProcesses!=null) {
            for (SourceCodeParamsYaml.Process p : process.subProcesses.processes) {
                SourceCodeApiData.SourceCodeValidationResult r1 = checkFunctionVariableNames(p);
                if (r1.status != OK) {
                    return r1;
                }
            }
        }
        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
    }

    @Nullable
    private static String findDoublesOfProcessCode(List<String> processCodes, SourceCodeParamsYaml.Process process) {
        if (processCodes.contains(process.code)) {
            return process.code;
        }
        processCodes.add(process.code);

        if (process.subProcesses==null) {
            return null;
        }
        for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {
            String code = findDoublesOfProcessCode(processCodes, subProcess);
            if (code!=null) {
                return code;
            }
        }
        return null;
    }

    @Nullable
    private static String validateProcessCode(SourceCodeParamsYaml.Process process) {
        if (!StrUtils.isCodeOk(process.code)) {
            return process.code;
        }
        if (process.subProcesses==null) {
            return null;
        }
        for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {
            String code = validateProcessCode(subProcess);
            if (code!=null) {
                return code;
            }
        }
        return null;
    }

    private static SourceCodeApiData.SourceCodeValidationResult checkStrictNaming(SourceCodeParamsYaml.SourceCodeYaml sourceCodeYaml) {
        Set<String> codes = new HashSet<>();
        for (SourceCodeParamsYaml.Process process : sourceCodeYaml.processes) {
            codes.add(process.function.code);
            collectFunctionCodes(process, codes);
        }
        if (codes.contains(Consts.MH_EVALUATION_FUNCTION) && !Boolean.TRUE.equals(sourceCodeYaml.strictNaming)) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.STRICT_NAMING_REQUIRED_ERROR,
                    "#177.235 strictNaming must be true if the function 'mh.evaluation' is being used");
        }
        for (SourceCodeParamsYaml.Process process : sourceCodeYaml.processes) {
            if (checkForCondition(process) && !Boolean.TRUE.equals(sourceCodeYaml.strictNaming)) {
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.STRICT_NAMING_REQUIRED_ERROR,
                        "#177.236 strictNaming must be true if the condition of process is being used");
            }
        }
        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
    }

    private static boolean checkForCondition(SourceCodeParamsYaml.Process process) {
        if (!S.b(process.condition)) {
            return true;
        }
        if (process.subProcesses!=null) {
            for (SourceCodeParamsYaml.Process p : process.subProcesses.processes) {
                if (checkForCondition(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void collectFunctionCodes(SourceCodeParamsYaml.Process process, Set<String> codes) {
        codes.add(process.function.code);

        if (process.subProcesses==null) {
            return;
        }
        if (!process.subProcesses.processes.isEmpty() && process.subProcesses.logic==null) {
            return;
        }
        for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {
            collectFunctionCodes(subProcess, codes);
        }
    }

    @Nullable
    private static String validateSubProcessLogic(SourceCodeParamsYaml.Process process) {
        if (process.subProcesses==null) {
            return null;
        }
        if (!process.subProcesses.processes.isEmpty() && process.subProcesses.logic==null) {
            return process.code;
        }
        for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {
            String code = validateSubProcessLogic(subProcess);
            if (code!=null) {
                return code;
            }
        }
        return null;
    }

    public SourceCodeApiData.SourceCodeValidation validate(SourceCodeImpl sourceCode) {
        TxUtils.checkTxExists();

        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = getSourceCodesValidation(sourceCode);

        sourceCodeStateService.setValidTo(sourceCode, sourceCode.companyId, sourceCodeValidation.status.status == EnumsApi.SourceCodeValidateStatus.OK );
        if (sourceCode.isValid() || sourceCodeValidation.status.status==OK) {
            dispatcherParamsService.registerSourceCode(sourceCode);
            if (sourceCode.isValid() && sourceCodeValidation.status.status!=OK) {
                log.error("#177.240 Need to investigate: (sourceCode.isValid() && sourceCodeValidation.status!=OK)");
            }
            sourceCodeValidation.infoMessages = Collections.singletonList("Validation result: OK");
        }
        else {
            dispatcherParamsService.unregisterSourceCode(sourceCode.uid);
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
        return checkRequiredVersion(sourceCodeYamlAsStr, snDef);
    }

    private SourceCodeApiData.SourceCodeValidationResult checkFunctions(SourceCodeImpl sourceCode, SourceCodeParamsYaml.Process process, List<String> checkedUids) {
        ParamsVersion v = YamlForVersioning.getParamsVersion(sourceCode.getParams());

        if (process.function !=null) {
            SourceCodeParamsYaml.FunctionDefForSourceCode snDef = process.function;
            if (snDef.context== EnumsApi.FunctionExecContext.internal) {
                if (!InternalFunctionRegisterService.isRegistered(snDef.code)) {
                    return new SourceCodeApiData.SourceCodeValidationResult(
                            EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR,
                            "#177.380 Unknown internal function '"+snDef.code+"'"
                    );
                }
                if (Consts.MH_EXEC_SOURCE_CODE_FUNCTION.equals(snDef.code)) {
                    String scUid = MetaUtils.getValue(process.metas, Consts.SOURCE_CODE_UID);
                    if (S.b(scUid)) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.META_NOT_FOUND_ERROR,
                                "#177.383 meta '"+Consts.SOURCE_CODE_UID+"' must be defined for internal function " + Consts.MH_EXEC_SOURCE_CODE_FUNCTION
                        );
                    }
                    SourceCodeImpl sc = sourceCodeRepository.findByUid(scUid);
                    if (sc==null) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR,
                                "#177.383 SourceCode wasn't found for uid '"+scUid+"'"
                        );
                    }
                    SourceCodeStoredParamsYaml scspy = sc.getSourceCodeStoredParamsYaml();
                    SourceCodeParamsYaml ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);

                    if (process.inputs.size()!=ppy.source.variables.inputs.size()) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.INPUT_VARIABLES_COUNT_MISMATCH_ERROR,
                                S.f("#177.386 SourceCode '%s' has different number of input variables (count: %d) from sourceCode '%s' (count: %d)",
                                        sourceCode.uid, process.inputs.size(), sc.uid, ppy.source.variables.inputs.size())
                        );
                    }
                    if (process.outputs.size()!=ppy.source.variables.outputs.size()) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.OUTPUT_VARIABLES_COUNT_MISMATCH_ERROR,
                                S.f("#177.388 SourceCode '%s' has different number of output variables (count: %d) from sourceCode '%s' (count: %d)",
                                        sourceCode.uid, process.outputs.size(), sc.uid, ppy.source.variables.outputs.size())
                        );
                    }
                    SourceCodeApiData.SourceCodeValidationResult result = checkConsistencyOfSourceCodeInternal(sc, checkedUids);
                    if (result.status!=OK) {
                        return result;
                    }
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
                SourceCodeApiData.SourceCodeValidationResult result = checkFunctions(sourceCode, subProcess, checkedUids);
                if (result.status != OK) {
                    return result;
                }
            }
        }

        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
    }

    private SourceCodeApiData.SourceCodeValidationResult checkRequiredVersion(int sourceCodeParamsVersion, SourceCodeParamsYaml.FunctionDefForSourceCode fnDef) {
        int taskParamsYamlVersion = SourceCodeParamsYamlUtils.getRequiredVersionOfTaskParamsYaml(sourceCodeParamsVersion);
        TaskParamsYaml.FunctionConfig sc = functionTopLevelService.getFunctionConfig(fnDef);
        if (sc==null) {
            String es = S.f("#175.440 Function %s wasn't found",  fnDef.code);
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR, es);
        }
        if (!sc.skipParams && taskParamsYamlVersion > FunctionCoreUtils.getTaskParamsVersion(sc.metas)) {
            String es = S.f("#175.460 Version of function %s is too low, required version: %s", fnDef.code, taskParamsYamlVersion);
            log.error(es);
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.VERSION_OF_FUNCTION_IS_TOO_LOW_ERROR, es);
        }
        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
    }


}
