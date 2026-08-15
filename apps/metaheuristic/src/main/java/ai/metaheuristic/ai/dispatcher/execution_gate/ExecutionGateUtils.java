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

package ai.metaheuristic.ai.dispatcher.execution_gate;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.data.GateData;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The decision arithmetic behind the admission gate, as pure functions over a map that is passed in.
 *
 * <p>Kept static and Spring-less so the rules that matter — when a block is still live, what deadline
 * results from blocking a key that is already blocked — can be tested without a context. The owning
 * bean holds the map; nothing here holds state.
 *
 * <p>Error code prefix: {@code 01.322.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Slf4j
public class ExecutionGateUtils {

    /**
     * A block is live while now has not reached its deadline. The boundary is deliberately exclusive:
     * at exactly {@code blockedUntil} the block is over, which makes a zero-length block a no-op
     * rather than something that lingers for one millisecond.
     */
    public static boolean isLive(long blockedUntil, long now) {
        return blockedUntil > now;
    }

    /**
     * The deadline that results from blocking a key. The longer of the two always wins, so a short
     * block can never shorten a long one that is already in force — an analyzer declaring 30 seconds
     * must not release a key an operator blocked for a day.
     */
    public static long resolveDeadline(GateData.@Nullable GateRecord existing, long requested, long now) {
        if (existing == null || !isLive(existing.blockedUntil(), now)) {
            return requested;
        }
        return Math.max(existing.blockedUntil(), requested);
    }

    /**
     * The deadline covering a key right now, or null when nothing does. An entry whose deadline has
     * passed is treated as absent; it is not removed here, because a read must not mutate.
     */
    @Nullable
    public static Long blockedUntil(Map<GateData.GateKey, GateData.GateRecord> records, Enums.GateScope scope, String refKey, long now) {
        final GateData.GateRecord record = records.get(new GateData.GateKey(scope, refKey));
        if (record == null || !isLive(record.blockedUntil(), now)) {
            return null;
        }
        return record.blockedUntil();
    }

    /**
     * Records a block in memory, extending an existing live one rather than adding a second.
     * Returns the record now in force.
     */
    public static GateData.GateRecord putOrExtend(
            Map<GateData.GateKey, GateData.GateRecord> records,
            Enums.GateScope scope, String refKey, long blockedUntil, String reasonCode, long now) {

        final GateData.GateKey key = new GateData.GateKey(scope, refKey);
        final long deadline = resolveDeadline(records.get(key), blockedUntil, now);
        final GateData.GateRecord record = new GateData.GateRecord(scope, refKey, deadline, reasonCode);
        records.put(key, record);
        return record;
    }

    /**
     * Drops every entry whose deadline has passed. Returns how many went.
     */
    public static int dropExpired(Map<GateData.GateKey, GateData.GateRecord> records, long now) {
        final int before = records.size();
        records.values().removeIf(r -> !isLive(r.blockedUntil(), now));
        return before - records.size();
    }
}
