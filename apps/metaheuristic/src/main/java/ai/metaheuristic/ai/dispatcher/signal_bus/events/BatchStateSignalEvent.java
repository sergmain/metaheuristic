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
 * Plain variant — consumed by SignalBusListener, the only thing that writes
 * to SignalBus. Published post-AFTER_COMMIT by SignalBusTxBridge.
 */
public record BatchStateSignalEvent(
    long batchId,
    int state,
    ScopeRef scope,
    Map<String, Object> info
) {}
