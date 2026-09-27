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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.5, decision 13): the DOT is a view derived from segments. Built
 * from the segments of a shape, it imports as an acyclic graph, keeps {@code tag terminal} on the join, and passes the
 * structural check the whole-ExecContext {@code verifyGraph} applies - exactly one root vertex - restated here so the test
 * outlives that code.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentDotViewTest {

    /** Segments of the shape -> their lines -> DOT -> parsed back (the parse fails on a cycle). */
    private static SegmentData.Graph viewOf(SegmentFixtureShapes.DotShape shape) {
        final List<SegmentData.Segment> segments = SegmentAlgebra.segments(
                SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot())), shape.startsSegment());
        final List<SegmentData.Line> lines = segments.stream().flatMap(s -> s.lines().stream()).toList();
        return SegmentDotUtils.parse(SegmentDotUtils.toDot(SegmentAlgebra.toGraph(lines)));
    }

    private static void assertOneRoot(SegmentFixtureShapes.DotShape shape) {
        final SegmentData.Graph g = viewOf(shape);
        final Set<Long> targets = g.edges().stream().map(SegmentData.Edge::to).collect(Collectors.toSet());
        final List<Long> roots = g.nodes().keySet().stream().filter(id -> !targets.contains(id)).toList();
        assertEquals(1, roots.size(), shape.id() + ": the derived DOT must have exactly one root vertex, found " + roots);
        assertEquals(SegmentDotUtils.parse(shape.dot()).nodes().size(), g.nodes().size(), shape.id() + ": no vertex lost");
    }

    @Test public void test_oneRoot_S1() { assertOneRoot(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_oneRoot_S2() { assertOneRoot(SegmentFixtureShapes.DOT_S2); }
    @Test public void test_oneRoot_S3() { assertOneRoot(SegmentFixtureShapes.DOT_S3); }
    @Test public void test_oneRoot_S5() { assertOneRoot(SegmentFixtureShapes.DOT_S5); }
    @Test public void test_oneRoot_S6() { assertOneRoot(SegmentFixtureShapes.DOT_S6); }
    @Test public void test_oneRoot_S7() { assertOneRoot(SegmentFixtureShapes.DOT_S7); }

    @Test
    public void test_joinKeepsTerminalTag_S1() {
        final SegmentData.Node join = viewOf(SegmentFixtureShapes.DOT_S1).nodes().get(191554L);
        assertNotNull(join);
        assertEquals("terminal", join.tag(), "S1: the join keeps tag terminal in the derived DOT");
    }

    @Test
    public void test_joinKeepsTerminalTag_S2() {
        final SegmentData.Node join = viewOf(SegmentFixtureShapes.DOT_S2).nodes().get(191647L);
        assertNotNull(join);
        assertEquals("terminal", join.tag(), "S2: the join keeps tag terminal in the derived DOT");
    }

    @Test
    public void test_untaggedVerticesStayUntagged_S1() {
        final SegmentData.Graph g = viewOf(SegmentFixtureShapes.DOT_S1);
        final List<Long> tagged = g.nodes().values().stream().filter(n -> n.tag() != null).map(SegmentData.Node::taskId).toList();
        assertEquals(List.of(191554L), tagged, "S1: only the terminal join carries a tag");
    }
}
