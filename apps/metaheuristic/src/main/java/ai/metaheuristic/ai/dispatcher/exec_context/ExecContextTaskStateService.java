/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 10/3/2020
 * Time: 10:22 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextTaskStateService {

    private final ExecContextCache execContextCache;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskExecStateService taskExecStateService;
    private final TaskSyncService taskSyncService;
    private final TaskProviderTopLevelService taskProviderTopLevelService;

    @Transactional
    public OperationStatusRest updateTaskExecStatesInGraph( Long execContextId, Long taskId, EnumsApi.TaskExecState execState, @Nullable String taskContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        taskSyncService.checkWriteLockPresent(taskId);

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(execContext, taskId, execState, taskContextId);
        taskExecStateService.updateTasksStateInDb(status);
        return status.status;
    }

    @Nullable
    @Transactional
    public TaskQueue.TaskGroup transferStateFromTaskQueueToExecContext(Long execContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);

        TaskQueue.TaskGroup taskGroup = taskProviderTopLevelService.getFinishedTaskGroup(execContextId);
        if (taskGroup==null) {
            return null;
        }
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
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
            final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(execContext, task.queuedTask.taskId, task.state, taskContextId);
            taskExecStateService.updateTasksStateInDb(status);
            found = true;
        }
        return found ? taskGroup : null;
    }
}
