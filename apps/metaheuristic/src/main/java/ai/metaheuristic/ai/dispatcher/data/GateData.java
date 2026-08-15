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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.ai.Enums;
import org.jspecify.annotations.Nullable;

/**
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
public class GateData {

    /**
     * The verdict on one candidate Task for one core.
     *
     * <p>{@code rejectedBy} is a per-Task reason: the caller skips this Task and keeps looking. It is
     * deliberately NOT the whole-search vocabulary — the two enums are split by LEVEL, not by topic,
     * and confusing them turns "skip this one" into "stop the search".
     */
    public record Admission(boolean admitted, Enums.@Nullable TaskRejectingStatus rejectedBy,
                           @Nullable String offendingValue) {

        public static final Admission ADMITTED = new Admission(true, null, null);

        public static Admission rejected(Enums.TaskRejectingStatus reason) {
            return new Admission(false, reason, null);
        }

        /**
         * @param offendingValue the thing a human would have to see to fix this — the env code no
         *                       Processor declares, the OS the Function demands. A count says a
         *                       Processor is refusing work; this says why, which is the difference
         *                       between noticing an outage and ending it.
         */
        public static Admission rejected(Enums.TaskRejectingStatus reason, @Nullable String offendingValue) {
            return new Admission(false, reason, offendingValue);
        }
    }

    /**
     * Which Processors have reported a given Function ready, and when that was last consulted.
     *
     * <p>Mutable, unlike {@link GateRecord}, and deliberately so: this is a REPORTED FACT rather than
     * a decision. It is rebuilt from the Processors whenever they reconnect and no table stands behind
     * it, so it is cheap to lose and must not be confused with something durable.
     */
    public static class FunctionReadiness {
        public long mills = System.currentTimeMillis();
        public final java.util.Set<Long> ids = new java.util.HashSet<>();

        public boolean contains(Long processorId) {
            return ids.contains(processorId);
        }
    }

    /**
     * Why a Processor is being refused work, and for how long.
     *
     * <p>{@code remainingMills} is 0 for a condition no deadline will fix — a version mismatch is not
     * something to wait out — which is a different thing from "expires imminently". Callers render the
     * reason either way and only show a countdown when there is one.
     */
    public record Blacklist(String reason, long remainingMills) {}

    /**
     * Identity of a blocked thing. A scope alone is not enough and a key alone is not either: one
     * Function code and one processor id can be the same string without being the same subject.
     */
    public record GateKey(EnumsApi.GateScope scope, String refKey) {}

    /**
     * The in-memory copy of one committed row. Immutable on purpose — it is read on the hot path
     * from many threads and replaced wholesale rather than mutated in place.
     */
    public record GateRecord(EnumsApi.GateScope scope, String refKey, long blockedUntil, String reasonCode) {

        public GateKey key() {
            return new GateKey(scope, refKey);
        }
    }
}
