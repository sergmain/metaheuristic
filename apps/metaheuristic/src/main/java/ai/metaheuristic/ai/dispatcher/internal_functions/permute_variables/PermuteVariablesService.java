/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.internal_functions.permute_variables;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.permutation.Permutation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class PermuteVariablesService {

    private final VariableService variableTopLevelService;
    private final ExecContextGraphService execContextGraphService;
    private final TaskProducingService taskProducingService;

    /**
     * @param simpleExecContext
     * @param taskId
     * @param executionContextData
     * @param descendants
     * @param holders
     * @param variableName
     * @param subProcessContextId
     * @param producePresentVariable do we need to produce variable, which has boolean value of present variable or not?
     * @param producePresentVariablePrefix
     * @param upperCaseFirstChar
     * @param presentVariable
     * @param variablesAs
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_UNCOMMITTED)
    public void createTaskForPermutations(
            ExecContextApiData.SimpleExecContext simpleExecContext, Long taskId, InternalFunctionData.ExecutionContextData executionContextData,
            Set<ExecContextData.TaskVertex> descendants, List<VariableUtils.VariableHolder> holders, String variableName,
            String subProcessContextId,
            final boolean producePresentVariable, final String producePresentVariablePrefix, boolean upperCaseFirstChar,
            final List<Pair<VariableUtils.VariableHolder, Boolean>> presentVariable,
            final Enums.VariablesAs variablesAs) {

        final AtomicInteger currTaskNumber = new AtomicInteger(0);
        final List<Long> lastIds = new ArrayList<>();
        ExecContextData.GraphAndStates graphAndStates = execContextGraphService.prepareGraphAndStates(simpleExecContext.execContextGraphId, simpleExecContext.execContextTaskStateId);

        if (variablesAs== Enums.VariablesAs.permute) {
            final Permutation<VariableUtils.VariableHolder> permutation = new Permutation<>();
            for (int i = 0; i < holders.size(); i++) {
                try {
                    permutation.printCombination(holders, i + 1,
                            permutedVariables -> createTaskWIthVariables(
                                graphAndStates, simpleExecContext, taskId, executionContextData, variableName, subProcessContextId,
                                producePresentVariable, producePresentVariablePrefix, upperCaseFirstChar, presentVariable,
                                currTaskNumber, lastIds, permutedVariables)
                    );
                }
                catch (BreakFromLambdaException e) {
                    log.error(e.getMessage());
                    throw new InternalFunctionException(
                            new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken, e.getMessage()));
                }
            }
        }
        else if (variablesAs== Enums.VariablesAs.array) {
            try {
                    createTaskWIthVariables(
                        graphAndStates, simpleExecContext, taskId, executionContextData, variableName, subProcessContextId,
                            producePresentVariable, producePresentVariablePrefix, upperCaseFirstChar, presentVariable,
                            currTaskNumber, lastIds, holders);
            }
            catch (BreakFromLambdaException e) {
                log.error(e.getMessage());
                throw new InternalFunctionException(
                        new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken, e.getMessage()));
            }
        }
        else {
            throw new IllegalStateException("unknown Enums.VariablesAs - "+variablesAs);
        }
        execContextGraphService.createEdges(graphAndStates.graph(), lastIds, descendants);
    }

    private boolean createTaskWIthVariables(
            ExecContextData.GraphAndStates graphAndStates, ExecContextApiData.SimpleExecContext simpleExecContext, Long taskId, InternalFunctionData.ExecutionContextData executionContextData,
            String variableName, String subProcessContextId, boolean producePresentVariable, String producePresentVariablePrefix,
            boolean upperCaseFirstChar, List<Pair<VariableUtils.VariableHolder, Boolean>> presentVariable,
            AtomicInteger currTaskNumber, List<Long> lastIds, List<VariableUtils.VariableHolder> permutedVariables) {

        if (log.isInfoEnabled()) {
            log.info(permutedVariables.stream().map(VariableUtils.VariableHolder::getName).collect(Collectors.joining(", ")));
        }

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

        currTaskNumber.incrementAndGet();
        final String currTaskContextId = ContextUtils.buildTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));

        VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(
                new VariableData.Permutation(permutedVariables, variableName, Map.of(),
                        null, null, false),
                booleanVariables);

        variableTopLevelService.createInputVariablesForSubProcess(
                variableDataSource, simpleExecContext.execContextId, variableName, currTaskContextId, true);

        taskProducingService.createTasksForSubProcesses(
            graphAndStates, simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);
        return true;
    }

}
