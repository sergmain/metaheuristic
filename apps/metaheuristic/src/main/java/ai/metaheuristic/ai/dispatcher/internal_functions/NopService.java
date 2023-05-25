/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Serge
 * Date: 6/24/2021
 * Time: 11:36 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class NopService {

    private final InternalFunctionService internalFunctionService;
    private final TaskProducingService taskProducingService;
    private final ExecContextGraphService execContextGraphService;

    @Transactional
    public Void processSubProcesses(ExecContextData.SimpleExecContext simpleExecContext, Long taskId, TaskParamsYaml taskParamsYaml) {
        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSubProcesses(simpleExecContext, taskParamsYaml, taskId);
        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            throw new InternalFunctionException(executionContextData.internalFunctionProcessingResult);
        }

        if (executionContextData.subProcesses.isEmpty()) {
            return null;
        }

        final List<Long> lastIds = new ArrayList<>();
        AtomicInteger currTaskNumber = new AtomicInteger(0);
        String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                taskId, taskParamsYaml.task.taskContextId, executionContextData.subProcesses.get(0).processContextId);

        try {
            String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));

            taskProducingService.createTasksForSubProcesses(
                    simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);

            execContextGraphService.createEdges(simpleExecContext.execContextGraphId, lastIds, executionContextData.descendants);

        } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
            throw e;
        } catch (Throwable th) {
            String es = "#995.300 An error while saving data to file, " + th.getMessage();
            log.error(es, th);
            throw new BatchResourceProcessingException(es);
        }
        return null;
    }
}
