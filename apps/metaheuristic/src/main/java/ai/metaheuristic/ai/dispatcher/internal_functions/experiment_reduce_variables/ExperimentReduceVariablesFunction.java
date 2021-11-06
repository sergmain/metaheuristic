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

package ai.metaheuristic.ai.dispatcher.internal_functions.experiment_reduce_variables;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * @author Serge
 * Date: 11/4/2021
 * Time: 8:06 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ExperimentReduceVariablesFunction implements InternalFunction {

    private final VariableService variableService;
    private final GlobalVariableService globalVariableService;
    private final ExecContextVariableService execContextVariableService;
    @Override
    public String getCode() {
        return Consts.MH_EXPERIMENT_REDUCE_VARIABLES;
    }

    @Override
    public String getName() {
        return Consts.MH_EXPERIMENT_REDUCE_VARIABLES;
    }

    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        if (taskParamsYaml.task.inputs.size()!=2) {
            throw new InternalFunctionException(number_of_inputs_is_incorrect, "#983.020 there must be only two input variables, actual count: " + taskParamsYaml.task.inputs.size());
        }

        if (taskParamsYaml.task.outputs.size()!=1) {
            throw new InternalFunctionException(number_of_outputs_is_incorrect, "#983.040 only one output variable is supported, actual count: " + taskParamsYaml.task.outputs.size());
        }

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        TaskParamsYaml.InputVariable filter = taskParamsYaml.task.inputs.stream().filter(o->"filter".equals(o.type)).findFirst().orElseThrow(
                ()-> new InternalFunctionException(number_of_outputs_is_incorrect, "#983.060 input variable with 'filter' type wasn't found"));

        TaskParamsYaml.InputVariable input = taskParamsYaml.task.inputs.stream().filter(o->!"filter".equals(o.type)).findFirst().orElseThrow(
                ()-> new InternalFunctionException(number_of_outputs_is_incorrect, "#983.080 input variable with actual values wasn't found"));

        String filterValue = getValue(filter);
        if (S.b(filterValue)) {
            throw new InternalFunctionException(variable_not_found, "#983.085 value of filter variable is blank");
        }
        String inputValue = getValue(input);
        if (S.b(filterValue)) {
            throw new InternalFunctionException(variable_not_found, "#983.090 value of input variable is blank");
        }

        InlineVariableUtils.NumberOfVariants filterOfVariants = InlineVariableUtils.getNumberOfVariants(filterValue);
        InlineVariableUtils.NumberOfVariants inputOfVariants = InlineVariableUtils.getNumberOfVariants(inputValue);

        String newValues = inputOfVariants.values.stream().filter(o->!filterOfVariants.values.contains(o)).collect(Collectors.joining(", ", "[", "]"));

        final TaskParamsYaml.OutputVariable outputVariable = taskParamsYaml.task.outputs.get(0);
        execContextVariableService.storeStringInVariable(outputVariable, newValues);
    }

    @Nullable
    private String getValue(TaskParamsYaml.InputVariable input) {
        String value = null;
        switch(input.context) {
            case global:
                value = globalVariableService.getVariableDataAsString(input.id);
                break;
            case local:
                value = variableService.getVariableDataAsString(input.id);
                break;
            case array:
            default:
                throw new NotImplementedException("#983.100 variable context isn't supported yet - "+ input.context);
        }
        return value;
    }
}
