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

package ai.metaheuristic.ai.dispatcher.exec_context_graph;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.util.SupplierUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 3/8/2026
 * Time: 7:00 PM
 */
@Execution(ExecutionMode.CONCURRENT)
class ExecContextGraphServiceTest {

    private static DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> createGraph() {
        return new DirectedAcyclicGraph<>(ExecContextData.TaskVertex::new, SupplierUtil.DEFAULT_EDGE_SUPPLIER, false);
    }

    private static ExecContextData.TaskVertex addVertex(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, long taskId, String ctxId) {
        ExecContextData.TaskVertex v = new ExecContextData.TaskVertex(taskId, ctxId);
        graph.addVertex(v);
        return v;
    }

    private static Set<Long> taskIds(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph) {
        return graph.vertexSet().stream().map(v -> v.taskId).collect(Collectors.toSet());
    }

    private static boolean hasEdge(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, long fromId, long toId) {
        ExecContextData.TaskVertex from = graph.vertexSet().stream().filter(v -> v.taskId == fromId).findFirst().orElse(null);
        ExecContextData.TaskVertex to = graph.vertexSet().stream().filter(v -> v.taskId == toId).findFirst().orElse(null);
        if (from == null || to == null) {
            return false;
        }
        return graph.containsEdge(from, to);
    }

    /**
     * Simple chain: A -> B -> C
     * Remove B, expect A -> C
     */
    @Test
    void test_removeVertices_middleOfChain() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextData.TaskVertex a = addVertex(graph, 1L, "1");
        ExecContextData.TaskVertex b = addVertex(graph, 2L, "1,2");
        ExecContextData.TaskVertex c = addVertex(graph, 3L, "1,2,3");
        graph.addEdge(a, b);
        graph.addEdge(b, c);

        // act
        ExecContextGraphService.removeVerticesFromGraph(graph, List.of(new ExecContextData.TaskVertex(2L, "1,2")));

        assertEquals(2, graph.vertexSet().size());
        assertTrue(taskIds(graph).contains(1L));
        assertTrue(taskIds(graph).contains(3L));
        assertFalse(taskIds(graph).contains(2L));
        assertTrue(hasEdge(graph, 1L, 3L));
    }

    /**
     * Chain: A -> B -> C -> D
     * Remove B and C, expect A -> D
     */
    @Test
    void test_removeVertices_twoMiddleVertices() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextData.TaskVertex a = addVertex(graph, 1L, "1");
        ExecContextData.TaskVertex b = addVertex(graph, 2L, "1,2");
        ExecContextData.TaskVertex c = addVertex(graph, 3L, "1,2,3");
        ExecContextData.TaskVertex d = addVertex(graph, 4L, "1,2,3,4");
        graph.addEdge(a, b);
        graph.addEdge(b, c);
        graph.addEdge(c, d);

        // act
        ExecContextGraphService.removeVerticesFromGraph(graph, List.of(
                new ExecContextData.TaskVertex(2L, "1,2"),
                new ExecContextData.TaskVertex(3L, "1,2,3")));

        assertEquals(2, graph.vertexSet().size());
        assertTrue(taskIds(graph).contains(1L));
        assertTrue(taskIds(graph).contains(4L));
        assertTrue(hasEdge(graph, 1L, 4L));
    }

    /**
     * Diamond: A -> B, A -> C, B -> D, C -> D
     * Remove B, expect A -> D (direct), A -> C still exists, C -> D still exists
     */
    @Test
    void test_removeVertices_diamond() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextData.TaskVertex a = addVertex(graph, 1L, "1");
        ExecContextData.TaskVertex b = addVertex(graph, 2L, "1,2");
        ExecContextData.TaskVertex c = addVertex(graph, 3L, "1,3");
        ExecContextData.TaskVertex d = addVertex(graph, 4L, "1,4");
        graph.addEdge(a, b);
        graph.addEdge(a, c);
        graph.addEdge(b, d);
        graph.addEdge(c, d);

        // act
        ExecContextGraphService.removeVerticesFromGraph(graph, List.of(new ExecContextData.TaskVertex(2L, "1,2")));

        assertEquals(3, graph.vertexSet().size());
        assertFalse(taskIds(graph).contains(2L));
        assertTrue(hasEdge(graph, 1L, 4L));
        assertTrue(hasEdge(graph, 1L, 3L));
        assertTrue(hasEdge(graph, 3L, 4L));
    }

    /**
     * Remove leaf vertex: A -> B -> C, remove C
     * Expect A -> B, no reconnection needed
     */
    @Test
    void test_removeVertices_leafVertex() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextData.TaskVertex a = addVertex(graph, 1L, "1");
        ExecContextData.TaskVertex b = addVertex(graph, 2L, "1,2");
        ExecContextData.TaskVertex c = addVertex(graph, 3L, "1,2,3");
        graph.addEdge(a, b);
        graph.addEdge(b, c);

        // act
        ExecContextGraphService.removeVerticesFromGraph(graph, List.of(new ExecContextData.TaskVertex(3L, "1,2,3")));

        assertEquals(2, graph.vertexSet().size());
        assertTrue(hasEdge(graph, 1L, 2L));
    }

    /**
     * Remove root vertex: A -> B -> C, remove A
     * Expect B -> C, B becomes root
     */
    @Test
    void test_removeVertices_rootVertex() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextData.TaskVertex a = addVertex(graph, 1L, "1");
        ExecContextData.TaskVertex b = addVertex(graph, 2L, "1,2");
        ExecContextData.TaskVertex c = addVertex(graph, 3L, "1,2,3");
        graph.addEdge(a, b);
        graph.addEdge(b, c);

        // act
        ExecContextGraphService.removeVerticesFromGraph(graph, List.of(new ExecContextData.TaskVertex(1L, "1")));

        assertEquals(2, graph.vertexSet().size());
        assertTrue(hasEdge(graph, 2L, 3L));
        ExecContextData.TaskVertex root = graph.vertexSet().stream()
                .filter(v -> graph.incomingEdgesOf(v).isEmpty())
                .findFirst().orElse(null);
        assertNotNull(root);
        assertEquals(2L, root.taskId);
    }

    /**
     * Fan-out: A -> B, A -> C, A -> D, B -> E, C -> E, D -> E
     * Remove B and C, expect A -> E (reconnected), A -> D still exists, D -> E still exists
     */
    @Test
    void test_removeVertices_fanOutMultipleMiddle() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextData.TaskVertex a = addVertex(graph, 1L, "1");
        ExecContextData.TaskVertex b = addVertex(graph, 2L, "1,2");
        ExecContextData.TaskVertex c = addVertex(graph, 3L, "1,3");
        ExecContextData.TaskVertex d = addVertex(graph, 4L, "1,4");
        ExecContextData.TaskVertex e = addVertex(graph, 5L, "1,5");
        graph.addEdge(a, b);
        graph.addEdge(a, c);
        graph.addEdge(a, d);
        graph.addEdge(b, e);
        graph.addEdge(c, e);
        graph.addEdge(d, e);

        // act
        ExecContextGraphService.removeVerticesFromGraph(graph, List.of(
                new ExecContextData.TaskVertex(2L, "1,2"),
                new ExecContextData.TaskVertex(3L, "1,3")));

        assertEquals(3, graph.vertexSet().size());
        assertTrue(hasEdge(graph, 1L, 5L));
        assertTrue(hasEdge(graph, 1L, 4L));
        assertTrue(hasEdge(graph, 4L, 5L));
    }

    /**
     * Remove a vertex that doesn't exist in the graph.
     * Expect no changes.
     */
    @Test
    void test_removeVertices_nonExistentVertex() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextData.TaskVertex a = addVertex(graph, 1L, "1");
        ExecContextData.TaskVertex b = addVertex(graph, 2L, "1,2");
        graph.addEdge(a, b);

        // act
        ExecContextGraphService.removeVerticesFromGraph(graph, List.of(new ExecContextData.TaskVertex(99L, "99")));

        assertEquals(2, graph.vertexSet().size());
        assertTrue(hasEdge(graph, 1L, 2L));
    }

    /**
     * Simulates the MHDG-RG scenario from the screenshot:
     * A(305) -> B(306) -> C(308) -> D(309) -> E(311) -> F(312)
     *                                                \-> G(310)
     *                                                         \-> H(307) -> FINISH(293)
     *
     * When resetting 305, sub-layer tasks 308,309,311,312,310 should be deleted,
     * and their parents reconnected to the remaining vertices (307, 293).
     */
    @Test
    void test_removeVertices_mhdgRgScenario() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextData.TaskVertex v305 = addVertex(graph, 305L, "1,2");
        ExecContextData.TaskVertex v306 = addVertex(graph, 306L, "1,2,5");
        ExecContextData.TaskVertex v308 = addVertex(graph, 308L, "1,2,5,6|1#0");
        ExecContextData.TaskVertex v309 = addVertex(graph, 309L, "1,2,5,6|1#0");
        ExecContextData.TaskVertex v311 = addVertex(graph, 311L, "1,2,5,6|1#0");
        ExecContextData.TaskVertex v312 = addVertex(graph, 312L, "1,2,5,6|1#0");
        ExecContextData.TaskVertex v310 = addVertex(graph, 310L, "1,2,5");
        ExecContextData.TaskVertex v307 = addVertex(graph, 307L, "1,2");
        ExecContextData.TaskVertex v293 = addVertex(graph, 293L, "1");

        graph.addEdge(v305, v306);
        graph.addEdge(v306, v308);
        graph.addEdge(v308, v309);
        graph.addEdge(v309, v311);
        graph.addEdge(v311, v312);
        graph.addEdge(v312, v310);
        graph.addEdge(v310, v307);
        graph.addEdge(v307, v293);

        // Remove the inner sub-layer tasks (those from deeper ctxIds)
        List<ExecContextData.TaskVertex> forDeletion = List.of(
                new ExecContextData.TaskVertex(308L, "1,2,5,6|1#0"),
                new ExecContextData.TaskVertex(309L, "1,2,5,6|1#0"),
                new ExecContextData.TaskVertex(311L, "1,2,5,6|1#0"),
                new ExecContextData.TaskVertex(312L, "1,2,5,6|1#0"));

        // act
        ExecContextGraphService.removeVerticesFromGraph(graph, forDeletion);

        assertEquals(5, graph.vertexSet().size());
        assertTrue(taskIds(graph).containsAll(Set.of(305L, 306L, 310L, 307L, 293L)));
        // 306 should now connect directly to 310 (since all inner tasks removed)
        assertTrue(hasEdge(graph, 306L, 310L));
        assertTrue(hasEdge(graph, 310L, 307L));
        assertTrue(hasEdge(graph, 307L, 293L));
    }
}
