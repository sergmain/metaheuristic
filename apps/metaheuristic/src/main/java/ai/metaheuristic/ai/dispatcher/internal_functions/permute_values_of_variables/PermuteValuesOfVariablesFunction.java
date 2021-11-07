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

package ai.metaheuristic.ai.dispatcher.internal_functions.permute_values_of_variables;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariable;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static ai.metaheuristic.ai.Enums.InternalFunctionProcessing.*;

/**
 * @author Serge
 * Date: 6/28/2021
 * Time: 9:29 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class PermuteValuesOfVariablesFunction implements InternalFunction {

    private final PermuteValuesOfVariablesService permuteValuesOfVariablesService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final InternalFunctionService internalFunctionService;
    private final VariableService variableService;
    private final GlobalVariableService globalVariableService;

    @Override
    public String getCode() {
        return Consts.MH_PERMUTE_VALUES_OF_VARIABLES_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_PERMUTE_VALUES_OF_VARIABLES_FUNCTION;
    }

    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSubProcesses(simpleExecContext, taskParamsYaml, taskId);

        if (executionContextData.internalFunctionProcessingResult.processing!= ok) {
            throw new InternalFunctionException(executionContextData.internalFunctionProcessingResult);
        }

        if (executionContextData.subProcesses.isEmpty()) {
            throw new InternalFunctionException(sub_process_not_found,
                    "#985.040 there isn't any sub-process for process '"+executionContextData.process.processCode+"'");
        }

        Set<ExecContextData.TaskVertex> descendants = execContextGraphTopLevelService.findDescendants(simpleExecContext.execContextGraphId, taskId);
        if (descendants.isEmpty()) {
            throw new InternalFunctionException(broken_graph_error,
                    "#985.060 Graph for ExecContext #"+ simpleExecContext.execContextId +" is broken");
        }

        final ExecContextParamsYaml.Process process = simpleExecContext.paramsYaml.findProcess(taskParamsYaml.task.processCode);
        if (process==null) {
            throw new InternalFunctionException(process_not_found,
                    "#985.080 Process '"+taskParamsYaml.task.processCode+"'not found");
        }

        boolean upperCaseFirstChar = MetaUtils.isTrue(taskParamsYaml.task.metas, "upper-case-first-char");
        final String prefix = MetaUtils.getValue(process.metas, "prefix");
        final String suffix = MetaUtils.getValue(process.metas, "suffix");

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        for (TaskParamsYaml.InputVariable input : taskParamsYaml.task.inputs) {

            String value;
            switch(input.context) {
                case global:
                    value = globalVariableService.getVariableDataAsString(input.id);
                    break;
                case local:
                    value = variableService.getVariableDataAsString(input.id);
                    break;
                case array:
                default:
                    throw new NotImplementedException("#985.100 variable context isn't supported yet - "+ input.context);
            }

            String var = (prefix!=null ? prefix : "") + (upperCaseFirstChar ? StringUtils.capitalize(input.name) : input.name) + (suffix!=null ? suffix : "");
            params.put(var, value);
        }

        final List<InlineVariable> inlineVariables = InlineVariableUtils.getAllInlineVariants(params);


        final String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                taskId, taskParamsYaml.task.taskContextId, executionContextData.subProcesses.get(0).processContextId);

        ExecContextGraphSyncService.getWithSyncVoid(simpleExecContext.execContextGraphId, ()->
                ExecContextTaskStateSyncService.getWithSyncVoid(simpleExecContext.execContextTaskStateId, ()->
                        permuteValuesOfVariablesService.createTaskForPermutations(
                                simpleExecContext, taskId, executionContextData, descendants, subProcessContextId,
                                inlineVariables)));
    }
}
