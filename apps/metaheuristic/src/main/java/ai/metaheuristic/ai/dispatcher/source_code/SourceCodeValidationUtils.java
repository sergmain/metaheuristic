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
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.SourceCodeValidateStatus.OK;

/**
 * @author Sergio Lissner
 * Date: 5/14/2023
 * Time: 9:16 PM
 */
@Slf4j
public class SourceCodeValidationUtils {

    public static final Function<SourceCodeParamsYaml.Process, SourceCodeApiData.SourceCodeValidationResult> NULL_CHECK_FUNC =
            (p)-> new SourceCodeApiData.SourceCodeValidationResult(OK, null);


    @Nullable
    public static SourceCodeApiData.SourceCodeValidationResult validateSourceCodeParamsYaml(
            Function<SourceCodeParamsYaml.Process, SourceCodeApiData.SourceCodeValidationResult> checkFunctionsFunc,
            SourceCodeParamsYaml sourceCodeParamsYaml) {
        SourceCodeParamsYaml.SourceCode sourceCode = sourceCodeParamsYaml.source;

        if (sourceCode.getProcesses().isEmpty()) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus.NO_ANY_PROCESSES_ERROR, "#177.080 At least one process must be defined");
        }
        SourceCodeApiData.SourceCodeValidationResult result = checkStrictNaming(sourceCode);
        if (result.status!=OK) {
            return result;
        }
        result = checkVariableNaming(sourceCode);
        if (result.status!=OK) {
            return result;
        }

        List<SourceCodeParamsYaml.Process> processes = sourceCode.getProcesses();
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
                    boolean doesVariableHaveSource = doesVariableHaveSource(sourceCode, variable.name);
                    if (!doesVariableHaveSource) {
                        return new SourceCodeApiData.SourceCodeValidationResult(
                                EnumsApi.SourceCodeValidateStatus.SOURCE_OF_VARIABLE_NOT_FOUND_ERROR,
                                S.f("177.200 Input variable %s in process %s isn't found in any source of Variables - Output, ExecContext Inputs, Globals Variables",  variable.name, process.code));
                    }

                }
            }
            if (process.code.equals(Consts.MH_FINISH_FUNCTION) && !process.function.code.equals(Consts.MH_FINISH_FUNCTION)) {
                // test that there isn't any mh.finish process which isn't actually for function mn.finish
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.WRONG_CODE_OF_PROCESS_ERROR, "#177.200 There is process with code mh.finish but function is " + process.function.code);
            }
            SourceCodeApiData.SourceCodeValidationResult status = checkFunctionsFunc.apply(process);
            if (status.status != OK) {
                return status;
            }
        }

        if (sourceCodeParamsYaml.source.variables!=null) {
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
        }
        return null;
    }

    private static SourceCodeApiData.SourceCodeValidationResult checkVariableNaming(SourceCodeParamsYaml.SourceCode sourceCodeYaml) {
        if (!Boolean.TRUE.equals(sourceCodeYaml.strictNaming)) {
            return ConstsApi.SOURCE_CODE_VALIDATION_RESULT_OK;
        }
        if (sourceCodeYaml.variables!=null) {
            List<String> badNames = sourceCodeYaml.variables.inputs.stream().map(o -> o.name).filter(o -> !StrUtils.isVarNameOk(o)).collect(Collectors.toList());
            if (!badNames.isEmpty()) {
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
                        S.f("#177.230 SourceCode-level input variables have wrong names: %s", String.join(",", badNames)));
            }
            badNames = sourceCodeYaml.variables.outputs.stream().map(o -> o.name).filter(o -> !StrUtils.isVarNameOk(o)).collect(Collectors.toList());
            if (!badNames.isEmpty()) {
                return new SourceCodeApiData.SourceCodeValidationResult(
                        EnumsApi.SourceCodeValidateStatus.WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
                        S.f("#177.231 SourceCode-level output variables have wrong names: %s", String.join(",", badNames)));
            }
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
                    S.f("#177.232 SourceCode-level input variables have wrong names: %s", String.join(", ", badNames)));
        }
        badNames = process.outputs.stream().map(o->o.name).filter(o->!StrUtils.isVarNameOk(o)).collect(Collectors.toList());
        if (!badNames.isEmpty()) {
            return new SourceCodeApiData.SourceCodeValidationResult(
                    EnumsApi.SourceCodeValidateStatus. WRONG_FORMAT_OF_INLINE_VARIABLE_ERROR,
                    S.f("#177.233 SourceCode-level output variables have wrong names: %s", String.join(", ", badNames)));
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

    public static boolean doesVariableHaveSource(SourceCodeParamsYaml.SourceCode sourceCode, String name) {
        if (sourceCode.variables!=null) {
            if (sourceCode.variables.globals!=null && sourceCode.variables.globals.contains(name)) {
                return true;
            }
            if (sourceCode.variables.inputs.stream().anyMatch(v->v.name.equals(name))) {
                return true;
            }
        }
        for (SourceCodeParamsYaml.Process process : sourceCode.getProcesses()) {
            if (findOutputVariable(process, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean findOutputVariable(SourceCodeParamsYaml.Process process, String name) {
        for (SourceCodeParamsYaml.Variable output : process.outputs) {
            if (output.name.equals(name)) {
                return true;
            }
        }
        if (process.subProcesses==null) {
            return false;
        }
        for (SourceCodeParamsYaml.Process subProcess : process.subProcesses.processes) {
            boolean b = findOutputVariable(subProcess, name);
            if (b) {
                return true;
            }
        }
        return false;
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

    private static SourceCodeApiData.SourceCodeValidationResult checkStrictNaming(SourceCodeParamsYaml.SourceCode sourceCodeYaml) {
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
}
