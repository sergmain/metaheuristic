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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 9/23/2020
 * Time: 12:55 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskExecStateService {

    private final TaskRepository taskRepository;
    private final ExecContextSyncService execContextSyncService;
    private final TaskService taskService;

    @Nullable
    public TaskImpl resetTask(final Long taskId) {
        TxUtils.checkTxExists();
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        log.info("#305.025 Start re-setting task #{}", taskId);
        if (task==null) {
            log.error("#305.030 task is null");
            return null;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        task.setFunctionExecResults(null);
        task.setProcessorId(null);
        task.setAssignedOn(null);
        task.setCompleted(false);
        task.setCompletedOn(null);
        task.setExecState(EnumsApi.TaskExecState.NONE.value);
        task.setResultReceived(false);
        task.setResultResourceScheduledOn(0);
        task = taskService.save(task);

        log.info("#305.035 task #{} was re-setted to initial state", taskId);
        return task;
    }

    public TaskImpl changeTaskState(TaskImpl task, EnumsApi.TaskExecState state){
        TxUtils.checkTxExists();

        log.info("#305.140 set task #{} as {}", task.id, state);
        switch (state) {
            case ERROR:
                throw new IllegalStateException("#305.150 Must be set via ExecContextFSM.finishWithError()");
            case OK:
                if (task.execState==EnumsApi.TaskExecState.OK.value) {
                    log.info("#305.045 Task #{} already has execState as OK", task.id);
                }
                else {
                    task.setExecState(EnumsApi.TaskExecState.OK.value);
                }
                break;
            case IN_PROGRESS:
            case SKIPPED:
            case NONE:
                if (task.execState!=state.value) {
                    task.setExecState(state.value);
                }
                break;
            default:
                throw new IllegalStateException("#305.160 Right now it must be initialized somewhere else. state: " + state);
        }
        return task;
    }

    public void updateTasksStateInDb(ExecContextOperationStatusWithTaskList status) {
        TxUtils.checkTxExists();

        status.childrenTasks.forEach(t -> {
            TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
            if (task != null) {
                changeTaskState(task, t.execState);
            } else {
                log.error("305.180 Graph state is compromised, found task in graph but it doesn't exist in db");
            }
        });
    }


}
