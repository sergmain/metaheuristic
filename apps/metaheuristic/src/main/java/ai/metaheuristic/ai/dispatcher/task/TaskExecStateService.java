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
import ai.metaheuristic.api.dispatcher.Task;
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
    public Task markAsCompleted(final Long taskId) {
        TxUtils.checkTxExists();
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        log.info("#305.010 Start re-setting task #{}", taskId);
        if (task==null) {
            log.error("#305.015 task is null");
            return null;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        task.setCompleted(true);
        task.setCompletedOn(System.currentTimeMillis());

        task = taskService.save(task);

        log.info("#305.020 task #{} was marked as completed", taskId);
        return task;
    }

    @Nullable
    public Task resetTask(final Long taskId) {
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

    private TaskImpl toInProgressSimpleLambda(TaskImpl task) {
        task.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        return taskService.save(task);
    }

    private TaskImpl toSkippedSimpleLambda(TaskImpl task) {
        task.setExecState(EnumsApi.TaskExecState.SKIPPED.value);
        return taskService.save(task);
    }

    @Nullable
    private TaskImpl toOkSimple(Long taskId) {
        TxUtils.checkTxExists();
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#305.040 Can't find Task for Id: {}", taskId);
            return null;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);
        if (task.execState==EnumsApi.TaskExecState.OK.value) {
            log.info("#305.045 Task #{} already has execState as OK", task.id);
            return task;
        }
        task.setExecState(EnumsApi.TaskExecState.OK.value);
        return taskService.save(task);
    }

    @Nullable
    private TaskImpl toNoneSimple(Long taskId) {
        TxUtils.checkTxExists();
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#305.050 Can't find Task for Id: {}", taskId);
            return null;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);
        if (task.execState==EnumsApi.TaskExecState.NONE.value) {
            return null;
        }
        task.setExecState(EnumsApi.TaskExecState.NONE.value);
        return taskService.save(task);
    }

    @Nullable
    private TaskImpl toInProgressSimple(Long taskId) {
        TxUtils.checkTxExists();
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#305.100 Can't find Task for Id: {}", taskId);
            return null;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);
        return toInProgressSimpleLambda(task);
    }

    @Nullable
    private TaskImpl toSkippedSimple(Long taskId) {
        TxUtils.checkTxExists();
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#305.120 Can't find Task for Id: {}", taskId);
            return null;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);
        return toSkippedSimpleLambda(task);
    }

    @Nullable
    public TaskImpl changeTaskState(Long taskId, EnumsApi.TaskExecState state){
        TxUtils.checkTxExists();

        log.info("#305.140 set task #{} as {}", taskId, state);
        switch (state) {
            case NONE:
                return toNoneSimple(taskId);
            case ERROR:
                throw new IllegalStateException("#305.150 Must be set via ExecContextFSM.finishWithErrorWithTx()");
            case OK:
                return toOkSimple(taskId);
            case IN_PROGRESS:
                return toInProgressSimple(taskId);
            case SKIPPED:
                return toSkippedSimple(taskId);
            default:
                throw new IllegalStateException("#305.160 Right now it must be initialized somewhere else. state: " + state);
        }
    }

    public void updateTasksStateInDb(ExecContextOperationStatusWithTaskList status) {
        TxUtils.checkTxExists();

        status.childrenTasks.forEach(t -> {
            TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
            if (task != null) {
                if (task.execState != t.execState.value) {
                    changeTaskState(task.id, t.execState);
                }
            } else {
                log.error("305.180 Graph state is compromised, found task in graph but it doesn't exist in db");
            }
        });
    }


}
