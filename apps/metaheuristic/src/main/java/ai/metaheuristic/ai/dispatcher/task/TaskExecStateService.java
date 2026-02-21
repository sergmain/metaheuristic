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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.ChangeTaskStateToInitForChildrenTasksTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.InitVariablesTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.event.events.SetTaskExecStateInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static ai.metaheuristic.api.EnumsApi.TaskExecState.*;

/**
 * @author Serge
 * Date: 9/23/2020
 * Time: 12:55 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskExecStateService {

    private final TaskRepository taskRepository;
    private final EventPublisherService eventPublisherService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(rollbackFor = CommonRollbackException.class)
    public void updateTaskExecStates(Long taskId, EnumsApi.TaskExecState execState) {
        TaskImpl task = taskRepository.findById(taskId).orElseThrow();
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

    private TaskImpl changeTaskState(TaskImpl task, EnumsApi.TaskExecState state){
        TxUtils.checkTxExists();
        TaskSyncService.checkWriteLockPresent(task.id);

        log.info("305.040 set the state of Task #{} as {}, Processor #{}", task.id, state, task.coreId);
        switch (state) {
            case ERROR:
            case ERROR_WITH_RECOVERY:
                throw new IllegalStateException("305.080 Must be set via ExecContextFSM.finishWithError()");
            case OK:
                if (task.execState==EnumsApi.TaskExecState.OK.value) {
                    log.info("305.120 Task #{} already has execState as OK", task.id);
                }
                else {
                    task.execState = EnumsApi.TaskExecState.OK.value;
                }
                break;
            case IN_PROGRESS:
            case SKIPPED:
            case NONE:
            case INIT:
            case PRE_INIT:
            case CHECK_CACHE:
                if (task.execState!=state.value) {
                    // Prevent downgrading from a finished state (SKIPPED, ERROR, OK) to a non-finished state (INIT, NONE, etc.)
                    // This guards against race conditions between async event handlers
                    if (EnumsApi.TaskExecState.isFinishedState(task.execState) && !EnumsApi.TaskExecState.isFinishedState(state)) {
                        log.warn("305.140 Prevented state downgrade for Task #{} from {} to {}", task.id, EnumsApi.TaskExecState.from(task.execState), state);
                        break;
                    }
                    task.execState = state.value;
                }
                break;
            default:
                throw new IllegalStateException("305.160 Right now it must be initialized somewhere else. state: " + state);
        }
        if (state==NONE) {
            eventPublisher.publishEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());
        }
        if (state==INIT) {
            eventPublisher.publishEvent(new InitVariablesTxEvent(task.id));
        }
        if (EnumsApi.TaskExecState.isFinishedState(state)) {
            eventPublisher.publishEvent(new ChangeTaskStateToInitForChildrenTasksTxEvent(task.id));
        }

        final SetTaskExecStateInQueueTxEvent event = getSetTaskExecStateInQueueTxEvent(task);
        eventPublisherService.publishSetTaskExecStateInQueueTxEvent(event);
        return task;
    }

    private static SetTaskExecStateInQueueTxEvent getSetTaskExecStateInQueueTxEvent(TaskImpl task) {
        final SetTaskExecStateInQueueTxEvent event;
        final EnumsApi.TaskExecState execState = EnumsApi.TaskExecState.from(task.execState);
        if (execState== EnumsApi.TaskExecState.OK || execState== EnumsApi.TaskExecState.ERROR) {
            TaskParamsYaml taskParams = task.getTaskParamsYaml();
            event = new SetTaskExecStateInQueueTxEvent(task.execContextId, task.id, execState, task.coreId, taskParams.task.context, taskParams.task.function.code);
        }
        else {
            event = new SetTaskExecStateInQueueTxEvent(task.execContextId, task.id, execState, task.coreId, null, null);
        }
        return event;
    }

    public void updateTasksStateInDb(ExecContextOperationStatusWithTaskList status) {
        TxUtils.checkTxExists();

        for (TaskData.TaskWithState t : status.childrenTasks) {
            if (t.state!=SKIPPED) {
                // rn only SKIPPED state must go here
                throw new IllegalStateException("(t.state!=SKIPPED)");
            }
            TaskSyncService.getWithSyncVoid(t.taskId, () -> {
                TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
                if (task != null) {
                    updateTaskExecStates(task, t.state, true);
                    taskRepository.save(task);
                } else {
                    log.error("305.200 Graph state is compromised, found task in graph but it doesn't exist in db");
                }
            });
        }
    }


}
