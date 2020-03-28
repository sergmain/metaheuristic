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

package ai.metaheuristic.ai.dispatcher.internal_functions.permute_variables_and_hyper_params;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 2/1/2020
 * Time: 9:17 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class PermuteVariablesAndHyperParamsFunction implements InternalFunction {

    private final VariableRepository variableRepository;
    private final GlobalVariableRepository globalVariableRepository;

    @Override
    public String getCode() {
        return Consts.MH_PERMUTE_VARIABLES_AND_HYPER_PARAMS_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_PERMUTE_VARIABLES_AND_HYPER_PARAMS_FUNCTION;
    }

    public InternalFunctionProcessingResult process(
            Long sourceCodeId, Long execContextId, String internalContextId, SourceCodeParamsYaml.VariableDefinition variableDefinition,
            List<TaskParamsYaml.InputVariable> inputs) {

        if (inputs.size()>1) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.number_of_inputs_is_incorrect, "Too many input variables");
        }
        if (inputs.isEmpty()) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.number_of_inputs_is_incorrect, "There must be at least one input variable");
        }
        TaskParamsYaml.InputVariable inputVariable = inputs.get(0);
        if (inputVariable.context== EnumsApi.VariableContext.local) {
            Variable bd = variableRepository.findById(Long.valueOf(inputVariable.id)).orElse(null);
            if (bd == null) {
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.variable_not_found, "Variable not found for code " + inputVariable);
            }
        }
        else {
            GlobalVariable gv = globalVariableRepository.findById(Long.valueOf(inputVariable.id)).orElse(null);
            if (gv == null) {
                return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.global_variable_not_found, "GlobalVariable not found for code " + inputVariable);
            }
        }

        if (true) {
            throw new NotImplementedException("not yet");
        }
        return Consts.INTERNAL_FUNCTION_PROCESSING_RESULT_OK;
    }

}
