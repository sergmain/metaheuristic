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
import java.time.ZoneId;

/**
 * Test fixture: a Clock whose now() can be advanced explicitly. Used by
 * SignalBusTest (coalescing) and SignalBusSweeperTest (TTL).
 */
public class MutableClock extends Clock {

    private Instant now;

    public MutableClock(Instant initial) {
        this.now = initial;
    }

    public void advance(Duration d) {
        this.now = this.now.plus(d);
    }

    @Override public Instant instant()           { return now; }
    @Override public ZoneId  getZone()           { return ZoneId.of("UTC"); }
    @Override public Clock   withZone(ZoneId z)  { return this; }
}
