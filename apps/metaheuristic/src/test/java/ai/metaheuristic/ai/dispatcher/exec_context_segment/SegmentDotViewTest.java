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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    // ---- Phase 12: verifyGraph on segments = SegmentAlgebra.structureError

    private static List<SegmentData.Line> linesOf(SegmentFixtureShapes.DotShape shape) {
        return SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot()));
    }

    private static void assertValid(SegmentFixtureShapes.DotShape shape) {
        assertNull(SegmentAlgebra.structureError(linesOf(shape)), shape.id() + ": the lines of a real shape are a valid structure");
    }

    @Test public void test_structureValid_S1() { assertValid(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_structureValid_S2() { assertValid(SegmentFixtureShapes.DOT_S2); }
    @Test public void test_structureValid_S3() { assertValid(SegmentFixtureShapes.DOT_S3); }
    @Test public void test_structureValid_S4() { assertValid(SegmentFixtureShapes.DOT_S4); }
    @Test public void test_structureValid_S5() { assertValid(SegmentFixtureShapes.DOT_S5); }
    @Test public void test_structureValid_S6() { assertValid(SegmentFixtureShapes.DOT_S6); }
    @Test public void test_structureValid_S7() { assertValid(SegmentFixtureShapes.DOT_S7); }
    @Test public void test_structureValid_S8() { assertValid(SegmentFixtureShapes.DOT_S8); }

    @Test
    public void test_structureValid_noLines() {
        assertNull(SegmentAlgebra.structureError(List.of()), "no lines is valid: today's check accepts an empty graph");
    }

    /** S1's lines plus {@code extra}; the error must carry {@code code}. */
    private static void assertRejected(String code, List<SegmentData.Line> extra) {
        final List<SegmentData.Line> lines = new ArrayList<>(linesOf(SegmentFixtureShapes.DOT_S1));
        lines.addAll(extra);
        final String error = SegmentAlgebra.structureError(lines);
        assertNotNull(error, "must be rejected with " + code);
        assertTrue(error.startsWith(code), "expected " + code + ", observed: " + error);
    }

    private static SegmentData.Line rootOfS1() {
        return linesOf(SegmentFixtureShapes.DOT_S1).getFirst();
    }

    private static SegmentData.Line line(String ctx, Long fork, long... taskIds) {
        final List<SegmentData.Vertex> tasks = new ArrayList<>();
        for (long id : taskIds) {
            tasks.add(new SegmentData.Vertex(id, null));
        }
        return new SegmentData.Line(ctx, fork, tasks);
    }

    @Test
    public void test_structureRejected_twoLinesShareCtx() {
        final SegmentData.Line nonRoot = linesOf(SegmentFixtureShapes.DOT_S1).get(1);
        assertRejected("01.906.015", List.of(line(nonRoot.ctx(), nonRoot.forkTaskId(), 900_001L)));
    }

    @Test
    public void test_structureRejected_secondRootLine() {
        assertRejected("01.906.150", List.of(line("77", null, 900_002L)));
    }

    @Test
    public void test_structureRejected_forkIsInNoLine() {
        assertRejected("01.906.160", List.of(line("1,91#1", 424_242L, 900_003L)));
    }

    @Test
    public void test_structureRejected_forkChainCycle() {
        // two lines, each forked from the other's Task: neither reaches the root line
        assertRejected("01.906.170", List.of(line("1,92#1", 900_005L, 900_004L), line("1,93#1", 900_004L, 900_005L)));
    }

    @Test
    public void test_structureRejected_forkIsTheRootTail_noJoin() {
        assertRejected("01.906.100", List.of(line("1,94#1", rootOfS1().tail().taskId(), 900_006L)));
    }

    // ---- Phase 12: getAllTasksTopologically on segments = SegmentAlgebra.topologicalOrder

    private static void assertTopological(SegmentFixtureShapes.DotShape shape) {
        final SegmentData.Graph g = SegmentAlgebra.toGraph(linesOf(shape));
        final List<Long> order = SegmentAlgebra.topologicalOrder(g);
        assertEquals(g.nodes().keySet(), new LinkedHashSet<>(order).stream().collect(Collectors.toSet()),
                shape.id() + ": every Task exactly once");
        assertEquals(g.nodes().size(), order.size(), shape.id() + ": no Task twice");
        final Map<Long, Integer> pos = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            pos.put(order.get(i), i);
        }
        for (SegmentData.Edge e : g.edges()) {
            assertTrue(pos.get(e.from()) < pos.get(e.to()), shape.id() + ": edge " + e + " must point forward in the order");
        }
        assertEquals(order, SegmentAlgebra.topologicalOrder(g), shape.id() + ": the order is deterministic");
    }

    @Test public void test_topological_S1() { assertTopological(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_topological_S2() { assertTopological(SegmentFixtureShapes.DOT_S2); }
    @Test public void test_topological_S6() { assertTopological(SegmentFixtureShapes.DOT_S6); }
    @Test public void test_topological_S7() { assertTopological(SegmentFixtureShapes.DOT_S7); }

    @Test
    public void test_topological_smallestReadyIdFirst() {
        final Map<Long, SegmentData.Node> nodes = new HashMap<>();
        for (long id : new long[]{5, 2, 9, 1}) {
            nodes.put(id, new SegmentData.Node(id, "1", null));
        }
        // 5 -> 1, 2 -> 1, 9 unconnected: ready {2, 5, 9} -> 2, 5 (releases 1), then 1 < 9
        final Set<SegmentData.Edge> edges = Set.of(new SegmentData.Edge(5, 1), new SegmentData.Edge(2, 1));
        assertEquals(List.of(2L, 5L, 1L, 9L), SegmentAlgebra.topologicalOrder(new SegmentData.Graph(nodes, edges)));
    }

    @Test
    public void test_topological_cycleRejected() {
        final Map<Long, SegmentData.Node> nodes = new HashMap<>();
        for (long id : new long[]{1, 2, 3}) {
            nodes.put(id, new SegmentData.Node(id, "1", null));
        }
        final Set<SegmentData.Edge> edges = Set.of(new SegmentData.Edge(1, 2), new SegmentData.Edge(2, 3), new SegmentData.Edge(3, 2));
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> SegmentAlgebra.topologicalOrder(new SegmentData.Graph(nodes, edges)));
        assertTrue(e.getMessage().startsWith("01.906.180"), e.getMessage());
    }
}
