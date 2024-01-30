/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.dispatcher.event.events.*;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * @author Serge
 * Date: 12/20/2020
 * Time: 2:01 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EventsBoundedToTx {

    private final ApplicationEventPublisher eventPublisher;
    private final DispatcherEventService dispatcherEventService;

    // TransactionPhase.AFTER_COMMIT

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSetTaskExecStateTxEvent(SetTaskExecStateInQueueTxEvent event) {
        log.debug("call EventsBoundedToTx.handleSetTaskExecStateTxEvent(execContextId:#{}, taskId:#{}, state:{})", event.execContextId, event.taskId, event.state);
        eventPublisher.publishEvent(event.to());
        if (event.state== EnumsApi.TaskExecState.OK || event.state== EnumsApi.TaskExecState.ERROR) {
            dispatcherEventService.publishTaskEvent(
                    event.state== EnumsApi.TaskExecState.OK ? EnumsApi.DispatcherEventType.TASK_FINISHED : EnumsApi.DispatcherEventType.TASK_ERROR,
                    event.coreId, event.taskId,
                    event.execContextId, event.context, event.funcCode);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCheckTaskCanBeFinishedTxEvent(CheckTaskCanBeFinishedTxEvent event) {
        log.debug("call EventsBoundedToTx.handleCheckTaskCanBeFinishedTxEvent(execContextId:#{}, taskId:#{})", event.execContextId, event.taskId);
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVariableUploadedTxEvent(VariableUploadedTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskCreatedTxEvent(TaskCreatedTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUpdateTaskExecStatesInGraphTxEvent(UpdateTaskExecStatesInExecContextTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStartTaskProcessingTxEvent(StartTaskProcessingTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSetVariableReceivedTxEvent(SetVariableReceivedTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProcessDeletedExecContextTxEvent(ProcessDeletedExecContextTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskQueueCleanByExecContextIdTxEvent(TaskQueueCleanByExecContextIdTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDeleteExecContextTxEvent(DeleteExecContextTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDeleteExecContextInListTxEvent(DeleteExecContextInListTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUnAssignTaskTxEventAfterCommit(UnAssignTaskTxAfterCommitEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUnAssignTaskTxEventAfterCommit(FindUnassignedTasksAndRegisterInQueueTxEvent event) {
        eventPublisher.publishEvent(new FindUnassignedTasksAndRegisterInQueueEvent());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInitVariablesTxEventAfterCommit(InitVariablesTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleChangeTaskStateToInitForChildrenTasksTxEventAfterCommit(ChangeTaskStateToInitForChildrenTasksTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRegisterFunctionCodesForStartedExecContextEvent(RegisterFunctionCodesForStartedExecContextTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    // TransactionPhase.AFTER_ROLLBACK

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleUnAssignTaskTxEvent(UnAssignTaskTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    // TransactionPhase.AFTER_COMPLETION

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void handleResourceCloseTxEvent(ResourceCloseTxEvent event) {
        eventPublisher.publishEvent(event.to());
        event.clean();
    }

}
