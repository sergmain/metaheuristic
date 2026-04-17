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

/**
 * Optional interface — domain entities that emit signals can implement this
 * so producer sites have a single source of truth for (kind, signalId).
 * Non-entity producers (document export, system notices) can skip it and
 * synthesize a stable id directly at the publish site.
 */
public interface HasSignalId {
    SignalKind signalKind();
    String signalId();
}
