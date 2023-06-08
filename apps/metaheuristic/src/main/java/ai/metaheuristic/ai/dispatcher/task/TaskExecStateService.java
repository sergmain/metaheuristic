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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.SetTaskExecStateTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
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
    private final EventPublisherService eventPublisherService;

    public void updateTaskExecStates(TaskImpl task, EnumsApi.TaskExecState execState) {
        updateTaskExecStates(task, execState, false);
    }

    public void updateTaskExecStates(TaskImpl task, EnumsApi.TaskExecState execState, boolean markAsCompleted) {
        TxUtils.checkTxExists();
        TaskSyncService.checkWriteLockPresent(task.id);
        TaskImpl t = changeTaskState(task, execState);
        if (markAsCompleted) {
            t.setCompleted(1);
            t.setCompletedOn(System.currentTimeMillis());
        }
    }

    public TaskImpl changeTaskState(TaskImpl task, EnumsApi.TaskExecState state){
        TxUtils.checkTxExists();
        TaskSyncService.checkWriteLockPresent(task.id);

        log.info("#305.140 set the state of Task #{} as {}, Processor #{}", task.id, state, task.coreId);
        switch (state) {
            case ERROR:
            case ERROR_WITH_RECOVERY:
                throw new IllegalStateException("#305.150 Must be set via ExecContextFSM.finishWithError()");
            case OK:
                if (task.execState==EnumsApi.TaskExecState.OK.value) {
                    log.info("#305.045 Task #{} already has execState as OK", task.id);
                }
                else {
                    task.execState = EnumsApi.TaskExecState.OK.value;
                }
                break;
            case IN_PROGRESS:
            case SKIPPED:
            case NONE:
                if (task.execState!=state.value) {
                    task.execState = state.value;
                }
                break;
            default:
                throw new IllegalStateException("#305.160 Right now it must be initialized somewhere else. state: " + state);
        }


        final SetTaskExecStateTxEvent event;
        final EnumsApi.TaskExecState execState = EnumsApi.TaskExecState.from(task.execState);
        if (execState== EnumsApi.TaskExecState.OK || execState== EnumsApi.TaskExecState.ERROR) {
            TaskParamsYaml taskParams = task.getTaskParamsYaml();
            event = new SetTaskExecStateTxEvent(task.execContextId, task.id, execState, task.coreId, taskParams.task.context, taskParams.task.function.code);
        }
        else {
            event = new SetTaskExecStateTxEvent(task.execContextId, task.id, execState, task.coreId, null, null);
        }
        eventPublisherService.publishSetTaskExecStateTxEvent(event);
        return task;
    }

    public void updateTasksStateInDb(ExecContextOperationStatusWithTaskList status) {
        TxUtils.checkTxExists();

        status.childrenTasks.forEach(
                t -> TaskSyncService.getWithSyncNullable(t.taskId, () -> {
                    TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
                    if (task != null) {
                        changeTaskState(task, t.state);
                    } else {
                        log.error("305.180 Graph state is compromised, found task in graph but it doesn't exist in db");
                    }
                    return null;
                })
        );
    }


}
