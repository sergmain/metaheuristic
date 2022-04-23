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

import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 6/20/2021
 * Time: 5:03 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class EventPublisherService {

    private final ApplicationEventPublisher eventPublisher;

    public void publishSetTaskExecStateTxEvent(SetTaskExecStateTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishProcessDeletedExecContextTxEvent(ProcessDeletedExecContextTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishTaskQueueCleanByExecContextIdTxEvent(TaskQueueCleanByExecContextIdTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishCheckTaskCanBeFinishedTxEvent(CheckTaskCanBeFinishedTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishVariableUploadedTxEvent(VariableUploadedTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishTaskCreatedTxEvent(TaskCreatedTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishUpdateTaskExecStatesInGraphTxEvent(UpdateTaskExecStatesInGraphTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishStartTaskProcessingTxEvent(StartTaskProcessingTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishSetVariableReceivedTxEvent(SetVariableReceivedTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishUnAssignTaskTxEvent(UnAssignTaskTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishUnAssignTaskTxEventAfterCommit(UnAssignTaskTxAfterCommitEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishResourceCloseTxEvent(ResourceCloseTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishDeleteExecContextTxEvent(DeleteExecContextTxEvent event) {
        TxUtils.checkTxExists();
        eventPublisher.publishEvent(event);
    }

    public void publishDeleteExecContextInListTxEvent(DeleteExecContextInListTxEvent event) {
        TxUtils.checkTxExists();
        if (!event.execContextIds.isEmpty()) {
            eventPublisher.publishEvent(event);
        }
    }
}
