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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
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
@RequiredArgsConstructor
public class EventsBoundedToTx {

    private final ApplicationEventPublisher eventPublisher;
    private final DispatcherEventService dispatcherEventService;

    // TransactionPhase.AFTER_COMMIT

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSetTaskExecStateTxEvent(SetTaskExecStateTxEvent event) {
        log.debug("call EventsBoundedToTx.handleSetTaskExecStateTxEvent(execContextId:#{}, taskId:#{}, state:{})", event.execContextId, event.taskId, event.state);
        eventPublisher.publishEvent(event.to());
        if (event.state== EnumsApi.TaskExecState.OK) {
            dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_FINISHED, null, event.taskId, event.execContextId);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCheckTaskCanBeFinishedTxEvent(CheckTaskCanBeFinishedTxEvent event) {
        log.debug("call EventsBoundedToTx.handleCheckTaskCanBeFinishedTxEvent(execContextId:#{}, taskId:#{})", event.execContextId, event.taskId);
        eventPublisher.publishEvent(event.to());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVariableUploadedTxEvent(VariableUploadedTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskCreatedTxEvent(TaskCreatedTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUpdateTaskExecStatesInGraphTxEvent(UpdateTaskExecStatesInGraphTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLockByExecContextIdTxEvent(LockByExecContextIdTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStartTaskProcessingTxEvent(StartTaskProcessingTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSetVariableReceivedTxEvent(SetVariableReceivedTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    // TransactionPhase.AFTER_ROLLBACK

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleUnAssignTaskTxEvent(UnAssignTaskTxEvent event) {
        eventPublisher.publishEvent(event.to());
    }

    // TransactionPhase.AFTER_COMPLETION

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void handleResourceCloseTxEvent(ResourceCloseTxEvent event) {
        eventPublisher.publishEvent(event.to());
        event.clean();
    }


}
