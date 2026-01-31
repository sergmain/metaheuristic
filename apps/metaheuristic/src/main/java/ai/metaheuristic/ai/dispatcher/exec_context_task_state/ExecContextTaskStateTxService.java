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

package ai.metaheuristic.ai.dispatcher.exec_context_task_state;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 10/3/2020
 * Time: 10:22 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextTaskStateTxService {

    private final ExecContextGraphService execContextGraphService;
    private final TaskExecStateService taskExecStateService;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final EventPublisherService eventPublisherService;

    @Transactional(rollbackFor = CommonRollbackException.class)
    public OperationStatusRest updateTaskExecStatesInGraph(ExecContextData.ExecContextDAC execContextDAC, Long execContextTaskStateId, List<TaskData.TaskWithStateAndTaskContextId> taskWithStates) {
        ExecContextTaskStateSyncService.checkWriteLockPresent(execContextTaskStateId);

        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(
            execContextDAC, execContextTaskStateId, taskWithStates);

        taskExecStateService.updateTasksStateInDb(status);
        eventPublisherService.handleFindUnassignedTasksAndRegisterInQueueEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());

        return status.status;
    }

    @Transactional(rollbackFor = CommonRollbackException.class)
    public TaskQueue.TaskGroups transferStateFromTaskQueueToExecContext(ExecContextData.ExecContextDAC execContextDAC, Long execContextId, Long execContextTaskStateId) {
        ExecContextTaskStateSyncService.checkWriteLockPresent(execContextTaskStateId);

        TaskQueue.TaskGroups taskGroups = TaskProviderTopLevelService.getTaskGroupForTransferring(execContextId);
        if (taskGroups.groups.isEmpty()) {
            throw new CommonRollbackException();
        }
        List<TaskData.TaskWithStateAndTaskContextId> taskWithStates = new ArrayList<>(TaskQueue.GROUP_SIZE_DEFAULT * 10);
        for (TaskQueue.TaskGroup group : taskGroups.groups) {
            for (TaskQueue.AllocatedTask task : group.tasks) {
                if (task==null) {
                    continue;
                }
                if (task.queuedTask.execContext != EnumsApi.FunctionExecContext.internal) {
                    if (task.queuedTask.task == null) {
                        throw new IllegalStateException("(task.queuedTask.task==null), need to investigate");
                    }
                    if (!task.queuedTask.execContextId.equals(task.queuedTask.task.execContextId)) {
                        throw new IllegalStateException("(!task.queuedTask.execContextId.equals(task.queuedTask.task.execContextId))");
                    }
                }
                if (task.queuedTask.taskParamYaml==null) {
                    throw new IllegalStateException("(task.queuedTask.taskParamYaml==null)");
                }
                String taskContextId = task.queuedTask.taskParamYaml.task.taskContextId;
                taskWithStates.add(new TaskData.TaskWithStateAndTaskContextId(task.queuedTask.taskId, task.state, taskContextId));
            }
        }
        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(execContextDAC, execContextTaskStateId, taskWithStates);

        taskExecStateService.updateTasksStateInDb(status);
        return taskGroups;
    }

    @Transactional
    public Void deleteOrphanTaskStates(List<Long> ids) {
        execContextTaskStateRepository.deleteAllByIdIn(ids);
        return null;
    }

}
