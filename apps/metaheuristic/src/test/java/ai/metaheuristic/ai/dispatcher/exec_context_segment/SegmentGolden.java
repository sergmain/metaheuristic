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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.JsonUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * The goldens of 041-EXEC-CONTEXT-SEGMENTS-PLAN, section 8.2: what today's whole-ExecContext DOT implementation answers
 * for each DOT shape - derived joins, descendants, ready sets and SKIPPED closures over seeded reachable states. Written
 * once by {@link SegmentGoldenGenerator} (removed in Phase 21) under {@code src/test/resources/segment/golden/}; read by
 * the segment tests, which therefore still compile and run after the DOT code is gone.
 *
 * <p>State maps carry only Tasks whose state is not NONE; a Task absent from a map is NONE.
 */
public final class SegmentGolden {

    private SegmentGolden() {
    }

    /** One seeded reachable state and the Tasks today's scheduler would hand out in it. */
    public record ReadyCase(int run, int step, Map<Long, EnumsApi.TaskExecState> states, List<Long> ready) {
    }

    /** One failure: the states with the failed Task already ERROR, and the states after today's SKIPPED propagation. */
    public record SkipCase(int run, int step, long seed, Map<Long, EnumsApi.TaskExecState> before,
                           Map<Long, EnumsApi.TaskExecState> after) {
    }

    /**
     * @param derivedJoins join Task of every non-root line, by line ctx, read off today's graph
     * @param descendants  every Task's descendants, sorted (empty map for shapes the plan excludes)
     */
    public record Golden(String shape, Map<String, Long> derivedJoins, Map<Long, List<Long>> descendants,
                         List<ReadyCase> readiness, List<SkipCase> skips) {
    }

    public static String toJson(Golden golden) {
        return JsonUtils.getMapper().writeValueAsString(golden);
    }

    public static Golden fromJson(String json) {
        return JsonUtils.getMapper().readValue(json, Golden.class);
    }

    @SneakyThrows
    public static Golden load(String shapeId) {
        return fromJson(IOUtils.resourceToString("/segment/golden/" + shapeId + ".json", StandardCharsets.UTF_8));
    }
}
