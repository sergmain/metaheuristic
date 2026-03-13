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

package ai.metaheuristic.ai.exec_context_graph;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.api.EnumsApi;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.util.SupplierUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Reproduces the parallel subprocess bug in TaskProducingService.createTasksForSubProcesses.
 *
 * Bug: in parallel (and) mode, the loop always updates parentTaskIds to the current task,
 * causing each subprocess to chain to the previous one (sequential) instead of all
 * branching from the wrapper task.
 *
 * Also, lastIds only records the LAST subprocess task instead of ALL of them,
 * so downstream tasks (mh.finish) only connect to the last branch.
 *
 * Real scenario from mhdg-rg-flat-short-1.0.0.mhsc:
 *   mh.nop-amendment-gate (Task#923) has parallel { mh.nop-active-branch, mh.nop-obsolete-branch }
 *   Expected: #923 -> #925 (active), #923 -> #926 (obsolete), both -> downstream
 *   Actual:   #923 -> #925 -> #926 (sequential chain)
 *
 * @author Sergio Lissner
 * Date: 3/13/2026
 */
@Execution(CONCURRENT)
class ExecContextGraphParallelSubProcessTest {

    /**
     * Simulates the BUGGY createTasksForSubProcesses loop with 'and' (parallel) logic.
     * Confirms the sequential chaining behavior.
     */
    @Test
    public void test_parallelSubProcess_buggySequentialChaining() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();

        ExecContextData.TaskVertex t923 = addVertex(graph, 923L, "1,2,5");   // mh.nop-amendment-gate
        ExecContextData.TaskVertex t900 = addVertex(graph, 900L, "1");       // mh.finish (downstream)
        graph.addEdge(t923, t900);

        // Simulate BUGGY loop: parentTaskIds updated after each iteration
        List<Long> parentTaskIds = new ArrayList<>(List.of(923L));
        List<Long> lastIds = new ArrayList<>();

        // Iteration 1: mh.nop-active-branch
        ExecContextData.TaskVertex t925 = addVertex(graph, 925L, "1,2,5,6#0");
        addEdgesFromParents(graph, parentTaskIds, t925);
        parentTaskIds = List.of(t925.taskId);  // BUG: reassigned for parallel

        // Iteration 2: mh.nop-obsolete-branch
        ExecContextData.TaskVertex t926 = addVertex(graph, 926L, "1,2,5,7#0");
        addEdgesFromParents(graph, parentTaskIds, t926);
        parentTaskIds = List.of(t926.taskId);

        lastIds.add(t926.taskId);  // BUG: only last

        // Confirm the bug: T925 -> T926 edge (sequential chain)
        assertTrue(graph.containsEdge(t925, t926),
                "Bug confirmed: T925 -> T926 creates sequential chain instead of parallel");

        // T926 has T925 as parent, not T923
        Set<ExecContextData.TaskVertex> parentsOf926 = getParents(graph, t926);
        assertTrue(parentsOf926.stream().anyMatch(v -> v.taskId == 925L));
        assertFalse(parentsOf926.stream().anyMatch(v -> v.taskId == 923L),
                "Bug: T926 parent should be T923 but is T925");

        // lastIds only has T926
        assertEquals(1, lastIds.size());
        assertEquals(926L, lastIds.get(0));
    }

    /**
     * Demonstrates the CORRECT parallel behavior after fix.
     * For 'and' logic: parentTaskIds stays as the original wrapper, all tasks added to lastIds.
     */
    @Test
    public void test_parallelSubProcess_correctBehavior() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();

        ExecContextData.TaskVertex t923 = addVertex(graph, 923L, "1,2,5");   // mh.nop-amendment-gate
        ExecContextData.TaskVertex t900 = addVertex(graph, 900L, "1");       // mh.finish (downstream)
        graph.addEdge(t923, t900);

        // Simulate FIXED loop: parentTaskIds stays [923] for all iterations in 'and' mode
        Long parentTaskId = 923L;
        List<Long> parentTaskIds = List.of(parentTaskId);
        List<Long> lastIds = new ArrayList<>();

        // Iteration 1: mh.nop-active-branch
        ExecContextData.TaskVertex t925 = addVertex(graph, 925L, "1,2,5,6#0");
        addEdgesFromParents(graph, parentTaskIds, t925);
        lastIds.add(t925.taskId);
        // parentTaskIds stays [923]

        // Iteration 2: mh.nop-obsolete-branch
        ExecContextData.TaskVertex t926 = addVertex(graph, 926L, "1,2,5,7#0");
        addEdgesFromParents(graph, parentTaskIds, t926);
        lastIds.add(t926.taskId);

        // Verify parallel topology
        assertTrue(graph.containsEdge(t923, t925), "T923 -> T925 (parallel branch 1)");
        assertTrue(graph.containsEdge(t923, t926), "T923 -> T926 (parallel branch 2)");
        assertFalse(graph.containsEdge(t925, t926), "No sequential chain: T925 -/-> T926");
        assertFalse(graph.containsEdge(t926, t925), "No reverse chain: T926 -/-> T925");

        // T926 parent is T923 (the wrapper), not T925
        Set<ExecContextData.TaskVertex> parentsOf926 = getParents(graph, t926);
        assertEquals(1, parentsOf926.size());
        assertTrue(parentsOf926.stream().anyMatch(v -> v.taskId == 923L));

        // lastIds contains BOTH branches
        assertEquals(2, lastIds.size());
        assertTrue(lastIds.contains(925L));
        assertTrue(lastIds.contains(926L));

        // Simulate createEdges: connect ALL lastIds to downstream (mh.finish)
        // Remove existing edge from wrapper to finish first (it was a placeholder from static DAG)
        graph.removeEdge(t923, t900);
        for (Long lastId : lastIds) {
            ExecContextData.TaskVertex src = graph.vertexSet().stream()
                    .filter(v -> v.taskId.equals(lastId)).findFirst().orElseThrow();
            graph.addEdge(src, t900);
        }

        // mh.finish should have BOTH branches as parents
        Set<ExecContextData.TaskVertex> parentsOfFinish = getParents(graph, t900);
        assertEquals(2, parentsOfFinish.size());
        assertTrue(parentsOfFinish.stream().anyMatch(v -> v.taskId == 925L));
        assertTrue(parentsOfFinish.stream().anyMatch(v -> v.taskId == 926L));
    }

    /**
     * Verifies that sequential logic is NOT affected by the fix.
     * For 'sequential' mode, parentTaskIds SHOULD be updated to chain tasks.
     */
    @Test
    public void test_sequentialSubProcess_stillChainsCorrectly() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();

        ExecContextData.TaskVertex t100 = addVertex(graph, 100L, "1,2,5");
        ExecContextData.TaskVertex t200 = addVertex(graph, 200L, "1");
        graph.addEdge(t100, t200);

        Long parentTaskId = 100L;
        List<Long> parentTaskIds = List.of(parentTaskId);
        List<Long> lastIds = new ArrayList<>();
        EnumsApi.SourceCodeSubProcessLogic logic = EnumsApi.SourceCodeSubProcessLogic.sequential;

        // Iteration 1
        ExecContextData.TaskVertex t101 = addVertex(graph, 101L, "1,2,5,6#0");
        addEdgesFromParents(graph, parentTaskIds, t101);
        // For sequential: update parentTaskIds
        parentTaskIds = List.of(t101.taskId);

        // Iteration 2
        ExecContextData.TaskVertex t102 = addVertex(graph, 102L, "1,2,5,6#0");
        addEdgesFromParents(graph, parentTaskIds, t102);
        parentTaskIds = List.of(t102.taskId);

        // For sequential: only last task in lastIds
        lastIds.add(t102.taskId);

        // Sequential chain is correct
        assertTrue(graph.containsEdge(t100, t101), "T100 -> T101");
        assertTrue(graph.containsEdge(t101, t102), "T101 -> T102 (sequential chain)");
        assertFalse(graph.containsEdge(t100, t102), "No skip: T100 -/-> T102");

        assertEquals(1, lastIds.size());
        assertEquals(102L, lastIds.get(0));
    }

    // ======================== Helpers ========================

    private static DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> createGraph() {
        return new DirectedAcyclicGraph<>(ExecContextData.TaskVertex::new, SupplierUtil.DEFAULT_EDGE_SUPPLIER, false);
    }

    private static ExecContextData.TaskVertex addVertex(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, Long taskId, String taskContextId) {
        ExecContextData.TaskVertex v = new ExecContextData.TaskVertex(taskId, taskContextId);
        graph.addVertex(v);
        return v;
    }

    private static void addEdgesFromParents(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph,
            List<Long> parentTaskIds, ExecContextData.TaskVertex target) {
        for (Long parentId : parentTaskIds) {
            ExecContextData.TaskVertex parent = graph.vertexSet().stream()
                    .filter(v -> v.taskId.equals(parentId)).findFirst().orElseThrow();
            graph.addEdge(parent, target);
        }
    }

    private static Set<ExecContextData.TaskVertex> getParents(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, ExecContextData.TaskVertex vertex) {
        return graph.incomingEdgesOf(vertex).stream()
                .map(graph::getEdgeSource)
                .collect(Collectors.toSet());
    }
}
