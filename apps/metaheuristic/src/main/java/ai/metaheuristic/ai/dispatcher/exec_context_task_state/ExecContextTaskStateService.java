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

package ai.metaheuristic.ai.dispatcher.exec_context_task_state;

import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static ai.metaheuristic.api.EnumsApi.*;

/**
 * @author Serge
 * Date: 10/3/2020
 * Time: 10:22 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextTaskStateService {

    private final ExecContextGraphService execContextGraphService;
    private final TaskExecStateService taskExecStateService;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public OperationStatusRest updateTaskExecStatesInGraph(Long execContextGraphId, Long execContextTaskStateId, Long taskId, TaskExecState execState, String taskContextId) {
        ExecContextTaskStateSyncService.checkWriteLockPresent(execContextTaskStateId);
        TaskSyncService.checkWriteLockPresent(taskId);

        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(
                execContextGraphId, execContextTaskStateId, taskId, execState, taskContextId);

        taskExecStateService.updateTasksStateInDb(status);
        eventPublisherService.handleFindUnassignedTasksAndRegisterInQueueEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());

        return status.status;
    }

    @Nullable
    @Transactional
    public TaskQueue.TaskGroup transferStateFromTaskQueueToExecContext(Long execContextId, Long execContextGraphId, Long execContextTaskStateId) {
        ExecContextGraphSyncService.checkWriteLockPresent(execContextGraphId);
        ExecContextTaskStateSyncService.checkWriteLockPresent(execContextTaskStateId);

        TaskQueue.TaskGroup taskGroup = TaskProviderTopLevelService.getTaskGroupForTransfering(execContextId);
        if (taskGroup==null) {
            return null;
        }
        boolean found = false;
        for (TaskQueue.AllocatedTask task : taskGroup.tasks) {
            if (task==null) {
                continue;
            }

            if (task.queuedTask.taskParamYaml==null) {
                throw new IllegalStateException("(task.queuedTask.taskParamYaml==null)");
            }
            String taskContextId = task.queuedTask.taskParamYaml.task.taskContextId;
            final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(
                    execContextGraphId, execContextTaskStateId, task.queuedTask.taskId, task.state, taskContextId);

            taskExecStateService.updateTasksStateInDb(status);
            found = true;
        }
        return found ? taskGroup : null;
    }

    @Transactional
    public Void deleteOrphanTaskStates(List<Long> ids) {
        execContextTaskStateRepository.deleteAllByIdIn(ids);
        return null;
    }

}
