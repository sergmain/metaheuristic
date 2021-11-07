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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.ContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Serge
 * Date: 6/28/2021
 * Time: 10:12 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class PermuteValuesOfVariablesService {

    private final VariableService variableService;
    private final ExecContextGraphService execContextGraphService;
    private final TaskProducingService taskProducingService;

    @Transactional
    public void createTaskForPermutations(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, InternalFunctionData.ExecutionContextData executionContextData,
            Set<ExecContextData.TaskVertex> descendants, String subProcessContextId, List<InlineVariable> inlineVariables) {

        final AtomicInteger currTaskNumber = new AtomicInteger(0);
        final List<Long> lastIds = new ArrayList<>();
        for (InlineVariable inlineVariable : inlineVariables) {
            try {
                currTaskNumber.incrementAndGet();
                String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));

                for (Map.Entry<String, String> entry : inlineVariable.params.entrySet()) {
                    VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(entry.getValue());
                    variableService.createInputVariablesForSubProcess(
                            variableDataSource, simpleExecContext.execContextId, entry.getKey(), currTaskContextId, false);
                }
                taskProducingService.createTasksForSubProcesses(
                        simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);

            } catch (BreakFromLambdaException e) {
                log.error(e.getMessage());
                throw new InternalFunctionException(
                        new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken, e.getMessage()));
            }
        }
        execContextGraphService.createEdges(simpleExecContext.execContextGraphId, lastIds, descendants);
    }


}
