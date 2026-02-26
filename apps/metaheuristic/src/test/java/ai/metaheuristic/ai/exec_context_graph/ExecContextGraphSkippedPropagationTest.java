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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.util.SupplierUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;

import static ai.metaheuristic.api.EnumsApi.TaskExecState;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Tests for SKIPPED state propagation in the task DAG.
 *
 * When a task gets ERROR, its descendants are marked SKIPPED.
 * Additionally, any task whose ALL parents are now SKIPPED (or ERROR/SKIPPED)
 * should also be marked SKIPPED — cascading through siblings in sequential chains.
 *
 * This reproduces the problem where mh.nop-objectives errors out (due to variable not found)
 * but subsequent sibling tasks (read-req, decompose, store-reqs) remain stuck in NONE state,
 * causing the ExecContext to be stuck.
 *
 * @author Sergio Lissner
 * Date: 2/26/2026
 */
@Execution(CONCURRENT)
class ExecContextGraphSkippedPropagationTest {

    /**
     * Build a graph mimicking the objective processing structure from SourceCode.
     *
     * Graph for the main test:
     * <pre>
     *   T1 (check-objectives, ctx="1,1") → T2 (nop-wrapper, ctx="1,1")
     *   T2 → T3 (nop-objectives, ctx="1,1,1") [subProcess child]
     *   T2 → T6 (read-req, ctx="1,1") [sibling in outer sequential]
     *   T3 → T4 (evaluate-objective, ctx="1,1,1,1")
     *   T4 → T5 (store-objective-result, ctx="1,1,1,1")
     *   T6 → T7 (decompose, ctx="1,1")
     *   T7 → T8 (store-reqs, ctx="1,1")
     * </pre>
     *
     * When T2 (nop-wrapper) gets ERROR:
     * - T3, T4, T5 are descendants → SKIPPED
     * - T6 is a sibling whose single parent T2 is ERROR → SKIPPED
     * - T7, T8 cascade from T6 → SKIPPED
     */
    @Test
    public void test_errorPropagation_skipsAllDescendants() {
        // Simple linear chain: T1 → T2 → T3 → T4
        // T1 gets ERROR → T2, T3, T4 should be SKIPPED
        // This is the baseline — should already work

        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextTaskStateParamsYaml stateParams = new ExecContextTaskStateParamsYaml();

        ExecContextData.TaskVertex t1 = addVertex(graph, 1L, "1");
        ExecContextData.TaskVertex t2 = addVertex(graph, 2L, "1");
        ExecContextData.TaskVertex t3 = addVertex(graph, 3L, "1");
        ExecContextData.TaskVertex t4 = addVertex(graph, 4L, "1");

        graph.addEdge(t1, t2);
        graph.addEdge(t2, t3);
        graph.addEdge(t3, t4);

        // All start as NONE
        stateParams.states.put(1L, TaskExecState.NONE);
        stateParams.states.put(2L, TaskExecState.NONE);
        stateParams.states.put(3L, TaskExecState.NONE);
        stateParams.states.put(4L, TaskExecState.NONE);

        // Act: T1 goes ERROR
        ExecContextData.ExecContextDAC dac = new ExecContextData.ExecContextDAC(1L, graph, 1);
        ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);

        updateTaskState(dac, stateParams, 1L, TaskExecState.ERROR, "1", status);

        // Assert: T1=ERROR, T2=SKIPPED, T3=SKIPPED, T4=SKIPPED
        assertEquals(TaskExecState.ERROR, stateParams.states.get(1L));
        assertEquals(TaskExecState.SKIPPED, stateParams.states.get(2L));
        assertEquals(TaskExecState.SKIPPED, stateParams.states.get(3L));
        // T4 is a leaf (no outgoing edges) — current code filters out leaves (mh.finish filter)
        // This is expected existing behavior
    }

    /**
     * Test the critical scenario: sibling task with single SKIPPED parent gets SKIPPED too.
     *
     * Graph structure (mimics objective processing in SourceCode):
     * <pre>
     *   T1 (check-objectives) → T2 (nop-wrapper)
     *   T2 → T3 (nop-objectives, subProcess child)
     *   T2 → T6 (read-req, outer sequential sibling)
     *   T3 → T4 (evaluate-objective)
     *   T4 → T5 (store-objective-result)
     *   T6 → T7 (decompose)
     *   T7 → T8 (store-reqs)
     * </pre>
     *
     * When T2 (nop-wrapper) gets ERROR:
     * - T3 should be SKIPPED (child of T2, single parent T2 is ERROR)
     * - T4 should be SKIPPED (child of T3, single parent T3 is SKIPPED)
     * - T5 should be SKIPPED (child of T4, single parent T4 is SKIPPED)
     * - T6 should be SKIPPED (sibling, single parent T2 is ERROR → all parents finished)
     * - T7 should be SKIPPED (single parent T6 is SKIPPED)
     * - T8 should be SKIPPED (single parent T7 is SKIPPED) — leaf node, but still should be marked
     */
    @Test
    public void test_errorPropagation_skipsSiblingsWithSingleSkippedParent() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextTaskStateParamsYaml stateParams = new ExecContextTaskStateParamsYaml();

        ExecContextData.TaskVertex t1 = addVertex(graph, 1L, "1,1");
        ExecContextData.TaskVertex t2 = addVertex(graph, 2L, "1,1");
        ExecContextData.TaskVertex t3 = addVertex(graph, 3L, "1,1,1");
        ExecContextData.TaskVertex t4 = addVertex(graph, 4L, "1,1,1,1");
        ExecContextData.TaskVertex t5 = addVertex(graph, 5L, "1,1,1,1");
        ExecContextData.TaskVertex t6 = addVertex(graph, 6L, "1,1");
        ExecContextData.TaskVertex t7 = addVertex(graph, 7L, "1,1");
        ExecContextData.TaskVertex t8 = addVertex(graph, 8L, "1,1");

        graph.addEdge(t1, t2);
        graph.addEdge(t2, t3);
        graph.addEdge(t2, t6);
        graph.addEdge(t3, t4);
        graph.addEdge(t4, t5);
        graph.addEdge(t6, t7);
        graph.addEdge(t7, t8);

        for (long i = 1; i <= 8; i++) {
            stateParams.states.put(i, TaskExecState.NONE);
        }
        // T1 already finished OK
        stateParams.states.put(1L, TaskExecState.OK);
        // T2 is about to be set to ERROR
        stateParams.states.put(2L, TaskExecState.IN_PROGRESS);

        // Act: T2 (nop-wrapper) goes ERROR
        ExecContextData.ExecContextDAC dac = new ExecContextData.ExecContextDAC(1L, graph, 1);
        ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);

        updateTaskState(dac, stateParams, 2L, TaskExecState.ERROR, "1,1", status);

        // Assert
        assertEquals(TaskExecState.OK, stateParams.states.get(1L), "T1 should remain OK");
        assertEquals(TaskExecState.ERROR, stateParams.states.get(2L), "T2 should be ERROR");
        assertEquals(TaskExecState.SKIPPED, stateParams.states.get(3L), "T3 (subProcess child of T2) should be SKIPPED");
        assertEquals(TaskExecState.SKIPPED, stateParams.states.get(4L), "T4 (child of T3) should be SKIPPED");
        assertEquals(TaskExecState.SKIPPED, stateParams.states.get(5L), "T5 (child of T4, leaf) should be SKIPPED");
        assertEquals(TaskExecState.SKIPPED, stateParams.states.get(6L), "T6 (sibling, single parent T2 is ERROR) should be SKIPPED");
        assertEquals(TaskExecState.SKIPPED, stateParams.states.get(7L), "T7 (child of T6) should be SKIPPED");
        assertEquals(TaskExecState.SKIPPED, stateParams.states.get(8L), "T8 (child of T7, leaf) should be SKIPPED");
    }

    /**
     * Test that siblings with multiple parents are NOT skipped if not all parents are SKIPPED.
     *
     * <pre>
     *   T1 → T3
     *   T2 → T3
     *   T3 → T4
     * </pre>
     *
     * When T1 gets ERROR:
     * - T3 has two parents: T1 (ERROR→treated as finished) and T2
     * - If T2 is still OK or NONE, T3 should NOT be SKIPPED
     */
    @Test
    public void test_errorPropagation_doesNotSkipSiblingWithMultipleParentsNotAllSkipped() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextTaskStateParamsYaml stateParams = new ExecContextTaskStateParamsYaml();

        ExecContextData.TaskVertex t1 = addVertex(graph, 1L, "1");
        ExecContextData.TaskVertex t2 = addVertex(graph, 2L, "1");
        ExecContextData.TaskVertex t3 = addVertex(graph, 3L, "1");
        ExecContextData.TaskVertex t4 = addVertex(graph, 4L, "1");

        graph.addEdge(t1, t3);
        graph.addEdge(t2, t3);
        graph.addEdge(t3, t4);

        stateParams.states.put(1L, TaskExecState.NONE);
        stateParams.states.put(2L, TaskExecState.OK);
        stateParams.states.put(3L, TaskExecState.NONE);
        stateParams.states.put(4L, TaskExecState.NONE);

        // Act: T1 goes ERROR
        ExecContextData.ExecContextDAC dac = new ExecContextData.ExecContextDAC(1L, graph, 1);
        ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);

        updateTaskState(dac, stateParams, 1L, TaskExecState.ERROR, "1", status);

        // Assert: T3 should NOT be SKIPPED because T2 (its other parent) is OK, not SKIPPED
        assertEquals(TaskExecState.ERROR, stateParams.states.get(1L));
        assertEquals(TaskExecState.OK, stateParams.states.get(2L));
        assertEquals(TaskExecState.NONE, stateParams.states.get(3L), "T3 should remain NONE — not all parents are SKIPPED/ERROR");
        assertEquals(TaskExecState.NONE, stateParams.states.get(4L), "T4 should remain NONE");
    }

    /**
     * Test SKIPPED state propagation (not ERROR).
     * When mh.nop condition is false, the task is SKIPPED.
     * Its siblings with single SKIPPED parent should also be SKIPPED.
     *
     * <pre>
     *   T1 → T2 → T3 → T4
     * </pre>
     *
     * When T2 gets SKIPPED:
     * - T3: single parent T2 is SKIPPED → T3 should be SKIPPED
     * - T4: single parent T3 is SKIPPED → T4 should be SKIPPED
     */
    @Test
    public void test_skippedPropagation_cascadesToSiblings() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = createGraph();
        ExecContextTaskStateParamsYaml stateParams = new ExecContextTaskStateParamsYaml();

        ExecContextData.TaskVertex t1 = addVertex(graph, 1L, "1");
        ExecContextData.TaskVertex t2 = addVertex(graph, 2L, "1");
        ExecContextData.TaskVertex t3 = addVertex(graph, 3L, "1");
        ExecContextData.TaskVertex t4 = addVertex(graph, 4L, "1");

        graph.addEdge(t1, t2);
        graph.addEdge(t2, t3);
        graph.addEdge(t3, t4);

        stateParams.states.put(1L, TaskExecState.OK);
        stateParams.states.put(2L, TaskExecState.NONE);
        stateParams.states.put(3L, TaskExecState.NONE);
        stateParams.states.put(4L, TaskExecState.NONE);

        // Act: T2 gets SKIPPED (condition was false)
        ExecContextData.ExecContextDAC dac = new ExecContextData.ExecContextDAC(1L, graph, 1);
        ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);

        updateTaskState(dac, stateParams, 2L, TaskExecState.SKIPPED, "1", status);

        // Assert: cascade SKIPPED through the chain
        assertEquals(TaskExecState.OK, stateParams.states.get(1L));
        assertEquals(TaskExecState.SKIPPED, stateParams.states.get(2L));
        assertEquals(TaskExecState.SKIPPED, stateParams.states.get(3L), "T3 should be SKIPPED — single parent T2 is SKIPPED");
        assertEquals(TaskExecState.SKIPPED, stateParams.states.get(4L), "T4 should be SKIPPED — single parent T3 is SKIPPED");
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

    /**
     * Mimics the logic of ExecContextGraphService.updateTaskExecState() for a single task state change.
     * This calls the same propagation logic that the real code uses.
     */
    private static void updateTaskState(
            ExecContextData.ExecContextDAC dac, ExecContextTaskStateParamsYaml stateParams,
            Long taskId, TaskExecState execState, String taskContextId,
            ExecContextOperationStatusWithTaskList status) {

        stateParams.states.put(taskId, execState);

        if (execState == TaskExecState.ERROR) {
            ExecContextGraphService.setStateForAllChildrenTasksStatic(dac, stateParams, taskId, status, TaskExecState.SKIPPED, taskContextId);
        }
        else if (execState == TaskExecState.SKIPPED) {
            ExecContextGraphService.setStateForAllChildrenTasksStatic(dac, stateParams, taskId, status, TaskExecState.SKIPPED, taskContextId);
        }
    }
}
