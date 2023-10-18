/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsTopLevelService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionRegisterService;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.ParamsVersion;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import ai.metaheuristic.commons.yaml.versioning.YamlForVersioning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static ai.metaheuristic.api.EnumsApi.SourceCodeValidateStatus.OK;

/**
 * @author Serge
 * Date: 2/23/2020
 * Time: 9:05 PM
 */
@SuppressWarnings("unused")
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SourceCodeValidationService {

    private final FunctionService functionTopLevelService;
    private final FunctionRepository functionRepository;
    private final SourceCodeStateService sourceCodeStateService;
    private final DispatcherParamsTopLevelService dispatcherParamsTopLevelService;
    private final SourceCodeRepository sourceCodeRepository;
    private final InternalFunctionRegisterService internalFunctionRegisterService;
//
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
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_UID_EMPTY_ERROR, "178.040 UID can't be blank");
        }
        if (StringUtils.isBlank(sourceCode.getParams())) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_PARAMS_EMPTY_ERROR, "178.060 SourceCode is blank");
        }
        final Function<SourceCodeParamsYaml.Process, SourceCodeApiData.SourceCodeValidationResult> checkFunctionsFunc = (p) -> checkFunctions(sourceCode, p, checkedUids);

        SourceCodeStoredParamsYaml scspy = sourceCode.getSourceCodeStoredParamsYaml();
        SourceCodeParamsYaml sourceCodeParamsYaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
        SourceCodeApiData.SourceCodeValidationResult anyErrors = SourceCodeValidationUtils.validateSourceCodeParamsYaml(checkFunctionsFunc, sourceCodeParamsYaml);
        if (anyErrors!=null) {
            return anyErrors;
        }
        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
    }

    public SourceCodeApiData.SourceCodeValidation validate(SourceCodeImpl sourceCode) {
        TxUtils.checkTxExists();

        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = getSourceCodesValidation(sourceCode);

        sourceCodeStateService.setValidTo(sourceCode, sourceCode.companyId, sourceCodeValidation.status.status == EnumsApi.SourceCodeValidateStatus.OK );
        if (sourceCode.isValid() || sourceCodeValidation.status.status==OK) {
            dispatcherParamsTopLevelService.registerSourceCode(sourceCode);
            if (sourceCode.isValid() && sourceCodeValidation.status.status!=OK) {
                log.error("178.08 Need to investigate: (sourceCode.isValid() && sourceCodeValidation.status!=OK)");
            }
            sourceCodeValidation.infoMessages = Collections.singletonList("Validation result: OK");
        }
        else {
            dispatcherParamsTopLevelService.unregisterSourceCode(sourceCode.uid);
            final String es = "178.100 Validation status: " + sourceCodeValidation.status.status+", sourceCode UID: " + sourceCode.uid;
            log.error(es+", error: " + sourceCodeValidation.status.error);
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
            sourceCodeValidation.addErrorMessage("178.120 Error while parsing yaml config, " + e.getMessage());
            sourceCodeValidation.status = new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.YAML_PARSING_ERROR, "178.300 Error while parsing yaml config");
        }
        return sourceCodeValidation;
    }

    private SourceCodeApiData.SourceCodeValidationResult checkRequiredVersionOfTaskParams(int sourceCodeYamlAsStr, SourceCodeParamsYaml.Process process, SourceCodeParamsYaml.FunctionDefForSourceCode snDef) {
        if (S.b(snDef.code)) {
            String es = S.f("178.140 function wasn't found for code: %s, process: %s", snDef.code, process.code);
            log.error(es);
            return new SourceCodeApiData.SourceCodeValidationResult(EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR, es);
        }
        if (snDef.refType==null) {
            String es = S.f("178.160 function %s has empty refType field");
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.FUNCTION_REF_TYPE_EMPTY_ERROR, es);
        }

        Long functionId;
        if (snDef.refType== EnumsApi.FunctionRefType.type) {
            functionId = functionRepository.findIdByType(snDef.code);
        }
        else if (snDef.refType== EnumsApi.FunctionRefType.code) {
            functionId = functionRepository.findIdByCode(snDef.code);
        }
        else {
            throw new IllegalStateException("unknown refType: " + snDef.refType);
        }
        if (functionId == null) {
            String es = S.f("177.180 Function wasn't found for code: %s, refType: %s,  process: %s", snDef.code, snDef.refType, process.code);
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
                final InternalFunction internalFunction = internalFunctionRegisterService.get(snDef.code);
                if (internalFunction==null) {
                    return new SourceCodeApiData.SourceCodeValidationResult(
                            EnumsApi.SourceCodeValidateStatus.INTERNAL_FUNCTION_NOT_FOUND_ERROR,
                            "177.200 Unknown internal function '"+snDef.code+"'"
                    );
                }

                if (!internalFunction.isCachable() && process.function.context == EnumsApi.FunctionExecContext.internal &&
                    process.cache!=null && process.cache.enabled ) {
                    return new SourceCodeApiData.SourceCodeValidationResult(
                            EnumsApi.SourceCodeValidateStatus.CACHING_ISNT_SUPPORTED_FOR_INTERNAL_FUNCTION_ERROR,
                            S.f("177.220 Caching isn't supported for internal functions %s. Process: ", internalFunction.getCode(), process.code));
                }

                if (Consts.MH_EXEC_SOURCE_CODE_FUNCTION.equals(snDef.code)) {
                    String scUid = MetaUtils.getValue(process.metas, Consts.SOURCE_CODE_UID);
                    if (S.b(scUid)) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.META_NOT_FOUND_ERROR,
                                "177.240 meta '"+Consts.SOURCE_CODE_UID+"' must be defined for internal function " + Consts.MH_EXEC_SOURCE_CODE_FUNCTION
                        );
                    }
                    SourceCodeImpl sc = sourceCodeRepository.findByUid(scUid);
                    if (sc==null) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR,
                                "178.260 SourceCode wasn't found for uid '"+scUid+"'"
                        );
                    }
                    SourceCodeStoredParamsYaml scspy = sc.getSourceCodeStoredParamsYaml();
                    SourceCodeParamsYaml ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);

                    int inputNumber = ppy.source.variables==null ? 0 : ppy.source.variables.inputs.size();
                    if (process.inputs.size()!=inputNumber) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.INPUT_VARIABLES_COUNT_MISMATCH_ERROR,
                                S.f("178.280 SourceCode '%s' has different number of input variables (count: %d) from sourceCode '%s' (count: %d)",
                                        sourceCode.uid, process.inputs.size(), sc.uid, inputNumber)
                        );
                    }
                    int outputNumber = ppy.source.variables==null ? 0 : ppy.source.variables.outputs.size();
                    if (process.outputs.size()!=outputNumber) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.OUTPUT_VARIABLES_COUNT_MISMATCH_ERROR,
                                S.f("178.300 SourceCode '%s' has different number of output variables (count: %d) from sourceCode '%s' (count: %d)",
                                        sourceCode.uid, process.outputs.size(), sc.uid, outputNumber)
                        );
                    }
                }
            }
            else {
                SourceCodeApiData.SourceCodeValidationResult x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x.status != OK) {
                    log.error("178.320 Function wasn't found for code: {}, process: {}", snDef.code, process);
                    return x;
                }
            }
        }
        if (process.preFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.preFunctions) {
                SourceCodeApiData.SourceCodeValidationResult x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x.status != OK) {
                    log.error("178.340 Pre-function {} wasn't found", snDef.code);
                    return x;
                }
            }
        }
        if (process.postFunctions !=null) {
            for (SourceCodeParamsYaml.FunctionDefForSourceCode snDef : process.postFunctions) {
                SourceCodeApiData.SourceCodeValidationResult x = checkRequiredVersionOfTaskParams(v.getActualVersion(), process, snDef);
                if (x.status != OK) {
                    log.error("178.360 Post-function {} wasn't found", snDef.code);
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
        TaskParamsYaml.FunctionConfig fc = functionTopLevelService.getFunctionConfig(fnDef);
        if (fc==null) {
            String es = S.f("178.380 Function %s wasn't found",  fnDef.code);
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.FUNCTION_NOT_FOUND_ERROR, es);
        }
        if (taskParamsYamlVersion > FunctionCoreUtils.getTaskParamsVersion(fc.metas)) {
            String es = S.f("178.400 Version of function %s is too low, required version: %s", fnDef.code, taskParamsYamlVersion);
            log.error(es);
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.VERSION_OF_FUNCTION_IS_TOO_LOW_ERROR, es);
        }
        return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
    }


}
