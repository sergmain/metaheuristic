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
import ai.metaheuristic.ai.dispatcher.signal_bus.events.DocumentExportSignalEvent;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.ExecContextStateSignalEvent;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.SystemNoticeSignalEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Sergio Lissner
 * Plan 02 — Event infrastructure, Step 1.
 * Unit test, no Spring. SignalBusListener must delegate to SignalBus.put
 * with the correct kind, signalId, scope, info, and terminal flag derived
 * from the BatchExecState code.
 */
class SignalBusListenerTest {

    @Test
    void onBatchState_terminalState_passesTrue() {
        // arrange — state 4 = Finished → terminal
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new BatchStateSignalEvent(42L, 4, new ScopeRef(100L),
            Map.of("state", 4, "stateName", "Finished", "companyId", 100L));

        // act
        listener.onBatchState(event);

        // assert
        verify(bus).put(eq(SignalKind.BATCH), eq("42"), eq(new ScopeRef(100L)),
            argThat(m -> ((Integer) m.get("state")) == 4), eq(true));
    }

    @Test
    void onBatchState_nonTerminalState_passesFalse() {
        // arrange — state 3 = Processing → not terminal
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new BatchStateSignalEvent(42L, 3, new ScopeRef(100L),
            Map.of("state", 3, "stateName", "Processing", "companyId", 100L));

        // act
        listener.onBatchState(event);

        // assert
        verify(bus).put(eq(SignalKind.BATCH), eq("42"), eq(new ScopeRef(100L)),
            argThat(m -> ((Integer) m.get("state")) == 3), eq(false));
    }

    @Test
    void onBatchState_errorState_isTerminal() {
        // arrange — state -1 = Error → terminal
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new BatchStateSignalEvent(42L, -1, new ScopeRef(100L),
            Map.of("state", -1, "stateName", "Error", "companyId", 100L));

        // act
        listener.onBatchState(event);

        // assert
        verify(bus).put(eq(SignalKind.BATCH), eq("42"), eq(new ScopeRef(100L)),
            argThat(m -> ((Integer) m.get("state")) == -1), eq(true));
    }

    @Test
    void onBatchState_archivedState_isTerminal() {
        // arrange — state 5 = Archived → terminal
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new BatchStateSignalEvent(42L, 5, new ScopeRef(100L),
            Map.of("state", 5, "stateName", "Archived", "companyId", 100L));

        // act
        listener.onBatchState(event);

        // assert
        verify(bus).put(eq(SignalKind.BATCH), eq("42"), eq(new ScopeRef(100L)),
            argThat(m -> ((Integer) m.get("state")) == 5), eq(true));
    }

    // --- EXEC_CONTEXT ---

    @Test
    void onExecContextState_finishedState_isTerminal() {
        // arrange — state 5 = FINISHED → terminal
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new ExecContextStateSignalEvent(100L, 5, new ScopeRef(100L),
            Map.of("state", 5, "stateName", "FINISHED",
                   "infoBank", "DRONE", "sourceCodeUid", "mhdg-rg-flat-1.0.0"));

        // act
        listener.onExecContextState(event);

        // assert
        verify(bus).put(eq(SignalKind.EXEC_CONTEXT), eq("100"), eq(new ScopeRef(100L)),
            argThat(m -> "FINISHED".equals(m.get("stateName"))), eq(true));
    }

    @Test
    void onExecContextState_startedState_isNotTerminal() {
        // arrange — state 3 = STARTED → not terminal
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new ExecContextStateSignalEvent(100L, 3, new ScopeRef(100L),
            Map.of("state", 3, "stateName", "STARTED"));

        // act
        listener.onExecContextState(event);

        // assert
        verify(bus).put(eq(SignalKind.EXEC_CONTEXT), eq("100"), eq(new ScopeRef(100L)),
            argThat(m -> "STARTED".equals(m.get("stateName"))), eq(false));
    }

    @Test
    void onExecContextState_errorState_isTerminal() {
        // arrange — state -2 = ERROR → terminal
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new ExecContextStateSignalEvent(100L, -2, new ScopeRef(100L),
            Map.of("state", -2, "stateName", "ERROR"));

        // act
        listener.onExecContextState(event);

        // assert
        verify(bus).put(eq(SignalKind.EXEC_CONTEXT), eq("100"), eq(new ScopeRef(100L)),
            argThat(m -> "ERROR".equals(m.get("stateName"))), eq(true));
    }

    @Test
    void onExecContextState_stoppedState_isTerminal() {
        // arrange — state 4 = STOPPED → terminal
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new ExecContextStateSignalEvent(100L, 4, new ScopeRef(100L),
            Map.of("state", 4, "stateName", "STOPPED"));

        // act
        listener.onExecContextState(event);

        // assert
        verify(bus).put(eq(SignalKind.EXEC_CONTEXT), eq("100"), eq(new ScopeRef(100L)),
            argThat(m -> "STOPPED".equals(m.get("stateName"))), eq(true));
    }

    @Test
    void onExecContextState_doesntExistState_isTerminal() {
        // arrange — state 6 = DOESNT_EXIST → terminal
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new ExecContextStateSignalEvent(100L, 6, new ScopeRef(100L),
            Map.of("state", 6, "stateName", "DOESNT_EXIST"));

        // act
        listener.onExecContextState(event);

        // assert
        verify(bus).put(eq(SignalKind.EXEC_CONTEXT), eq("100"), eq(new ScopeRef(100L)),
            argThat(m -> "DOESNT_EXIST".equals(m.get("stateName"))), eq(true));
    }

    // --- DOCUMENT_EXPORT ---

    @Test
    void onDocumentExport_finishedPhase_isTerminal() {
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new DocumentExportSignalEvent("export:42:xyz", new ScopeRef(100L),
            Map.of("phase", "finished", "projectId", 42L, "percent", 100.0));

        // act
        listener.onDocumentExport(event);

        // assert
        verify(bus).put(eq(SignalKind.DOCUMENT_EXPORT), eq("export:42:xyz"),
            eq(new ScopeRef(100L)),
            argThat(m -> "finished".equals(m.get("phase"))), eq(true));
    }

    @Test
    void onDocumentExport_failedPhase_isTerminal() {
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new DocumentExportSignalEvent("export:42:xyz", new ScopeRef(100L),
            Map.of("phase", "failed", "projectId", 42L, "message", "boom"));

        // act
        listener.onDocumentExport(event);

        // assert
        verify(bus).put(eq(SignalKind.DOCUMENT_EXPORT), eq("export:42:xyz"),
            eq(new ScopeRef(100L)),
            argThat(m -> "failed".equals(m.get("phase"))), eq(true));
    }

    @Test
    void onDocumentExport_renderingPhase_isNotTerminal() {
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new DocumentExportSignalEvent("export:42:xyz", new ScopeRef(100L),
            Map.of("phase", "rendering", "projectId", 42L,
                   "completed", 10, "total", 100, "percent", 10.0));

        // act
        listener.onDocumentExport(event);

        // assert
        verify(bus).put(eq(SignalKind.DOCUMENT_EXPORT), eq("export:42:xyz"),
            eq(new ScopeRef(100L)),
            argThat(m -> "rendering".equals(m.get("phase"))), eq(false));
    }

    // --- SYSTEM_NOTICE ---

    @Test
    void onSystemNotice_isNeverTerminal() {
        SignalBus bus = mock(SignalBus.class);
        SignalBusListener listener = new SignalBusListener(bus);
        var event = new SystemNoticeSignalEvent("notice-uuid-1", new ScopeRef(100L),
            Map.of("severity", "INFO", "message", "something happened"));

        // act
        listener.onSystemNotice(event);

        // assert
        verify(bus).put(eq(SignalKind.SYSTEM_NOTICE), eq("notice-uuid-1"),
            eq(new ScopeRef(100L)),
            argThat(m -> "INFO".equals(m.get("severity"))), eq(false));
    }
}
