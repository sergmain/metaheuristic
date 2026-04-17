/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.signal_bus;

import ai.metaheuristic.ai.dispatcher.signal_bus.events.BatchStateSignalTxEvent;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.ExecContextStateSignalTxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Transactional bridge for Signal Bus events. Each method is a one-liner:
 * after commit, re-publish the plain variant via {@code e.to()}.
 * Rolled-back transactions never reach AFTER_COMMIT so the bus never sees
 * the signal — zero conditional logic required.
 * See docs/mh/signal-bus-01-architecture.md §6.2.
 */
@Component
@Profile("dispatcher")
@RequiredArgsConstructor
public class SignalBusTxBridge {

    private final ApplicationEventPublisher applicationEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBatchStateTx(BatchStateSignalTxEvent e) {
        applicationEventPublisher.publishEvent(e.to());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExecContextStateTx(ExecContextStateSignalTxEvent e) {
        applicationEventPublisher.publishEvent(e.to());
    }
}
