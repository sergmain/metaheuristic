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
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Serge
 * Date: 11/10/2021
 * Time: 6:30 PM
 */
@Execution(CONCURRENT)
class ExecContextGraphServiceTest {

    @Test
    public void test_findAllForAssigning() throws IOException {
        ExecContextGraph ecg = new ExecContextGraph();
        ecg.id = 2908L;
        ecg.execContextId = 42L;
        ecg.version = 2;
        ecg.setParams( IOUtils.resourceToString("/test_data/exec_context_graph/exec-context-graph.yaml", StandardCharsets.UTF_8) );

        ExecContextTaskState ects = new ExecContextTaskState();
        ects.id = 2908L;
        ects.execContextId = 42L;
        ects.setParams(IOUtils.resourceToString("/test_data/exec_context_graph/exec_context_task_state.yaml", StandardCharsets.UTF_8));


        List<ExecContextData.TaskVertex> vertices = ExecContextGraphService.findAllForAssigning(ecg, ects, true);

        System.out.println(vertices);

        assertEquals(2, vertices.size());
        assertTrue(vertices.contains(new ExecContextData.TaskVertex(978308L)));
        assertTrue(vertices.contains(new ExecContextData.TaskVertex(978189L)));
    }

    @Test
    public void test_findAllForAssigning_1() throws IOException {
        ExecContextGraph ecg = new ExecContextGraph();
        ecg.id = 2908L;
        ecg.execContextId = 42L;
        ecg.version = 2;
        ecg.setParams( IOUtils.resourceToString("/test_data/exec_context_graph_1/exec-context-graph.yaml", StandardCharsets.UTF_8) );

        ExecContextTaskState ects = new ExecContextTaskState();
        ects.id = 2908L;
        ects.execContextId = 42L;
        ects.setParams(IOUtils.resourceToString("/test_data/exec_context_graph_1/exec_context_task_state.yaml", StandardCharsets.UTF_8));


        List<ExecContextData.TaskVertex> vertices = ExecContextGraphService.findAllForAssigning(ecg, ects, true);

        System.out.println(vertices);

        assertTrue(vertices.isEmpty());
    }

    @Test
    public void test_isDescendantContext() {
        // Production scenario
        // Wrapper task ctx: "1,2,5|1#1" (nopObjectivesWrapper instance #1)
        // Direct child ctx: "1,2,5,6|1|1#0" (nopObjectives — layer 1 below wrapper)
        // Grandchild ctx:   "1,2,5,6,7|1|1|0#0" (evaluateObjective — layer 2 below wrapper)

        String wrapperCtx = "1,2,5|1#1";

        // Direct child — one level down from wrapper
        assertTrue(ExecContextGraphService.isDescendantContext("1,2,5,6|1|1#0", wrapperCtx));

        // Grandchild — two levels down from wrapper
        assertTrue(ExecContextGraphService.isDescendantContext("1,2,5,6,7|1|1|0#0", wrapperCtx));

        // mh.finish at root level — not a descendant of the wrapper
        assertFalse(ExecContextGraphService.isDescendantContext("1", wrapperCtx));

        // Sibling wrapper instance — not a descendant
        assertFalse(ExecContextGraphService.isDescendantContext("1,2,5|1#2", wrapperCtx));

        // Child of a sibling wrapper instance — not a descendant
        assertFalse(ExecContextGraphService.isDescendantContext("1,2,5,6|1|2#0", wrapperCtx));

        // Parent of the wrapper — not a descendant
        assertFalse(ExecContextGraphService.isDescendantContext("1,2#1", wrapperCtx));

        // The wrapper itself — not a descendant of itself
        assertFalse(ExecContextGraphService.isDescendantContext("1,2,5|1#1", wrapperCtx));

        // Top-level context
        assertFalse(ExecContextGraphService.isDescendantContext("1,2", wrapperCtx));

        // Simpler scenario: wrapper at "1,2#1", child at "1,2,3|1#0"
        assertTrue(ExecContextGraphService.isDescendantContext("1,2,3|1#0", "1,2#1"));

        // Even deeper: 4 levels
        assertTrue(ExecContextGraphService.isDescendantContext("1,2,5,6,7,8|1|1|0|0#0", wrapperCtx));
    }

    @Test
    public void test_findAllForAssigning_deadAncestorNotAssignable() {
        // A grafted line, linear: 100(root, OK) -> 101(head, ERROR) -> 102(downstream, NONE) -> 103(mh.finish, leaf, NONE)
        // #102 is a NONE task whose grafted head #101 terminally ERRORed - a dead/broken upstream.
        // Such a task must NEVER be handed out for assigning/execution.
        String graphYaml = """
            graph: |
              strict digraph G {
                100 [ ctxid="1" ];
                101 [ ctxid="1" ];
                102 [ ctxid="1" ];
                103 [ ctxid="1" ];
                100 -> 101;
                101 -> 102;
                102 -> 103;
              }
            version: 1
            """;
        String stateYaml = """
            states:
              100: OK
              101: ERROR
              102: NONE
              103: NONE
            version: 1
            """;

        ExecContextGraph ecg = new ExecContextGraph();
        ecg.id = 2908L;
        ecg.execContextId = 42L;
        ecg.version = 2;
        ecg.setParams(graphYaml);

        ExecContextTaskState ects = new ExecContextTaskState();
        ects.id = 2908L;
        ects.execContextId = 42L;
        ects.setParams(stateYaml);

        List<ExecContextData.TaskVertex> vertices = ExecContextGraphService.findAllForAssigning(ecg, ects, true);

        System.out.println(vertices);

        // Green-1 (characterization of current BUGGY behavior): the dead-branch task #102 IS returned as
        // assignable, because isParentFullyProcessed() treats an ERROR ancestor as a "finished" (ready) parent.
        assertFalse(vertices.contains(new ExecContextData.TaskVertex(102L)),
                "dead-ancestor task #102 (upstream #101 is ERROR) must NOT be handed out for assigning");
    }

    @Test
    public void test_findAllForAssigning_convergenceAfterSkippedConditionalBranchIsAssignable() {
        // Mirrors the mhdg-rg req-rung shape: a nop-wrapper (OK) whose conditional child nop-objectives is
        // SKIPPED (hasObjectives=false), rejoining the sequential sibling resolve-requirement-status:
        //   200(open,OK) -> 201(nop-wrapper,OK)
        //   201 -> 202(nop-objectives, SKIPPED)          [conditional branch, 'when' false]
        //   201 -> 203(resolve-requirement-status, NONE) [sequential sibling]
        //   202 -> 203                                    [conditional branch rejoins here]
        //   203 -> 204(mh.finish, leaf, NONE)
        // #203 is REACHABLE via its live OK parent #201, so it MUST be assignable even though its other
        // parent #202 was SKIPPED by a false 'when' condition.
        String graphYaml = """
            graph: |
              strict digraph G {
                200 [ ctxid="1" ];
                201 [ ctxid="1" ];
                202 [ ctxid="1" ];
                203 [ ctxid="1" ];
                204 [ ctxid="1" ];
                200 -> 201;
                201 -> 202;
                201 -> 203;
                202 -> 203;
                203 -> 204;
              }
            version: 1
            """;
        String stateYaml = """
            states:
              200: OK
              201: OK
              202: SKIPPED
              203: NONE
              204: NONE
            version: 1
            """;

        ExecContextGraph ecg = new ExecContextGraph();
        ecg.id = 2909L;
        ecg.execContextId = 42L;
        ecg.version = 2;
        ecg.setParams(graphYaml);

        ExecContextTaskState ects = new ExecContextTaskState();
        ects.id = 2909L;
        ects.execContextId = 42L;
        ects.setParams(stateYaml);

        List<ExecContextData.TaskVertex> vertices = ExecContextGraphService.findAllForAssigning(ecg, ects, true);

        // Green-1 (characterization of current BUGGY behavior): #203 is NOT handed out for assigning,
        // because isParentFullyProcessed() blocks it on the SKIPPED ancestor #202 - so the EC stalls.
        assertTrue(vertices.contains(new ExecContextData.TaskVertex(203L)),
                "convergence task #203 after a SKIPPED conditional branch must be assignable (reachable via OK parent #201)");
    }
}
