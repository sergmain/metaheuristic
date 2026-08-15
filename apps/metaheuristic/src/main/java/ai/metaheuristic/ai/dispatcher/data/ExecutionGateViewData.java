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
import ai.metaheuristic.ai.dispatcher.monitoring.GateMonitoring;
import ai.metaheuristic.api.EnumsApi;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the admin screen is sent. Deliberately flat scalars rather than the internal value objects:
 * a view model that shares types with the in-memory state ends up dragging that state's shape into
 * the UI, and every later change to one becomes a change to both.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
public class ExecutionGateViewData {

    /** One block in force. {@code remainingMills} is computed server-side so the UI needs no clock. */
    public record GateRecordView(EnumsApi.GateScope scope, String refKey, String reasonCode,
                                 long blockedUntil, long remainingMills) {}

    /** One exemplar, already truncated and copied — see the ring's own contract. */
    public record ExemplarView(long atMills, @Nullable Long taskId, @Nullable String functionCode,
                               @Nullable Long processorId, @Nullable String offendingValue) {}

    /**
     * One rejection reason at its current level.
     *
     * @param bucketsPresent how many minutes of the window it has been non-zero for. ❗ This, not
     *                       {@code count}, is what says "broken" rather than "busy" — a reason firing
     *                       across the whole window has stopped being transient whatever its volume.
     */
    public record ReasonLevelView(String reason, String rejectionClass, long count, int bucketsPresent,
                                  List<ExemplarView> exemplars) {}

    public record GateStatus(List<GateRecordView> records, List<ReasonLevelView> rejections) {}
}
