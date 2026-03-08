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
        // Production scenario from MHDG-RG:
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
}
