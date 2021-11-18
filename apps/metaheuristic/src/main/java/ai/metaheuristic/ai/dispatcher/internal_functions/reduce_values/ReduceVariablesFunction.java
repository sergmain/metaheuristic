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

package ai.metaheuristic.ai.dispatcher.internal_functions.reduce_values;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.ReduceVariablesData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYaml;
import ai.metaheuristic.ai.yaml.reduce_values_function.ReduceVariablesConfigParamsYamlUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.File;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.number_of_inputs_is_incorrect;
import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.number_of_outputs_is_incorrect;

/**
 * @author Serge
 * Date: 7/2/2021
 * Time: 9:36 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ReduceVariablesFunction implements InternalFunction {

    private final VariableService variableService;
    private final GlobalVariableService globalVariableService;
    private final ExecContextVariableService execContextVariableService;

    @Override
    public String getCode() {
        return Consts.MH_REDUCE_VARIABLES_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_REDUCE_VARIABLES_FUNCTION;
    }

    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        if (taskParamsYaml.task.inputs.size()!=1) {
            throw new InternalFunctionException(number_of_inputs_is_incorrect, "#961.040 there must be only one input variable, actual count: " + taskParamsYaml.task.inputs.size());
        }

        if (taskParamsYaml.task.outputs.size()!=1) {
            throw new InternalFunctionException(number_of_outputs_is_incorrect, "#961.080 only one output variable is supported, actual count: " + taskParamsYaml.task.outputs.size());
        }

        TaskParamsYaml.InputVariable input = taskParamsYaml.task.inputs.get(0);

        ReduceVariablesConfigParamsYaml config = initConfig(taskParamsYaml);
        ReduceVariablesData.Request request = initRequestData(simpleExecContext.execContextId, taskContextId, config);


        File tempDir = DirUtils.createMhTempDir("reduce-variables-");
        File zipFile = new File(tempDir, "zip.zip");
        variableService.storeToFile(input.id, zipFile);

        ReduceVariablesUtils.reduceVariables(zipFile, config, request);


/*
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
*/
    }

    private static ReduceVariablesConfigParamsYaml initConfig(TaskParamsYaml taskParamsYaml) {
        String yaml = MetaUtils.getValue(taskParamsYaml.task.metas, "config");
        if (S.b(yaml)) {
            throw new InternalFunctionException(Enums.InternalFunctionProcessing.meta_not_found,
                            "#961.120 Meta 'config' wasn't found or empty, process: "+ taskParamsYaml.task.processCode);
        }

        ReduceVariablesConfigParamsYaml config = ReduceVariablesConfigParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        return config;
    }

    public ReduceVariablesData.Request initRequestData(Long execContextId, String taskContextId, ReduceVariablesConfigParamsYaml config) {
        ReduceVariablesData.Request request = new ReduceVariablesData.Request();

        for (ReduceVariablesConfigParamsYaml.ByInstance byInstance : config.config.reduceByInstance) {
            SimpleVariable sv = variableService.findVariableInAllInternalContexts(byInstance.inputIs, taskContextId, execContextId);
            if (sv==null) {
                throw new InternalFunctionException(Enums.InternalFunctionProcessing.meta_not_found,
                                "#961.200 Input variable "+byInstance.inputIs+" wasn't found");
            }
            String content = variableService.getVariableDataAsString(sv.id);
            if (S.b(content)) {
                throw new InternalFunctionException(Enums.InternalFunctionProcessing.meta_not_found,
                        "#961.240 Input variable "+byInstance.inputIs+" is empty");
            }
            request.nullifiedVars.put(sv.variable, Boolean.valueOf(content));
        }

        return request;
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

