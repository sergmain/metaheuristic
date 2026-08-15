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
    public record Admission(boolean admitted, Enums.@Nullable TaskRejectingStatus rejectedBy) {

        public static final Admission ADMITTED = new Admission(true, null);

        public static Admission rejected(Enums.TaskRejectingStatus reason) {
            return new Admission(false, reason);
        }
    }

    /**
     * Identity of a blocked thing. A scope alone is not enough and a key alone is not either: one
     * Function code and one processor id can be the same string without being the same subject.
     */
    public record GateKey(Enums.GateScope scope, String refKey) {}

    /**
     * The in-memory copy of one committed row. Immutable on purpose — it is read on the hot path
     * from many threads and replaced wholesale rather than mutated in place.
     */
    public record GateRecord(Enums.GateScope scope, String refKey, long blockedUntil, String reasonCode) {

        public GateKey key() {
            return new GateKey(scope, refKey);
        }
    }
}
