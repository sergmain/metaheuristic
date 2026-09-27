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

package ai.metaheuristic.ai.yaml.exec_context_segment;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON params of an ExecContext segment (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 3): what is written is what is read
 * back - structure, task states, tries, variable-state entries - and the stored form is versioned JSON.
 */
@Execution(ExecutionMode.CONCURRENT)
public class ExecContextSegmentParamsTest {

    private static ExecContextSegmentParams sample() {
        ExecContextSegmentParams p = new ExecContextSegmentParams();

        ExecContextSegmentParams.Line root = new ExecContextSegmentParams.Line("1", null);
        root.tasks.add(new ExecContextSegmentParams.Vertex(1L, null));
        root.tasks.add(new ExecContextSegmentParams.Vertex(2L, null));
        root.tasks.add(new ExecContextSegmentParams.Vertex(3L, "terminal"));
        p.lines.add(root);

        ExecContextSegmentParams.Line line = new ExecContextSegmentParams.Line("1,2#1", 2L);
        line.tasks.add(new ExecContextSegmentParams.Vertex(4L, null));
        line.tasks.add(new ExecContextSegmentParams.Vertex(5L, null));
        p.lines.add(line);

        p.states.put(1L, EnumsApi.TaskExecState.OK);
        p.states.put(4L, EnumsApi.TaskExecState.SKIPPED);
        p.triesWasMade.put(4L, 1);

        ExecContextApiData.VariableState vs = new ExecContextApiData.VariableState();
        vs.taskId = 4L;
        vs.execContextId = 42L;
        vs.taskContextId = "1,2#1";
        vs.process = "lineHead";
        vs.functionCode = "mh.nop";
        p.variableStates.add(vs);
        return p;
    }

    @Test
    public void test_roundTrip_keepsStructure() {
        ExecContextSegmentParams back = ExecContextSegmentParamsUtils.BASE_UTILS.to(ExecContextSegmentParamsUtils.BASE_UTILS.toString(sample()));
        assertNotNull(back);
        assertEquals(2, back.lines.size());

        ExecContextSegmentParams.Line root = back.lines.get(0);
        assertEquals("1", root.ctx);
        assertNull(root.forkTaskId, "the root chain has no fork");
        assertEquals(List.of(1L, 2L, 3L), root.tasks.stream().map(v -> v.taskId).toList(), "chain order must survive");
        assertNull(root.tasks.get(0).tag);
        assertEquals("terminal", root.tasks.get(2).tag);

        ExecContextSegmentParams.Line line = back.lines.get(1);
        assertEquals("1,2#1", line.ctx);
        assertEquals(2L, line.forkTaskId);
        assertEquals(List.of(4L, 5L), line.tasks.stream().map(v -> v.taskId).toList());
    }

    @Test
    public void test_roundTrip_keepsStatesAndTries() {
        ExecContextSegmentParams back = ExecContextSegmentParamsUtils.BASE_UTILS.to(ExecContextSegmentParamsUtils.BASE_UTILS.toString(sample()));
        assertNotNull(back);
        assertEquals(2, back.states.size());
        assertEquals(EnumsApi.TaskExecState.OK, back.states.get(1L));
        assertEquals(EnumsApi.TaskExecState.SKIPPED, back.states.get(4L));
        assertEquals(1, back.triesWasMade.get(4L));
    }

    @Test
    public void test_roundTrip_keepsVariableStates() {
        ExecContextSegmentParams back = ExecContextSegmentParamsUtils.BASE_UTILS.to(ExecContextSegmentParamsUtils.BASE_UTILS.toString(sample()));
        assertNotNull(back);
        assertEquals(1, back.variableStates.size());
        ExecContextApiData.VariableState vs = back.variableStates.getFirst();
        assertEquals(4L, vs.taskId);
        assertEquals(42L, vs.execContextId);
        assertEquals("1,2#1", vs.taskContextId);
        assertEquals("lineHead", vs.process);
        assertEquals("mh.nop", vs.functionCode);
    }

    @Test
    public void test_emptyParams_roundTrip() {
        ExecContextSegmentParams back = ExecContextSegmentParamsUtils.BASE_UTILS.to(
                ExecContextSegmentParamsUtils.BASE_UTILS.toString(new ExecContextSegmentParams()));
        assertNotNull(back);
        assertTrue(back.lines.isEmpty());
        assertTrue(back.states.isEmpty());
        assertTrue(back.triesWasMade.isEmpty());
        assertTrue(back.variableStates.isEmpty());
    }

    @Test
    public void test_storedFormIsJsonWithVersionFirst() {
        String json = ExecContextSegmentParamsUtils.BASE_UTILS.toString(sample());
        assertTrue(json.startsWith("{\"version\":1"), "the stored form must be JSON starting with the version, was: " + json);
    }

    @Test
    public void test_readsHandWrittenV1() {
        String json = """
                {"version":1,"lines":[{"ctx":"1","forkTaskId":null,"tasks":[{"taskId":7,"tag":"terminal"}]},\
                {"ctx":"1,3#2","forkTaskId":7,"tasks":[{"taskId":8,"tag":null}]}],\
                "states":{"7":"OK","8":"NONE"},"triesWasMade":{"8":2},"variableStates":[]}""";
        ExecContextSegmentParams p = ExecContextSegmentParamsUtils.BASE_UTILS.to(json);
        assertNotNull(p);
        assertEquals(2, p.lines.size());
        assertEquals("terminal", p.lines.get(0).tasks.getFirst().tag);
        assertEquals(7L, p.lines.get(1).forkTaskId);
        assertEquals(8L, p.lines.get(1).tasks.getFirst().taskId);
        assertEquals(EnumsApi.TaskExecState.OK, p.states.get(7L));
        assertEquals(EnumsApi.TaskExecState.NONE, p.states.get(8L));
        assertEquals(2, p.triesWasMade.get(8L));
    }
}
