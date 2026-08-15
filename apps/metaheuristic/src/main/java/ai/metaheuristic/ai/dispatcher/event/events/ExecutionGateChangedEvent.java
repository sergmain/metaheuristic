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

package ai.metaheuristic.ai.dispatcher.event.events;

import ai.metaheuristic.ai.Enums;
import lombok.AllArgsConstructor;

/**
 * The post-commit half of {@link ExecutionGateChangedTxEvent}. Its listener is the ONLY thing that
 * mutates the in-memory admission state.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@AllArgsConstructor
public class ExecutionGateChangedEvent {
    public final Enums.GateScope scope;
    public final String refKey;
    public final long blockedUntil;
    public final String reasonCode;
    public final boolean removed;
}
