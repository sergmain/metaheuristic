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

package ai.metaheuristic.ai.dispatcher.internal_functions.permute_variables_and_hyper_params_create_tasks;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.utils.ContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Serge
 * Date: 3/25/2021
 * Time: 1:06 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class PermuteVariablesAndInlinesCreateTasksTxService {

    private final VariableService variableService;
    private final ExecContextGraphService execContextGraphService;
    private final TaskProducingService taskProducingService;

    @Transactional
    @SneakyThrows
    public Void createTaskForPermutations(
            ExecContextData.SimpleExecContext simpleExecContext, Long taskId, InternalFunctionData.ExecutionContextData executionContextData,
            Set<ExecContextData.TaskVertex> descendants, String subProcessContextId, List<String> lines) {

        final List<Long> lastIds = new ArrayList<>();
        final AtomicInteger currTaskNumber = new AtomicInteger(0);
        for (String line : lines) {
            currTaskNumber.incrementAndGet();
            VariableData.Permutation p = VariableUtils.asStringAsPermutation(line);

            VariableData.VariableDataSource variableDataSource = new VariableData.VariableDataSource(p);

            String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));

            variableService.createInputVariablesForSubProcess(
                    variableDataSource, simpleExecContext.execContextId, p.permutedVariableName, currTaskContextId);

            taskProducingService.createTasksForSubProcesses(
                    simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);

        }

        execContextGraphService.createEdges(simpleExecContext.execContextGraphId, lastIds, descendants);

        return null;
    }
}
