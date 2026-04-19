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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sergio Lissner
 * Plan 01 — Foundation, Step 9. TTL sweep.
 */
class SignalBusSweeperTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static SignalBus newBatchBus(MutableClock clock) {
        TopicBuilder batchTopic = (k, id, info) -> "batch." + id + ".state";
        SignalKindRegistry registry = new SignalKindRegistry(
            Map.of(SignalKind.BATCH, batchTopic),
            Map.of(SignalKind.BATCH, CoalescePolicy.NONE));
        return new SignalBus(registry, clock);
    }

    @Test
    void sweep_removesEntriesOlderThanTtl() {
        // arrange
        MutableClock clock = new MutableClock(T0);
        SignalBus bus = newBatchBus(clock);
        ScopeRef scope = new ScopeRef(100L);

        bus.put(SignalKind.BATCH, "42", scope, Map.of("state", 4), true);
        clock.advance(Duration.ofHours(25));

        SignalBusSweeper sweeper = new SignalBusSweeper(bus, Duration.ofHours(24), clock);

        // act
        sweeper.sweep();

        // assert
        assertThat(bus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of()).signals())
            .isEmpty();
    }

    @Test
    void sweep_retainsEntriesWithinTtl() {
        // arrange
        MutableClock clock = new MutableClock(T0);
        SignalBus bus = newBatchBus(clock);
        ScopeRef scope = new ScopeRef(100L);

        bus.put(SignalKind.BATCH, "42", scope, Map.of("state", 4), true);
        clock.advance(Duration.ofHours(23));

        SignalBusSweeper sweeper = new SignalBusSweeper(bus, Duration.ofHours(24), clock);

        // act
        sweeper.sweep();

        // assert
        assertThat(bus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of()).signals())
            .hasSize(1);
    }

    @Test
    void put_overwriteResetsCreatedAt() {
        // arrange
        MutableClock clock = new MutableClock(T0);
        SignalBus bus = newBatchBus(clock);
        ScopeRef scope = new ScopeRef(100L);

        // First put at T0
        bus.put(SignalKind.BATCH, "42", scope, Map.of("state", 2), false);

        // Advance 23h, second put for the same signalId — createdAt resets
        clock.advance(Duration.ofHours(23));
        bus.put(SignalKind.BATCH, "42", scope, Map.of("state", 4), true);

        // Advance another 23h (total 46h from T0, but only 23h from the second put)
        clock.advance(Duration.ofHours(23));

        SignalBusSweeper sweeper = new SignalBusSweeper(bus, Duration.ofHours(24), clock);

        // act
        sweeper.sweep();

        // assert: still alive because second put reset createdAt
        assertThat(bus.query(scope, 0L, Set.of(SignalKind.BATCH), List.of()).signals())
            .hasSize(1);
    }
}
