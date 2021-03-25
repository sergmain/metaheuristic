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
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class PermuteVariablesAndInlinesFunction implements InternalFunction {

    private final VariableService variableService;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final ExecContextGraphService execContextGraphService;
    private final TaskProducingService taskProducingService;
    private final InternalFunctionService internalFunctionService;

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
        TxUtils.checkTxExists();

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
        final List<Long> lastIds = new ArrayList<>();
        final List<InlineVariable> inlineVariables = permuteInlines ? InlineVariableUtils.getAllInlineVariants(item.inlines) : List.of();
        AtomicInteger currTaskNumber = new AtomicInteger(0);
        String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                taskId, taskParamsYaml.task.taskContextId, executionContextData.subProcesses.get(0).processContextId);

        final Permutation<VariableUtils.VariableHolder> permutation = new Permutation<>();
        for (int i = 0; i < holders.size(); i++) {
            try {
                permutation.printCombination(holders, i+1,
                        permutedVariables -> {
                            log.info(permutedVariables.stream().map(VariableUtils.VariableHolder::getName).collect(Collectors.joining(", ")));
                            if (permuteInlines) {
                                for (InlineVariable inlineVariable : inlineVariables) {
                                    currTaskNumber.incrementAndGet();
                                    Map<String, Map<String, String>> map = new HashMap<>(simpleExecContext.paramsYaml.variables.inline);
                                    map.put(item.inlineKey, inlineVariable.params);

                                    VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(
                                            new VariableData.Permutation(permutedVariables, variableName, map, inlineVariableName, inlineVariable.params));

                                    String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));

                                    variableService.createInputVariablesForSubProcess(
                                            variableDataSource, simpleExecContext.execContextId, variableName, currTaskContextId);

                                    taskProducingService.createTasksForSubProcesses(
                                            simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);
                                }
                            }
                            else {
                                currTaskNumber.incrementAndGet();

                                VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(
                                        new VariableData.Permutation(permutedVariables, variableName, simpleExecContext.paramsYaml.variables.inline, inlineVariableName,Map.of()));

                                String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));

                                variableService.createInputVariablesForSubProcess(
                                        variableDataSource, simpleExecContext.execContextId, variableName, currTaskContextId);

                                taskProducingService.createTasksForSubProcesses(
                                        simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);
                            }
                            return true;
                        }
                );
            } catch (BreakFromLambdaException e) {
                log.error(e.getMessage());
                throw new InternalFunctionException(
                    new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken, e.getMessage()));
            }
        }
        execContextGraphService.createEdges(simpleExecContext.execContextGraphId, lastIds, descendants);
    }
}
