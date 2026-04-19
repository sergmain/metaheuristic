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

/**
 * Signal Bus — ephemeral live-change notification layer for the dispatcher.
 * Announces "something changed for (kind, signalId)" to listening UIs.
 * Not durable; not authoritative; on restart the snapshot is empty. Core data
 * remains in the database and is served by existing REST endpoints.
 * See docs/mh/signal-bus-01-architecture.md for the full design.
 */
@NullMarked
package ai.metaheuristic.ai.dispatcher.signal_bus;

import org.jspecify.annotations.NullMarked;
