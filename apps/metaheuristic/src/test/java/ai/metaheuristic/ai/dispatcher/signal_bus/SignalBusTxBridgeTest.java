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

import ai.metaheuristic.ai.dispatcher.signal_bus.events.BatchStateSignalEvent;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.BatchStateSignalTxEvent;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.ExecContextStateSignalEvent;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.ExecContextStateSignalTxEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Sergio Lissner
 * Plan 02 — Event infrastructure, Step 4.
 * Unit test, no Spring. Each Tx bridge method must re-publish the plain event
 * via e.to() with identical field values.
 */
class SignalBusTxBridgeTest {

    @Test
    void onBatchStateTx_republishesAsPlainEvent() {
        // arrange
        ApplicationEventPublisher pub = mock(ApplicationEventPublisher.class);
        SignalBusTxBridge bridge = new SignalBusTxBridge(pub);
        var tx = new BatchStateSignalTxEvent(42L, 4, new ScopeRef(100L),
            Map.of("state", 4));

        // act
        bridge.onBatchStateTx(tx);

        // assert
        ArgumentCaptor<BatchStateSignalEvent> cap =
            ArgumentCaptor.forClass(BatchStateSignalEvent.class);
        verify(pub).publishEvent(cap.capture());
        BatchStateSignalEvent out = cap.getValue();
        assertThat(out.batchId()).isEqualTo(42L);
        assertThat(out.state()).isEqualTo(4);
        assertThat(out.scope()).isEqualTo(new ScopeRef(100L));
        assertThat(out.info()).isEqualTo(tx.info());
    }

    @Test
    void onExecContextStateTx_republishesAsPlainEvent() {
        // arrange
        ApplicationEventPublisher pub = mock(ApplicationEventPublisher.class);
        SignalBusTxBridge bridge = new SignalBusTxBridge(pub);
        var tx = new ExecContextStateSignalTxEvent(100L, 5, new ScopeRef(100L),
            Map.of("state", 5, "stateName", "FINISHED",
                   "infoBank", "DRONE", "sourceCodeUid", "mhdg-rg-flat-1.0.0"));

        // act
        bridge.onExecContextStateTx(tx);

        // assert
        ArgumentCaptor<ExecContextStateSignalEvent> cap =
            ArgumentCaptor.forClass(ExecContextStateSignalEvent.class);
        verify(pub).publishEvent(cap.capture());
        ExecContextStateSignalEvent out = cap.getValue();
        assertThat(out.execContextId()).isEqualTo(100L);
        assertThat(out.state()).isEqualTo(5);
        assertThat(out.scope()).isEqualTo(new ScopeRef(100L));
        assertThat(out.info()).isEqualTo(tx.info());
    }
}
