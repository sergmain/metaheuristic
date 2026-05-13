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

import java.util.Map;

/**
 * SPI for callers outside MH that want to register their own {@link SignalKind}
 * with the {@link SignalKindRegistry}. Mirrors the bean-auto-collection
 * pattern used by {@link ai.metaheuristic.ai.shutdown.ShutdownInterface}:
 * every Spring bean implementing this interface is collected into a list and
 * merged into the registry at startup by {@link SignalBusConfig}.
 *
 * Contributors are merged on top of MH's core kinds defined in
 * {@link SignalKindRegistry#v1Default()}. Re-registering a core kind here
 * overrides MH's defaults — usually a mistake.
 *
 * MH itself does not implement this interface for its own kinds; core kinds
 * stay in {@code v1Default()}.
 */
public interface SignalKindContributor {

    Map<SignalKind, TopicBuilder> topicBuilders();

    Map<SignalKind, CoalescePolicy> coalescePolicies();
}
