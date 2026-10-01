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
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.5): the structural algebra - a graph decomposes into lines and
 * recomposes into the same graph, lines group into segments as the shape's SourceCode dictates, and a graph that is not
 * line-based is rejected with a named error code.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentAlgebraTest {

    private static void assertRoundTrip(SegmentFixtureShapes.DotShape shape) {
        final SegmentData.Graph graph = SegmentDotUtils.parse(shape.dot());
        final List<SegmentData.Line> lines = SegmentAlgebra.decompose(graph);
        final SegmentData.Graph back = SegmentAlgebra.toGraph(lines);
        assertEquals(graph.nodes(), back.nodes(), shape.id() + ": vertices, ctx and tags must survive DOT -> lines -> graph");
        assertEquals(graph.edges(), back.edges(), shape.id() + ": the edges lines imply must be exactly the DOT's edges");

        final SegmentData.Graph reparsed = SegmentDotUtils.parse(SegmentDotUtils.toDot(back));
        assertEquals(graph.nodes(), reparsed.nodes(), shape.id() + ": vertices must survive lines -> DOT");
        assertEquals(graph.edges(), reparsed.edges(), shape.id() + ": edges must survive lines -> DOT");
    }

    @Test public void test_roundTrip_S1() { assertRoundTrip(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_roundTrip_S2() { assertRoundTrip(SegmentFixtureShapes.DOT_S2); }
    @Test public void test_roundTrip_S3_idsOutOfChainOrder() { assertRoundTrip(SegmentFixtureShapes.DOT_S3); }
    @Test public void test_roundTrip_S4() { assertRoundTrip(SegmentFixtureShapes.DOT_S4); }
    @Test public void test_roundTrip_S5() { assertRoundTrip(SegmentFixtureShapes.DOT_S5); }
    @Test public void test_roundTrip_S6() { assertRoundTrip(SegmentFixtureShapes.DOT_S6); }
    @Test public void test_roundTrip_S7() { assertRoundTrip(SegmentFixtureShapes.DOT_S7); }
    @Test public void test_roundTrip_S8() { assertRoundTrip(SegmentFixtureShapes.DOT_S8); }

    private static void assertCounts(SegmentFixtureShapes.DotShape shape, int expectedLines, int expectedSegments) {
        final List<SegmentData.Line> lines = SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot()));
        assertEquals(expectedLines, lines.size(), shape.id() + ": number of lines");
        final List<SegmentData.Segment> segments = SegmentAlgebra.segments(lines, shape.startsSegment());
        assertEquals(expectedSegments, segments.size(), shape.id() + ": number of segments");
        assertEquals("1", segments.getFirst().lineCtxId(), shape.id() + ": the root segment comes first");
        assertNull(segments.getFirst().forkTaskId(), shape.id() + ": the root segment has no fork");
        final Set<String> all = segments.stream().flatMap(s -> s.lines().stream()).map(SegmentData.Line::ctx).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(lines.size(), all.size(), shape.id() + ": every line belongs to exactly one segment");
    }

    @Test public void test_counts_S1() { assertCounts(SegmentFixtureShapes.DOT_S1, 11, 2); }
    @Test public void test_counts_S2() { assertCounts(SegmentFixtureShapes.DOT_S2, 22, 4); }
    @Test public void test_counts_S3() { assertCounts(SegmentFixtureShapes.DOT_S3, 26, 8); }
    @Test public void test_counts_S4() { assertCounts(SegmentFixtureShapes.DOT_S4, 1, 1); }
    @Test public void test_counts_S5() { assertCounts(SegmentFixtureShapes.DOT_S5, 3, 2); }
    @Test public void test_counts_S6() { assertCounts(SegmentFixtureShapes.DOT_S6, 6, 6); }
    @Test public void test_counts_S7() { assertCounts(SegmentFixtureShapes.DOT_S7, 1001, 1001); }

    @Test
    public void test_S1_segmentCtxSets() {
        final List<SegmentData.Segment> segments = SegmentAlgebra.segments(
                SegmentAlgebra.decompose(SegmentDotUtils.parse(SegmentFixtureShapes.DOT_S1.dot())),
                SegmentFixtureShapes.DOT_S1.startsSegment());
        assertEquals(List.of("1", "1,2#0", "1,4#1", "1,4,5|1#0"),
                segments.get(0).lines().stream().map(SegmentData.Line::ctx).toList(),
                "S1: the root segment holds the top chain and the static blocks outside any splitter line");
        assertEquals("1,6#1", segments.get(1).lineCtxId());
        assertEquals(191553L, segments.get(1).forkTaskId());
        assertEquals(List.of("1,6#1", "1,6,7,10,11,12|1|0|0|0#0", "1,6,7,10,11|1|0|0#0", "1,6,7,10|1|0#0",
                        "1,6,7,15|1|0#1", "1,6,7,8|1|0#0", "1,6,7|1#0"),
                segments.get(1).lines().stream().map(SegmentData.Line::ctx).toList(),
                "S1: the splitter line carries its whole nested subtree");
    }

    @Test
    public void test_S4_producerWithZeroLines_keepsForkToContinuation() {
        final List<SegmentData.Line> lines = SegmentAlgebra.decompose(SegmentDotUtils.parse(SegmentFixtureShapes.DOT_S4.dot()));
        assertEquals(1, lines.size(), "S4: no child line");
        assertEquals(List.of(1L, 2L, 3L, 4L), lines.getFirst().tasks().stream().map(SegmentData.Vertex::taskId).toList());
        assertTrue(SegmentAlgebra.toGraph(lines).edges().contains(new SegmentData.Edge(2L, 3L)),
                "S4: the producer keeps its fork -> continuation edge");
    }

    @Test
    public void test_S8_twoLineBasesUnderOneFork_independentSegments() {
        final List<SegmentData.Segment> segments = SegmentAlgebra.segments(
                SegmentAlgebra.decompose(SegmentDotUtils.parse(SegmentFixtureShapes.DOT_S8.dot())),
                SegmentFixtureShapes.DOT_S8.startsSegment());
        final List<String> underSplitter0 = segments.stream()
                .filter(s -> Long.valueOf(192031L).equals(s.forkTaskId()))
                .map(SegmentData.Segment::lineCtxId)
                .sorted()
                .toList();
        assertEquals(List.of("1,17#1", "1,17#2", "1,17#3", "1,17#4", "1,6#1"), underSplitter0,
                "S8: every line of both bases under the one fork is its own segment");
        for (SegmentData.Segment s : segments) {
            if (s.lineCtxId().startsWith("1,17#")) {
                assertEquals(1, s.lines().size(), "S8: a manual line " + s.lineCtxId() + " is flat - one line, one segment");
                assertEquals(5, s.lines().getFirst().tasks().size(), "S8: a manual line has five Tasks");
            }
        }
    }

    private static void assertRejected(SegmentFixtureShapes.DotShape shape, String code) {
        final SegmentData.Graph graph = SegmentDotUtils.parse(shape.dot());
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> SegmentAlgebra.decompose(graph),
                shape.id() + " must be rejected");
        assertTrue(e.getMessage().startsWith(code), shape.id() + ": expected error " + code + ", was: " + e.getMessage());
    }

    @Test public void test_X1_edgeBetweenTwoLinesOfOneFork_rejected() { assertRejected(SegmentFixtureShapes.DOT_X1, "01.906.040"); }
    @Test public void test_X2_tailWiredToWrongJoin_rejected() { assertRejected(SegmentFixtureShapes.DOT_X2, "01.906.080"); }
    @Test public void test_X3_oneJoinFedByTwoForks_rejected() { assertRejected(SegmentFixtureShapes.DOT_X3, "01.906.080"); }
}
