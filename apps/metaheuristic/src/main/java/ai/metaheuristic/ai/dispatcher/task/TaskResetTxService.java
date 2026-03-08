/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskResettingService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * @author Sergio Lissner
 * Date: 3/7/2026
 * Time: 2:14 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskResetTxService {

    private final ExecContextCache execContextCache;
    private final ExecContextTaskResettingService execContextTaskResettingService;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public void resetTaskAndExecContextTx(Long execContextId, Long taskId) {
        resetTaskAndExecContext(execContextId, taskId);
    }

    public void resetTaskAndExecContext(Long execContextId, Long taskId) {
        TxUtils.checkTxExists();

        ExecContextSyncService.checkWriteLockPresent(execContextId);
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec == null) {
            return;
        }
        ExecContextTaskStateSyncService.checkWriteLockPresent(ec.execContextTaskStateId);

        // Set ExecContext to STARTED if it was FINISHED
        if (ec.state == EnumsApi.ExecContextState.FINISHED.code) {
            ec.setState(EnumsApi.ExecContextState.STARTED.code);
            ec.setCompletedOn(null);
            execContextCache.save(ec);
            log.info("801.200 ExecContext #{} state changed from FINISHED to STARTED", execContextId);
        }

        // Reset the specified task in DB
        TaskSyncService.getWithSyncVoid(taskId, () ->
            execContextTaskResettingService.resetTask(ec, taskId, EnumsApi.TaskExecState.NONE));

        // Reset all descendant tasks in the DAG (downstream tasks including mh.finish)
        Set<ExecContextData.TaskVertex> descendants = execContextGraphService.findDescendants(
            execContextId, ec.execContextGraphId, taskId);

        log.info("801.210 Found {} descendant tasks to reset for task #{}", descendants.size(), taskId);

        for (ExecContextData.TaskVertex descendant : descendants) {
            TaskSyncService.getWithSyncVoid(descendant.taskId, () ->
                execContextTaskResettingService.resetTask(ec, descendant.taskId, EnumsApi.TaskExecState.NONE));
            log.info("801.215 Reset descendant task #{}", descendant.taskId);
        }

        int _=0;

        // Update graph state (ExecContextTaskState) to mark reset tasks as NONE
        ExecContextTaskState execContextTaskState = execContextTaskStateRepository.findById(ec.execContextTaskStateId).orElse(null);
        if (execContextTaskState == null) {
            log.error("801.230 ExecContextTaskState #{} not found", ec.execContextTaskStateId);
            return;
        }
        ExecContextTaskStateParamsYaml stateParams = execContextTaskState.getExecContextTaskStateParamsYaml();
        stateParams.states.put(taskId, EnumsApi.TaskExecState.NONE);
        for (ExecContextData.TaskVertex descendant : descendants) {
            stateParams.states.put(descendant.taskId, EnumsApi.TaskExecState.NONE);
        }
        execContextTaskState.updateParams(stateParams);
        execContextTaskStateRepository.save(execContextTaskState);
        log.info("801.240 Updated graph state for task #{} and {} descendants to NONE", taskId, descendants.size());

        // Trigger task assignment so the scheduler picks up the reset tasks
        eventPublisherService.handleFindUnassignedTasksAndRegisterInQueueEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());
    }
}
