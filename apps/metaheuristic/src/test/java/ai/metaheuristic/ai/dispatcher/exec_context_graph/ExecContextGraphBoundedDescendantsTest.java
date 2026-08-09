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

package ai.metaheuristic.ai.dispatcher.exec_context_graph;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Bounded descendant walk: descendants reachable WITHOUT passing through a declined vertex.
 *
 * <p>The contract worth pinning is that "prune at X" means <em>unreachable except through X</em>,
 * not "everything the unbounded walk returned, minus X's subtree". Those two readings differ
 * exactly when a vertex has a second parent outside the pruned region, and the second reading
 * would silently drop work that is still genuinely live.
 *
 * <p>Graphs are built from DOT so the whole thing runs without a Spring context, the same way
 * {@link ValidateGraphStructureTest} does.
 *
 * @author Sergio Lissner
 * Date: 8/8/2026
 */
@Execution(CONCURRENT)
public class ExecContextGraphBoundedDescendantsTest {

    private static final Predicate<ExecContextData.TaskVertex> DESCEND_EVERYWHERE = _ -> true;

    private static DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graphOf(String edgesAndVertices) {
        return ExecContextGraphService.importExecContextGraph(
                "strict digraph G {\n" + edgesAndVertices + "\n}\n");
    }

    private static List<Long> idsOf(Set<ExecContextData.TaskVertex> vertices) {
        return vertices.stream().map(v -> v.taskId).sorted().collect(Collectors.toList());
    }

    /** Decline to descend below the given task ids. */
    private static Predicate<ExecContextData.TaskVertex> pruneAt(Long... taskIds) {
        Set<Long> declined = Set.of(taskIds);
        return v -> !declined.contains(v.taskId);
    }

    // ==================================================================
    // Baseline — with no bound, the walk matches the unbounded one.
    // ==================================================================
    @Test
    public void test_descendEverywhere_returnsWholeSubtree() {
        var graph = graphOf("""
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" ];
              4 [ ctxid="1" ];
              1 -> 2;
              2 -> 3;
              3 -> 4;
            """);

        assertEquals(List.of(2L, 3L, 4L),
                idsOf(ExecContextGraphService.findDescendantsBounded(graph, 1L, DESCEND_EVERYWHERE)));
    }

    @Test
    public void test_startVertexIsNeverInTheResult() {
        var graph = graphOf("""
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              1 -> 2;
            """);

        Set<ExecContextData.TaskVertex> d =
                ExecContextGraphService.findDescendantsBounded(graph, 1L, DESCEND_EVERYWHERE);
        assertFalse(d.stream().anyMatch(v -> v.taskId == 1L), "start vertex must not be a descendant of itself");
    }

    @Test
    public void test_unknownTaskId_returnsEmpty() {
        var graph = graphOf("""
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              1 -> 2;
            """);

        assertTrue(ExecContextGraphService.findDescendantsBounded(graph, 999L, DESCEND_EVERYWHERE).isEmpty());
    }

    @Test
    public void test_leafStart_returnsEmpty() {
        var graph = graphOf("""
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              1 -> 2;
            """);

        assertTrue(ExecContextGraphService.findDescendantsBounded(graph, 2L, DESCEND_EVERYWHERE).isEmpty());
    }

    // ==================================================================
    // Pruning at a vertex excludes its EXCLUSIVE descendants.
    // ==================================================================
    @Test
    public void test_pruneAtVertex_excludesItsExclusiveDescendants() {
        //   1 -> 2 -> 3 -> 4      (3 and 4 hang exclusively off 2)
        //   1 -> 5
        var graph = graphOf("""
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" ];
              4 [ ctxid="1" ];
              5 [ ctxid="1" ];
              1 -> 2;
              2 -> 3;
              3 -> 4;
              1 -> 5;
            """);

        // 2 itself survives — it is reached from its own parent, not through itself.
        assertEquals(List.of(2L, 5L),
                idsOf(ExecContextGraphService.findDescendantsBounded(graph, 1L, pruneAt(2L))));
    }

    @Test
    public void test_prunedVertexItselfRemainsADescendant() {
        var graph = graphOf("""
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" ];
              1 -> 2;
              2 -> 3;
            """);

        Set<ExecContextData.TaskVertex> d =
                ExecContextGraphService.findDescendantsBounded(graph, 1L, pruneAt(2L));
        assertTrue(d.stream().anyMatch(v -> v.taskId == 2L),
                "the pruned vertex is reached by an edge from its parent, so it is still a descendant");
        assertFalse(d.stream().anyMatch(v -> v.taskId == 3L));
    }

    @Test
    public void test_pruningAtSeveralVertices_excludesEachExclusiveSubtree() {
        //   1 -> 2 -> 20
        //   1 -> 3 -> 30
        //   1 -> 4 -> 40
        var graph = graphOf("""
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" ];
              4 [ ctxid="1" ];
              20 [ ctxid="1" ];
              30 [ ctxid="1" ];
              40 [ ctxid="1" ];
              1 -> 2;
              1 -> 3;
              1 -> 4;
              2 -> 20;
              3 -> 30;
              4 -> 40;
            """);

        assertEquals(List.of(2L, 3L, 4L, 40L),
                idsOf(ExecContextGraphService.findDescendantsBounded(graph, 1L, pruneAt(2L, 3L))));
    }

    // ==================================================================
    // A vertex reachable by a SECOND path survives the prune.
    // ==================================================================
    @Test
    public void test_vertexReachableBySecondPath_survivesThePrune() {
        //   1 -> 2 -> 4     (2 is pruned)
        //   1 -> 3 -> 4     (4 is still reachable through 3)
        var graph = graphOf("""
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" ];
              4 [ ctxid="1" ];
              1 -> 2;
              1 -> 3;
              2 -> 4;
              3 -> 4;
            """);

        assertEquals(List.of(2L, 3L, 4L),
                idsOf(ExecContextGraphService.findDescendantsBounded(graph, 1L, pruneAt(2L))));
    }

    @Test
    public void test_vertexReachableOnlyThroughPrunedPaths_isExcluded() {
        //   Same diamond, but BOTH shoulders are pruned, so 4 has no surviving path.
        var graph = graphOf("""
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" ];
              4 [ ctxid="1" ];
              1 -> 2;
              1 -> 3;
              2 -> 4;
              3 -> 4;
            """);

        assertEquals(List.of(2L, 3L),
                idsOf(ExecContextGraphService.findDescendantsBounded(graph, 1L, pruneAt(2L, 3L))));
    }

    @Test
    public void test_deepRejoinAfterAPrunedBranch_isStillReached() {
        //   1 -> 2 -> 3 -> 6      (2 pruned: 3 unreachable through it)
        //   1 -> 4 -> 5 -> 6      (6 rejoins via the surviving branch)
        var graph = graphOf("""
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" ];
              4 [ ctxid="1" ];
              5 [ ctxid="1" ];
              6 [ ctxid="1" ];
              1 -> 2;
              2 -> 3;
              3 -> 6;
              1 -> 4;
              4 -> 5;
              5 -> 6;
            """);

        assertEquals(List.of(2L, 4L, 5L, 6L),
                idsOf(ExecContextGraphService.findDescendantsBounded(graph, 1L, pruneAt(2L))));
    }

    // ==================================================================
    // The shape Phase 13/14 relies on: prune at a splitter, keep the tail.
    // ==================================================================
    @Test
    public void test_pruneAtSplitter_keepsTerminalReachableThroughTheBypassEdge() {
        //   10 (reset point) -> 11 (splitter) -> 12, 13   (per-line work)
        //   10 -> 99 (terminal)                            (bypass edge)
        // Pruning at the splitter must drop the per-line work and KEEP the terminal, or the
        // cloned EC would hang STARTED forever with no snapshot ever committed.
        var graph = graphOf("""
              10 [ ctxid="1" ];
              11 [ ctxid="1" ];
              12 [ ctxid="1,2#1" ];
              13 [ ctxid="1,2#2" ];
              99 [ ctxid="1" ];
              10 -> 11;
              11 -> 12;
              11 -> 13;
              10 -> 99;
            """);

        Set<ExecContextData.TaskVertex> d =
                ExecContextGraphService.findDescendantsBounded(graph, 10L, pruneAt(11L));
        assertEquals(List.of(11L, 99L), idsOf(d));
        assertTrue(d.stream().anyMatch(v -> v.taskId == 99L),
                "terminal must stay reachable from the reset point when the splitter is pruned");
    }

    @Test
    public void test_pruneAtSplitter_whenTerminalHangsOffTheSplitter_terminalIsLost() {
        //   The counter-shape, pinned deliberately: if the ONLY path to the terminal runs through
        //   the splitter, pruning there DOES drop it. This is the graph-level statement of the
        //   hazard — whether a given rung looks like this is what the RG-side reachability test
        //   answers, and Phase 13 must not prune where it does.
        var graph = graphOf("""
              10 [ ctxid="1" ];
              11 [ ctxid="1" ];
              12 [ ctxid="1,2#1" ];
              99 [ ctxid="1" ];
              10 -> 11;
              11 -> 12;
              12 -> 99;
            """);

        assertEquals(List.of(11L),
                idsOf(ExecContextGraphService.findDescendantsBounded(graph, 10L, pruneAt(11L))));
    }

    @Test
    public void test_boundedWalkAgreesWithUnboundedWalkWhenNothingIsPruned() {
        var graph = graphOf("""
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" ];
              4 [ ctxid="1" ];
              5 [ ctxid="1" ];
              1 -> 2;
              1 -> 3;
              2 -> 4;
              3 -> 4;
              4 -> 5;
            """);

        assertEquals(List.of(2L, 3L, 4L, 5L),
                idsOf(ExecContextGraphService.findDescendantsBounded(graph, 1L, DESCEND_EVERYWHERE)));
    }
}
