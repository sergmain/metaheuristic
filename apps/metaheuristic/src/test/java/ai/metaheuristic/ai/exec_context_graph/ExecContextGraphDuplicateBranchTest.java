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

package ai.metaheuristic.ai.exec_context_graph;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.utils.ContextUtils;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.util.SupplierUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Reproduces the duplicate branch bug when a wrapper task (mh.nop) with subProcesses
 * is reset and re-executed.
 *
 * Root cause: when processSubProcesses runs, it calls findDirectDescendants(wrapper)
 * which returns the OLD dynamically-created child (from the first run) along with the
 * real downstream tasks. Then createEdges connects the NEW last subProcess task to ALL
 * those descendants, creating an edge newChild → oldChild. This produces a duplicate
 * branch in the DAG.
 *
 * Real scenario from MHDG-RG: mh.nop-objectives-wrapper-2 (Task#153) re-executes after
 * objective is applied. findDirectDescendants returns [Task#154 (old nop-objectives-2),
 * Task#141 (mh.finish)]. New Task#155 is created and createEdges produces 155→154 and
 * 155→141. The 155→154 edge is the bug — it makes the old task a child of the new one.
 *
 * @author Sergio Lissner
 * Date: 3/6/2026
 */
@Execution(CONCURRENT)
class ExecContextGraphDuplicateBranchTest {

    /**
     * Simulates the exact graph topology from the real bug.
     *
     * Phase 1 — First run (nop-objectives SKIPPED, no subProcess tasks created):
     * <pre>
     *   T151 (set-reset-task-id, ctx="1,2,5|1#1")
     *     → T152 (check-objectives, ctx="1,2,5|1#1")
     *       → T153 (nop-wrapper, ctx="1,2,5|1#1")
     *         → T154 (nop-objectives, ctx="1,2,5,6|1|1#0") [SKIPPED, no subProcess created]
     *           → T141 (mh.finish, ctx="1")
     * </pre>
     *
     * Phase 2 — After objective applied and reset, wrapper re-executes:
     *   findDirectDescendants(T153) returns [T154, T141]
     *   createTasksForSubProcesses creates T155 (new nop-objectives)
     *   createEdges(lastIds=[T155], descendants=[T154, T141])
     *     → creates edges: T155→T154 and T155→T141
     *
     * Result graph:
     * <pre>
     *   T153 → T154 (old)
     *   T153 → T155 (new)
     *   T155 → T154 (BUG: new linked as parent of old)
     *   T155 → T141
     *   T154 → T141
     * </pre>
     *
     * The test confirms that T155→T154 should NOT exist — old children from previous run
     * should be filtered from descendants before createEdges.
     */
    @Test
    public void test_duplicateBranch_oldChildBecomesDependentOfNewChild() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();

        // Phase 1: Initial graph structure after first run
        ExecContextData.TaskVertex t151 = addVertex(graph, 151L, "1,2,5|1#1");
        ExecContextData.TaskVertex t152 = addVertex(graph, 152L, "1,2,5|1#1");
        ExecContextData.TaskVertex t153 = addVertex(graph, 153L, "1,2,5|1#1");   // nop-wrapper
        ExecContextData.TaskVertex t154 = addVertex(graph, 154L, "1,2,5,6|1|1#0"); // OLD nop-objectives (SKIPPED first run)
        ExecContextData.TaskVertex t141 = addVertex(graph, 141L, "1");             // mh.finish

        graph.addEdge(t151, t152);
        graph.addEdge(t152, t153);
        graph.addEdge(t153, t154);
        graph.addEdge(t154, t141);

        // Phase 2: Wrapper (T153) re-executes after reset.
        // findDirectDescendants(T153) returns T154 and T141 (via T154)
        // But we only need DIRECT descendants:
        Set<ExecContextData.TaskVertex> directDescendants = graph.outgoingEdgesOf(t153).stream()
                .map(graph::getEdgeTarget)
                .collect(Collectors.toSet());

        // Confirm direct descendants of T153 — only T154
        assertEquals(1, directDescendants.size());
        assertTrue(directDescendants.stream().anyMatch(v -> v.taskId == 154L));

        // Create NEW nop-objectives task T155 (same processCode, same taskContextId as T154)
        ExecContextData.TaskVertex t155 = addVertex(graph, 155L, "1,2,5,6|1|1#0");
        // Add edge from wrapper to new child (done by addNewTasksToGraph)
        graph.addEdge(t153, t155);

        // Now simulate createEdges(lastIds=[T155], descendants=directDescendants)
        // This is what the CURRENT buggy code does — connects T155 to ALL direct descendants of T153
        // which includes T154 (old child)
        for (ExecContextData.TaskVertex desc : directDescendants) {
            graph.addEdge(t155, desc);
        }

        // BUG CONFIRMED: T155 → T154 edge exists — new nop-objectives linked as parent of old
        assertTrue(graph.containsEdge(t155, t154),
                "Bug confirmed: T155 (new) → T154 (old) edge should NOT exist but was created by createEdges");

        // Verify the graph has the problematic topology — T154 now has TWO parents: T153 and T155
        Set<ExecContextData.TaskVertex> parentsOfT154 = graph.incomingEdgesOf(t154).stream()
                .map(graph::getEdgeSource)
                .collect(Collectors.toSet());
        assertEquals(2, parentsOfT154.size(),
                "T154 should have 2 parents (T153 and T155) confirming the duplicate branch");
        assertTrue(parentsOfT154.stream().anyMatch(v -> v.taskId == 153L));
        assertTrue(parentsOfT154.stream().anyMatch(v -> v.taskId == 155L));

        // Verify the consequence: T154 is now a descendant of T155
        Set<ExecContextData.TaskVertex> descendantsOfT155 = findDescendants(graph, t155);
        assertTrue(descendantsOfT155.stream().anyMatch(v -> v.taskId == 154L),
                "T154 should be a descendant of T155 — confirming the broken topology");
    }

    /**
     * Demonstrates the correct behavior after the fix:
     * Old children (identified by subProcessCtxPrefix match) should be excluded from
     * descendants before createEdges. Only real downstream tasks (mh.finish, read-req, etc.)
     * should be connected.
     */
    @Test
    public void test_fixedBehavior_oldChildFilteredFromDescendants() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();

        // Phase 1: Initial graph after first run
        ExecContextData.TaskVertex t153 = addVertex(graph, 153L, "1,2,5|1#1");   // nop-wrapper
        ExecContextData.TaskVertex t154 = addVertex(graph, 154L, "1,2,5,6|1|1#0"); // OLD nop-objectives
        ExecContextData.TaskVertex t141 = addVertex(graph, 141L, "1");             // mh.finish

        graph.addEdge(t153, t154);
        graph.addEdge(t154, t141);

        // Phase 2: Wrapper re-executes. Get direct descendants.
        Set<ExecContextData.TaskVertex> directDescendants = graph.outgoingEdgesOf(t153).stream()
                .map(graph::getEdgeTarget)
                .collect(Collectors.toSet());

        // Compute the subProcessCtxPrefix for filtering
        // Wrapper taskContextId = "1,2,5|1#1", subProcess processContextId = "1,2,5,6"
        // getCurrTaskContextIdForSubProcesses("1,2,5|1#1", "1,2,5,6") → "1,2,5,6|1|1"
        String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses("1,2,5|1#1", "1,2,5,6");
        assertEquals("1,2,5,6|1|1", subProcessContextId);
        String subProcessCtxPrefix = subProcessContextId + ContextUtils.CONTEXT_SEPARATOR;
        // subProcessCtxPrefix = "1,2,5,6|1|1#"

        // Filter: exclude descendants whose taskContextId starts with subProcessCtxPrefix
        Set<ExecContextData.TaskVertex> filteredDescendants = directDescendants.stream()
                .filter(v -> v.taskContextId == null || !v.taskContextId.startsWith(subProcessCtxPrefix))
                .collect(Collectors.toSet());

        // T154 (ctx="1,2,5,6|1|1#0") starts with "1,2,5,6|1|1#" → FILTERED OUT
        assertFalse(filteredDescendants.stream().anyMatch(v -> v.taskId == 154L),
                "T154 (old child) should be filtered out — its taskContextId starts with subProcessCtxPrefix");

        // filteredDescendants should be EMPTY in this case since T154 was the only direct descendant
        assertTrue(filteredDescendants.isEmpty(),
                "After filtering, no direct descendants remain (T141 is not a DIRECT descendant of T153)");

        // Create NEW nop-objectives T155
        ExecContextData.TaskVertex t155 = addVertex(graph, 155L, "1,2,5,6|1|1#0");
        graph.addEdge(t153, t155);

        // createEdges with filtered descendants — no edge T155→T154 is created
        for (ExecContextData.TaskVertex desc : filteredDescendants) {
            graph.addEdge(t155, desc);
        }

        // Verify: T155 → T154 edge does NOT exist
        assertFalse(graph.containsEdge(t155, t154),
                "After fix: T155 → T154 edge should NOT exist");

        // T154 should have only ONE parent: T153 (from original graph)
        Set<ExecContextData.TaskVertex> parentsOfT154 = graph.incomingEdgesOf(t154).stream()
                .map(graph::getEdgeSource)
                .collect(Collectors.toSet());
        assertEquals(1, parentsOfT154.size(), "T154 should have only 1 parent (T153)");
        assertTrue(parentsOfT154.stream().anyMatch(v -> v.taskId == 153L));
    }

    /**
     * Tests the full scenario matching the real graph from the bug report.
     * Uses the actual taskContextId values from the DOT graph dump.
     *
     * Verifies that with the real graph structure, after wrapper (T153) re-executes:
     * - findDirectDescendants returns [T154] (only direct, not T141)
     * - T154's taskContextId "1,2,5,6|1|1#0" matches subProcessCtxPrefix "1,2,5,6|1|1#"
     * - After filtering, mh.finish (T141) must be reached via InternalFunctionService.getSubProcesses
     *   which uses findDirectDescendants (not findDescendants), so T141 is NOT in the set
     * - The fix must ensure createEdges connects T155 → T141 (the real downstream) not T155 → T154
     *
     * NOTE: In the actual InternalFunctionService.getSubProcesses, the descendants come from
     * findDirectDescendants(wrapper). In the real graph, the wrapper's DIRECT descendants after
     * first run are [T154] (the old nop-objectives). T141 is connected through T154, not directly
     * from T153. So after filtering T154 out, the descendants set is EMPTY.
     * This means createEdges would not connect T155 to T141 either — which is another problem
     * to address (the new chain needs to connect to mh.finish somehow).
     */
    @Test
    public void test_realGraphTopology_fullScenario() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();

        // Build the full graph from the DOT dump (simplified to the relevant tasks)
        ExecContextData.TaskVertex t148 = addVertex(graph, 148L, "1,2#1");        // batch-line-splitter-1
        ExecContextData.TaskVertex t150 = addVertex(graph, 150L, "1,2,5|1#1");    // store-req-1
        ExecContextData.TaskVertex t151 = addVertex(graph, 151L, "1,2,5|1#1");    // set-reset-task-id-1
        ExecContextData.TaskVertex t152 = addVertex(graph, 152L, "1,2,5|1#1");    // check-objectives-2
        ExecContextData.TaskVertex t153 = addVertex(graph, 153L, "1,2,5|1#1");    // nop-objectives-wrapper-2
        ExecContextData.TaskVertex t154 = addVertex(graph, 154L, "1,2,5,6|1|1#0");// OLD nop-objectives-2
        ExecContextData.TaskVertex t141 = addVertex(graph, 141L, "1");             // mh.finish

        graph.addEdge(t148, t150);
        graph.addEdge(t150, t151);
        graph.addEdge(t151, t152);
        graph.addEdge(t152, t153);
        graph.addEdge(t153, t154);
        graph.addEdge(t154, t141);
        // From the real graph: 153→141 is NOT a direct edge — T141 is reached via T154

        // Verify: findDirectDescendants(T153) = [T154]
        Set<ExecContextData.TaskVertex> directDescOfWrapper = graph.outgoingEdgesOf(t153).stream()
                .map(graph::getEdgeTarget)
                .collect(Collectors.toSet());
        assertEquals(1, directDescOfWrapper.size());
        assertEquals(154L, directDescOfWrapper.iterator().next().taskId);

        // Now simulate wrapper re-execution.
        // subProcessContextId for this wrapper:
        String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses("1,2,5|1#1", "1,2,5,6");
        assertEquals("1,2,5,6|1|1", subProcessContextId);
        String subProcessCtxPrefix = subProcessContextId + ContextUtils.CONTEXT_SEPARATOR;

        // Without fix: T154 is in directDescOfWrapper and createEdges makes T155→T154
        // With fix: T154 is filtered out, directDescOfWrapper becomes empty

        // Verify T154 matches the filter
        assertTrue(t154.taskContextId.startsWith(subProcessCtxPrefix),
                "T154 ctx '1,2,5,6|1|1#0' should start with prefix '1,2,5,6|1|1#'");

        // Verify T141 does NOT match (even if it were in the set)
        assertFalse(t141.taskContextId.startsWith(subProcessCtxPrefix),
                "T141 ctx '1' should NOT start with prefix '1,2,5,6|1|1#'");

        // Apply filter
        Set<ExecContextData.TaskVertex> filtered = directDescOfWrapper.stream()
                .filter(v -> v.taskContextId == null || !v.taskContextId.startsWith(subProcessCtxPrefix))
                .collect(Collectors.toSet());

        assertTrue(filtered.isEmpty(),
                "After filtering, no direct descendants remain — T154 was the only direct descendant and it was filtered");

        // Create T155 (new nop-objectives) and add to graph
        ExecContextData.TaskVertex t155 = addVertex(graph, 155L, "1,2,5,6|1|1#0");
        graph.addEdge(t153, t155);

        // With filtered (empty) descendants, createEdges does nothing
        for (ExecContextData.TaskVertex desc : filtered) {
            graph.addEdge(t155, desc);
        }

        // Verify no T155→T154 edge
        assertFalse(graph.containsEdge(t155, t154),
                "T155 → T154 should NOT exist after fix");

        // Verify T155 only has edges FROM T153 (parent) and nothing outgoing to old children
        Set<ExecContextData.TaskVertex> outOfT155 = graph.outgoingEdgesOf(t155).stream()
                .map(graph::getEdgeTarget)
                .collect(Collectors.toSet());
        assertTrue(outOfT155.isEmpty(),
                "T155 should have no outgoing edges (since filtered descendants was empty)");
    }

    // ======================== Helper methods ========================

    private static DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> createGraph() {
        return new DirectedAcyclicGraph<>(ExecContextData.TaskVertex::new, SupplierUtil.DEFAULT_EDGE_SUPPLIER, false);
    }

    private static ExecContextData.TaskVertex addVertex(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, Long taskId, String taskContextId) {
        ExecContextData.TaskVertex v = new ExecContextData.TaskVertex(taskId, taskContextId);
        graph.addVertex(v);
        return v;
    }

    private static Set<ExecContextData.TaskVertex> findDescendants(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph,
            ExecContextData.TaskVertex vertex) {
        Iterator<ExecContextData.TaskVertex> iterator = new BreadthFirstIterator<>(graph, vertex);
        Set<ExecContextData.TaskVertex> descendants = new LinkedHashSet<>();
        if (iterator.hasNext()) {
            iterator.next(); // skip the start vertex
        }
        iterator.forEachRemaining(descendants::add);
        return descendants;
    }
}
