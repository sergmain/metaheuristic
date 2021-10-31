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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.*;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.commons.permutation.Permutation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 3/25/2021
 * Time: 12:50 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class PermuteVariablesAndInlinesTxService {

    private final VariableService variableService;
    private final ExecContextGraphService execContextGraphService;
    private final TaskProducingService taskProducingService;

    @Transactional
    public Void createTaskFroPermutations(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, InternalFunctionData.ExecutionContextData executionContextData,
            Set<ExecContextData.TaskVertex> descendants, List<VariableUtils.VariableHolder> holders, String variableName,
            String subProcessContextId, @Nullable PermutationData.Inlines inlines,
            final boolean producePresentVariable, final String producePresentVariablePrefix, boolean upperCaseFirstChar,
            final List<Pair<VariableUtils.VariableHolder, Boolean>> presentVariable
    ) {

        final AtomicInteger currTaskNumber = new AtomicInteger(0);
        final List<Long> lastIds = new ArrayList<>();
        final Permutation<VariableUtils.VariableHolder> permutation = new Permutation<>();
        for (int i = 0; i < holders.size(); i++) {
            try {
                permutation.printCombination(holders, i+1,
                        permutedVariables -> {
                            log.info(permutedVariables.stream().map(VariableUtils.VariableHolder::getName).collect(Collectors.joining(", ")));

                            List<Pair<String, Boolean>> booleanVariables = new ArrayList<>();
                            if (producePresentVariable) {
                                for (Pair<VariableUtils.VariableHolder, Boolean> pair : presentVariable) {
                                    final String varName = producePresentVariablePrefix + (upperCaseFirstChar ? StringUtils.capitalize(pair.getLeft().getName()) : pair.getLeft().getName());
                                    boolean present = false;
                                    if (pair.getRight()) {
                                        present = permutedVariables.stream().anyMatch(o -> o.getName().equals(pair.getLeft().getName()));
                                    }
                                    booleanVariables.add(Pair.of(varName, present));
                                }
                            }

                            if (inlines!=null) {
                                for (InlineVariable inlineVariable : inlines.inlineVariables) {
                                    currTaskNumber.incrementAndGet();
                                    Map<String, Map<String, String>> map = new HashMap<>(simpleExecContext.paramsYaml.variables.inline);
                                    map.put(inlines.item.inlineKey, inlineVariable.params);

                                    VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(
                                            new VariableData.Permutation(permutedVariables, variableName, map, inlines.inlineVariableName, inlineVariable.params, true),
                                            booleanVariables);

                                    String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));

                                    variableService.createInputVariablesForSubProcess(
                                            variableDataSource, simpleExecContext.execContextId, variableName, currTaskContextId);

                                    taskProducingService.createTasksForSubProcesses(
                                            simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);
                                }
                            }
                            else {
                                currTaskNumber.incrementAndGet();

                                // TODO 2021-10-14 it's not clear why we need to send simpleExecContext.paramsYaml.variables.inline here
                                VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(
                                        new VariableData.Permutation(permutedVariables, variableName, simpleExecContext.paramsYaml.variables.inline,
                                                null, null, false),
                                        booleanVariables);

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
                        new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken, e.getMessage()));
            }
        }
        execContextGraphService.createEdges(simpleExecContext.execContextGraphId, lastIds, descendants);

        return null;
    }

}
