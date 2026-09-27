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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 4 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.5): for every Task of S1-S6, the descendants computed from lines
 * equal what today's whole-graph walk returned (the goldens).
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentDescendantsTest {

    private static void assertGolden(SegmentFixtureShapes.DotShape shape) {
        final SegmentStates.Adjacency adj = SegmentStates.Adjacency.of(SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot())));
        final SegmentGolden.Golden golden = SegmentGolden.load(shape.id());
        assertEquals(SegmentDotUtils.parse(shape.dot()).nodes().size(), golden.descendants().size(),
                shape.id() + ": the golden covers every Task");
        for (Map.Entry<Long, List<Long>> en : golden.descendants().entrySet()) {
            assertEquals(new TreeSet<>(en.getValue()), SegmentStates.descendants(adj, en.getKey()),
                    shape.id() + ": descendants of #" + en.getKey());
        }
    }

    @Test public void test_golden_S1() { assertGolden(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_golden_S2() { assertGolden(SegmentFixtureShapes.DOT_S2); }
    @Test public void test_golden_S3() { assertGolden(SegmentFixtureShapes.DOT_S3); }
    @Test public void test_golden_S4() { assertGolden(SegmentFixtureShapes.DOT_S4); }
    @Test public void test_golden_S5() { assertGolden(SegmentFixtureShapes.DOT_S5); }
    @Test public void test_golden_S6() { assertGolden(SegmentFixtureShapes.DOT_S6); }
}
