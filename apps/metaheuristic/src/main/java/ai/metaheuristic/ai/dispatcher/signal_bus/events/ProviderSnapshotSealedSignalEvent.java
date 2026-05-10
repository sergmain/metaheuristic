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

package ai.metaheuristic.ai.dispatcher.signal_bus.events;

import ai.metaheuristic.ai.dispatcher.signal_bus.ScopeRef;

import java.util.Map;

/**
 * Cross-Project Requirements (Stage 4) — emitted by the legal-side
 * snapshot-seal path after a provider RG project commits a STAGE
 * snapshot to COMMITTED. Consumer projects pull-discover via
 * {@code RgImportQueryService.findNewerProviderSnapshots}; this signal
 * is for any subscriber that wants live notification.
 * <p>
 * {@code signalId} is {@code String.valueOf(providerProjectId)} — one
 * authoritative entry per provider in the bus at any time. The
 * {@code providerSnapshotId} and {@code sealedAt} live in {@code info}.
 * Subscribers that need the full history of seals query the database via
 * {@code RgImportQueryService.findNewerProviderSnapshots} — the bus is for
 * change-notification, not durable history.
 * <p>
 * Per signal-bus-01-architecture §6.3, this event is non-transactional
 * (no {@code *TxEvent} pair). It is published from inside the snapshot
 * commit transaction and the listener invokes {@code SignalBus.put}
 * directly; the bus is in-memory and ephemeral so a partial publish on
 * TX rollback is acceptable — the listener still has only a single
 * authoritative entry per topic at any time.
 */
public record ProviderSnapshotSealedSignalEvent(
    String signalId,
    ScopeRef scope,
    Map<String, Object> info
) {}
