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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * TTL sweeper for the SignalBus snapshot. In production, wired with the
 * configured global default TTL and invoked on a fixed schedule. The schedule
 * is configured at the Spring layer, not here, so this class stays
 * easy to test without Spring.
 *
 * createdAt resets on every overwrite, so a still-alive source keeps its
 * signal alive indefinitely — the TTL clock restarts on every publish.
 */
public class SignalBusSweeper {

    private final SignalBus bus;
    private final Duration ttl;
    private final Clock clock;

    public SignalBusSweeper(SignalBus bus, Duration ttl, Clock clock) {
        this.bus = bus;
        this.ttl = ttl;
        this.clock = clock;
    }

    public void sweep() {
        Instant cutoff = Instant.now(clock).minus(ttl);
        bus.evictOlderThan(cutoff);
    }
}
