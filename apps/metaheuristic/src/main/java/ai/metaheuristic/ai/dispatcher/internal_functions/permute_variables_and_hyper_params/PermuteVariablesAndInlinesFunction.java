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

package ai.metaheuristic.ai.dispatcher.internal_functions.permute_variables_and_hyper_params;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InlineVariableData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunction;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariable;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.commons.permutation.Permutation;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 2/1/2020
 * Time: 9:17 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class PermuteVariablesAndInlinesFunction implements InternalFunction {

    private final PermuteVariablesAndInlinesTxService permuteVariablesAndInlinesTxService;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final InternalFunctionService internalFunctionService;
    private final ExecContextGraphSyncService execContextGraphSyncService;
    private final ExecContextTaskStateSyncService execContextTaskStateSyncService;

    @Override
    public String getCode() {
        return Consts.MH_PERMUTE_VARIABLES_AND_INLINES_FUNCTION;
    }

    @Override
    public String getName() {
        return Consts.MH_PERMUTE_VARIABLES_AND_INLINES_FUNCTION;
    }

    @Override
    public void process(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, String taskContextId,
            TaskParamsYaml taskParamsYaml) {
        TxUtils.checkTxNotExists();

        if (CollectionUtils.isNotEmpty(taskParamsYaml.task.inputs)) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.number_of_inputs_is_incorrect,
                    "#991.020 The function 'mh.permute-variables-and-inlines' can't have input variables, process code: '" + taskParamsYaml.task.processCode+"'"));
        }

        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSubProcesses(simpleExecContext, taskParamsYaml, taskId);

        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            throw new InternalFunctionException(
                executionContextData.internalFunctionProcessingResult);
        }

        if (executionContextData.subProcesses.isEmpty()) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.sub_process_not_found,
                    "#991.040 there isn't any sub-process for process '"+executionContextData.process.processCode+"'"));
        }


        Set<ExecContextData.TaskVertex> descendants = execContextGraphTopLevelService.findDescendants(simpleExecContext.execContextGraphId, taskId);
        if (descendants.isEmpty()) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.broken_graph_error,
                    "#991.060 Graph for ExecContext #"+ simpleExecContext.execContextId +" is broken"));
        }

        final ExecContextParamsYaml.Process process = simpleExecContext.paramsYaml.findProcess(taskParamsYaml.task.processCode);
        if (process==null) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.process_not_found,
                    "#991.080 Process '"+taskParamsYaml.task.processCode+"'not found"));
        }

        // variableNames contains a list of variables for permutation
        String variableNames = MetaUtils.getValue(taskParamsYaml.task.metas, "variables-for-permutation");
        if (S.b(variableNames)) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "#991.100 Meta 'variables-for-permutation' must be defined and can't be empty"));
        }
        String[] names = StringUtils.split(variableNames, ", ");

        boolean permuteInlines = MetaUtils.isTrue(taskParamsYaml.task.metas, InlineVariableUtils.PERMUTE_INLINE);

        InlineVariableData.InlineVariableItem item = InlineVariableUtils.getInlineVariableItem(simpleExecContext.paramsYaml.variables, taskParamsYaml.task.metas);
        if (S.b(item.inlineKey)) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "#991.120 Meta 'inline-key' wasn't found or empty."));
        }
        if (item.inlines == null || item.inlines.isEmpty()) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.inline_not_found,
                    "#991.140 Inline variable '" + item.inlineKey + "' wasn't found or empty. List of keys in inlines: " + simpleExecContext.paramsYaml.variables.inline.keySet()));
        }

        List<VariableUtils.VariableHolder> holders = internalFunctionVariableService.discoverVariables(simpleExecContext.execContextId, taskContextId, names);

        final String variableName = MetaUtils.getValue(process.metas, "output-variable");
        if (S.b(variableName)) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "#991.160 Meta with key 'output-variable' wasn't found for process '"+process.processCode+"'"));
        }
        final String inlineVariableName = MetaUtils.getValue(process.metas, "inline-permutation");
        if (S.b(inlineVariableName)) {
            throw new InternalFunctionException(
                new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.meta_not_found,
                    "#991.180 Meta with key 'inline-permutation' wasn't found for process '"+process.processCode+"'"));
        }
        final List<InlineVariable> inlineVariables = permuteInlines ? InlineVariableUtils.getAllInlineVariants(item.inlines) : List.of();
        final String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                taskId, taskParamsYaml.task.taskContextId, executionContextData.subProcesses.get(0).processContextId);

        execContextGraphSyncService.getWithSync(simpleExecContext.execContextGraphId, ()->
                execContextTaskStateSyncService.getWithSync(simpleExecContext.execContextTaskStateId, ()->
                        permuteVariablesAndInlinesTxService.createTaskFroPermutations(
                                simpleExecContext, taskId, executionContextData, descendants, permuteInlines,
                                item, holders, variableName, inlineVariableName, inlineVariables, subProcessContextId)));
    }

}
