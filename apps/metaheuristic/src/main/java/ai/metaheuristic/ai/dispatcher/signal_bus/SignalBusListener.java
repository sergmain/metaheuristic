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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.BatchStateSignalEvent;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.DocumentExportSignalEvent;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.ExecContextStateSignalEvent;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.ProviderSnapshotSealedSignalEvent;
import ai.metaheuristic.ai.dispatcher.signal_bus.events.SystemNoticeSignalEvent;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Sole caller of {@link SignalBus#put}. Each method is a one-liner: the event
 * carries the info map already shaped by the producer, and the listener only
 * adds the derived "terminal" flag per kind.
 * See docs/mh/signal-bus-01-architecture.md §6.3.
 */
@Component
@Profile("dispatcher")
@RequiredArgsConstructor
public class SignalBusListener {

    private final SignalBus signalBus;

    @EventListener
    public void onBatchState(BatchStateSignalEvent e) {
        signalBus.put(SignalKind.BATCH, String.valueOf(e.batchId()), e.scope(),
            e.info(), isBatchTerminal(e.state()));
    }

    @EventListener
    public void onExecContextState(ExecContextStateSignalEvent e) {
        signalBus.put(SignalKind.EXEC_CONTEXT, String.valueOf(e.execContextId()), e.scope(),
            e.info(), isExecContextTerminal(e.state()));
    }

    @EventListener
    public void onDocumentExport(DocumentExportSignalEvent e) {
        signalBus.put(SignalKind.DOCUMENT_EXPORT, e.signalId(), e.scope(),
            e.info(), isExportTerminal(e.info()));
    }

    @EventListener
    public void onSystemNotice(SystemNoticeSignalEvent e) {
        // SYSTEM_NOTICE is never terminal — TTL handles eviction.
        signalBus.put(SignalKind.SYSTEM_NOTICE, e.signalId(), e.scope(), e.info(), false);
    }

    /**
     * Cross-Project Requirements (Stage 4) — emit
     * {@code provider.snapshot.<providerProjectId>.sealed}. The signal id
     * carries provider project + snapshot ids so subscribers see one
     * entry per seal event. Always non-terminal — sealing is a discrete
     * fact, but the topic is reused across snapshots, so subsequent
     * seals replace earlier ones in the bus per coalesce policy.
     */
    @EventListener
    public void onProviderSnapshotSealed(ProviderSnapshotSealedSignalEvent e) {
        signalBus.put(SignalKind.PROVIDER_SNAPSHOT_SEALED, e.signalId(), e.scope(),
            e.info(), false);
    }

    private static boolean isBatchTerminal(int state) {
        return state == Enums.BatchExecState.Error.code
            || state == Enums.BatchExecState.Finished.code
            || state == Enums.BatchExecState.Archived.code;
    }

    private static boolean isExecContextTerminal(int state) {
        return state == EnumsApi.ExecContextState.FINISHED.code
            || state == EnumsApi.ExecContextState.ERROR.code
            || state == EnumsApi.ExecContextState.STOPPED.code
            || state == EnumsApi.ExecContextState.DOESNT_EXIST.code;
    }

    private static boolean isExportTerminal(Map<String, Object> info) {
        Object phase = info.get("phase");
        return "finished".equals(phase) || "failed".equals(phase);
    }
}
