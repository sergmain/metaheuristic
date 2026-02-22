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

package ai.metaheuristic.ai.exec_context;

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.api.EnumsApi.SourceCodeType;
import static ai.metaheuristic.api.EnumsApi.TaskExecState;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Serge
 * Date: 8/20/2020
 * Time: 12:02 AM
 */
@Execution(CONCURRENT)
class TestExecContextState {

    @Test
    public void test() {

        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(100L, 1111L, 1001L, "1", "process-1", "function-1", null, null),
                new ExecContextApiData.VariableState(120L, 1111L, 1001L, "1,1", "process-2", "function-2", null, null),
                new ExecContextApiData.VariableState(140L, 1111L, 1001L, "1,1", "process-3", "function-3", null, null),
                new ExecContextApiData.VariableState(160L, 1111L, 1001L, "1,2", "process-2", "function-2", null, null),
                new ExecContextApiData.VariableState(180L, 1111L, 1001L, "1,2", "process-3", "function-3", null, null),
                new ExecContextApiData.VariableState(190L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                100L, new TaskApiData.TaskState(100L, TaskExecState.OK.value, 0L, false, "1", "process-1"),
                120L, new TaskApiData.TaskState(120L, TaskExecState.IN_PROGRESS.value, 0L, false, "1,1", "process-2"),
                140L, new TaskApiData.TaskState(140L, TaskExecState.NONE.value, 0L, false, "1,1", "process-3"),
                160L, new TaskApiData.TaskState(160L, TaskExecState.OK.value, 0L, false, "1,2", "process-2"),
                180L, new TaskApiData.TaskState(180L, TaskExecState.ERROR.value, 0L, false, "1,2", "process-3"),
                190L, new TaskApiData.TaskState(190L, TaskExecState.OK.value, 0L, false, "1", "mh.finish")
        );
        List<String> processCodes = List.of("process-1", "process-2", "process-3", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.batch, "test-source-code", true, states);
        ExecContextApiData.ExecContextStateResult r = ExecContextUtils.getExecContextStateResult(42L, raw, true);

        assertNotNull(r);
        assertNotNull(r.header);
        assertNotNull(r.lines);
        assertEquals(4, r.header.length);
        assertEquals(3, r.lines.length);

        assertEquals("process-1", r.header[0].process);
        assertEquals("process-2", r.header[1].process);
        assertEquals("process-3", r.header[2].process);
        assertEquals("mh.finish", r.header[3].process);

        assertEquals("1", r.lines[0].context);
        assertEquals("1,1", r.lines[1].context);
        assertEquals("1,2", r.lines[2].context);

        assertFalse(r.lines[0].cells[0].empty);
        assertTrue(r.lines[0].cells[1].empty);
        assertTrue(r.lines[0].cells[2].empty);
        assertFalse(r.lines[0].cells[3].empty);

        assertTrue(r.lines[1].cells[0].empty);
        assertFalse(r.lines[1].cells[1].empty);
        assertFalse(r.lines[1].cells[2].empty);
        assertTrue(r.lines[1].cells[3].empty);

        assertTrue(r.lines[2].cells[0].empty);
        assertFalse(r.lines[2].cells[1].empty);
        assertFalse(r.lines[2].cells[2].empty);
        assertTrue(r.lines[2].cells[3].empty);

        assertEquals(100L, r.lines[0].cells[0].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[0].cells[0].state);
        assertEquals("1", r.lines[0].cells[0].context);

        assertEquals(190L, r.lines[0].cells[3].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[0].cells[3].state);
        assertEquals("1", r.lines[0].cells[3].context);

        assertEquals(120L, r.lines[1].cells[1].taskId);
        assertEquals(TaskExecState.IN_PROGRESS.toString(), r.lines[1].cells[1].state);
        assertEquals("1,1", r.lines[1].cells[1].context);

        assertEquals(140L, r.lines[1].cells[2].taskId);
        assertEquals(TaskExecState.NONE.toString(), r.lines[1].cells[2].state);
        assertEquals("1,1", r.lines[1].cells[2].context);

        assertEquals(160L, r.lines[2].cells[1].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[2].cells[1].state);
        assertEquals("1,2", r.lines[2].cells[1].context);

        assertEquals(180L, r.lines[2].cells[2].taskId);
        assertEquals(TaskExecState.ERROR.toString(), r.lines[2].cells[2].state);
        assertEquals("1,2", r.lines[2].cells[2].context);


        System.out.println(Arrays.toString(r.header));
        for (ExecContextApiData.LineWithState line : r.lines) {
            System.out.println(line.context+", "+ Arrays.toString(line.cells));
        }
    }

    // P0: deeper contexts "1,2,3" and "1,2,3,4" with more process codes
    @Test
    public void test_deeperContexts() {
        // Simulates a 4-level deep requirement decomposition:
        // Level 1: top-level process
        // Level 1,1 / 1,2: first fan-out
        // Level 1,1,1 / 1,1,2: second fan-out (depth 3)
        // Level 1,1,1,1: depth 4
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(1L, 1111L, 1001L, "1", "p-init", "f-init", null, null),
                new ExecContextApiData.VariableState(2L, 1111L, 1001L, "1,1", "p-decompose", "f-decompose", null, null),
                new ExecContextApiData.VariableState(3L, 1111L, 1001L, "1,2", "p-decompose", "f-decompose", null, null),
                new ExecContextApiData.VariableState(4L, 1111L, 1001L, "1,1,1", "p-analyze", "f-analyze", null, null),
                new ExecContextApiData.VariableState(5L, 1111L, 1001L, "1,1,2", "p-analyze", "f-analyze", null, null),
                new ExecContextApiData.VariableState(6L, 1111L, 1001L, "1,1,1,1", "p-validate", "f-validate", null, null),
                new ExecContextApiData.VariableState(7L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                1L, new TaskApiData.TaskState(1L, TaskExecState.OK.value, 0L, false, "1", "p-init"),
                2L, new TaskApiData.TaskState(2L, TaskExecState.OK.value, 0L, false, "1,1", "p-decompose"),
                3L, new TaskApiData.TaskState(3L, TaskExecState.IN_PROGRESS.value, 0L, false, "1,2", "p-decompose"),
                4L, new TaskApiData.TaskState(4L, TaskExecState.OK.value, 0L, false, "1,1,1", "p-analyze"),
                5L, new TaskApiData.TaskState(5L, TaskExecState.NONE.value, 0L, false, "1,1,2", "p-analyze"),
                6L, new TaskApiData.TaskState(6L, TaskExecState.ERROR.value, 0L, false, "1,1,1,1", "p-validate"),
                7L, new TaskApiData.TaskState(7L, TaskExecState.OK.value, 0L, false, "1", "mh.finish")
        );

        List<String> processCodes = List.of("p-init", "p-decompose", "p-analyze", "p-validate", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "deep-source-code", true, states);
        ExecContextApiData.ExecContextStateResult r = ExecContextUtils.getExecContextStateResult(42L, raw, true);

        assertNotNull(r);
        assertEquals(5, r.header.length);
        // contexts: "1", "1,1", "1,1,1", "1,1,1,1", "1,1,2", "1,2" — 6 unique contexts
        assertEquals(6, r.lines.length);

        // verify header order matches processCodes
        assertEquals("p-init", r.header[0].process);
        assertEquals("p-decompose", r.header[1].process);
        assertEquals("p-analyze", r.header[2].process);
        assertEquals("p-validate", r.header[3].process);
        assertEquals("mh.finish", r.header[4].process);

        // verify context sorting: "1" < "1,1" < "1,1,1" < "1,1,1,1" < "1,1,2" < "1,2"
        assertEquals("1", r.lines[0].context);
        assertEquals("1,1", r.lines[1].context);
        assertEquals("1,1,1", r.lines[2].context);
        assertEquals("1,1,1,1", r.lines[3].context);
        assertEquals("1,1,2", r.lines[4].context);
        assertEquals("1,2", r.lines[5].context);

        // line "1": p-init(task1) at col 0, mh.finish(task7) at col 4, rest empty
        assertFalse(r.lines[0].cells[0].empty);
        assertEquals(1L, r.lines[0].cells[0].taskId);
        assertTrue(r.lines[0].cells[1].empty);
        assertTrue(r.lines[0].cells[2].empty);
        assertTrue(r.lines[0].cells[3].empty);
        assertFalse(r.lines[0].cells[4].empty);
        assertEquals(7L, r.lines[0].cells[4].taskId);

        // line "1,1": p-decompose(task2) at col 1
        assertTrue(r.lines[1].cells[0].empty);
        assertFalse(r.lines[1].cells[1].empty);
        assertEquals(2L, r.lines[1].cells[1].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[1].cells[1].state);

        // line "1,1,1": p-analyze(task4) at col 2
        assertFalse(r.lines[2].cells[2].empty);
        assertEquals(4L, r.lines[2].cells[2].taskId);

        // line "1,1,1,1": p-validate(task6) at col 3
        assertFalse(r.lines[3].cells[3].empty);
        assertEquals(6L, r.lines[3].cells[3].taskId);
        assertEquals(TaskExecState.ERROR.toString(), r.lines[3].cells[3].state);

        // line "1,1,2": p-analyze(task5) at col 2
        assertFalse(r.lines[4].cells[2].empty);
        assertEquals(5L, r.lines[4].cells[2].taskId);
        assertEquals(TaskExecState.NONE.toString(), r.lines[4].cells[2].state);

        // line "1,2": p-decompose(task3) at col 1
        assertFalse(r.lines[5].cells[1].empty);
        assertEquals(3L, r.lines[5].cells[1].taskId);
        assertEquals(TaskExecState.IN_PROGRESS.toString(), r.lines[5].cells[1].state);
    }

    // P0: dedicated test for findOrAssignCol
    @Test
    public void test_findOrAssignCol() {
        // pre-populated headers — exact match
        ExecContextApiData.ColumnHeader[] headers = new ExecContextApiData.ColumnHeader[]{
                new ExecContextApiData.ColumnHeader("p1", "f1"),
                new ExecContextApiData.ColumnHeader("p2", "f2"),
                new ExecContextApiData.ColumnHeader("p3", "f3")
        };
        assertEquals(0, ExecContextUtils.findOrAssignCol(headers, "p1"));
        assertEquals(1, ExecContextUtils.findOrAssignCol(headers, "p2"));
        assertEquals(2, ExecContextUtils.findOrAssignCol(headers, "p3"));
    }

    @Test
    public void test_findOrAssignCol_assignsToFirstNull() {
        // headers with null slots — assigns to first available null
        ExecContextApiData.ColumnHeader[] headers = new ExecContextApiData.ColumnHeader[]{
                new ExecContextApiData.ColumnHeader("p1", "f1"),
                new ExecContextApiData.ColumnHeader(null, null),
                new ExecContextApiData.ColumnHeader(null, null)
        };
        int idx = ExecContextUtils.findOrAssignCol(headers, "p-new");
        assertEquals(1, idx);
        assertEquals("p-new", headers[1].process);
    }

    @Test
    public void test_findOrAssignCol_throwsWhenFull() {
        // all headers populated, process not found — should throw
        ExecContextApiData.ColumnHeader[] headers = new ExecContextApiData.ColumnHeader[]{
                new ExecContextApiData.ColumnHeader("p1", "f1"),
                new ExecContextApiData.ColumnHeader("p2", "f2")
        };
        assertThrows(IllegalStateException.class, () -> ExecContextUtils.findOrAssignCol(headers, "p-unknown"));
    }

    // P0: edge case — single context (no fan-out)
    @Test
    public void test_singleContext() {
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(1L, 1111L, 1001L, "1", "p-only", "f-only", null, null)
        );
        Map<Long, TaskApiData.TaskState> states = Map.of(
                1L, new TaskApiData.TaskState(1L, TaskExecState.OK.value, 0L, false, "1", "p-only")
        );
        List<String> processCodes = List.of("p-only");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "single-sc", true, states);
        ExecContextApiData.ExecContextStateResult r = ExecContextUtils.getExecContextStateResult(42L, raw, true);

        assertEquals(1, r.header.length);
        assertEquals(1, r.lines.length);
        assertEquals("1", r.lines[0].context);
        assertFalse(r.lines[0].cells[0].empty);
        assertEquals(1L, r.lines[0].cells[0].taskId);
    }

    // P1-5: target behavior — columnNames map present, columns defined by columnNames instead of processCodes
    @Test
    public void test_withColumnNames() {
        // Same data as original test, but with columnNames map defining columns dynamically
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(100L, 1111L, 1001L, "1", "process-1", "function-1", null, null),
                new ExecContextApiData.VariableState(120L, 1111L, 1001L, "1,1", "process-2", "function-2", null, null),
                new ExecContextApiData.VariableState(140L, 1111L, 1001L, "1,1", "process-3", "function-3", null, null),
                new ExecContextApiData.VariableState(160L, 1111L, 1001L, "1,2", "process-2", "function-2", null, null),
                new ExecContextApiData.VariableState(180L, 1111L, 1001L, "1,2", "process-3", "function-3", null, null),
                new ExecContextApiData.VariableState(190L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                100L, new TaskApiData.TaskState(100L, TaskExecState.OK.value, 0L, false, "1", "process-1"),
                120L, new TaskApiData.TaskState(120L, TaskExecState.IN_PROGRESS.value, 0L, false, "1,1", "process-2"),
                140L, new TaskApiData.TaskState(140L, TaskExecState.NONE.value, 0L, false, "1,1", "process-3"),
                160L, new TaskApiData.TaskState(160L, TaskExecState.OK.value, 0L, false, "1,2", "process-2"),
                180L, new TaskApiData.TaskState(180L, TaskExecState.ERROR.value, 0L, false, "1,2", "process-3"),
                190L, new TaskApiData.TaskState(190L, TaskExecState.OK.value, 0L, false, "1", "mh.finish")
        );

        List<String> processCodes = List.of("process-1", "process-2", "process-3", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.batch, "test-source-code", true, states);

        // columnNames defines column layout dynamically
        raw.columnNames = new java.util.LinkedHashMap<>();
        raw.columnNames.put(0, "Init");
        raw.columnNames.put(1, "Decompose");
        raw.columnNames.put(2, "Analyze");
        raw.columnNames.put(3, "Finish");

        ExecContextApiData.ExecContextStateResult r = ExecContextUtils.getExecContextStateResult(42L, raw, true);

        assertNotNull(r);
        // When columnNames is present, header should use columnNames display names
        assertEquals(4, r.header.length);
        assertEquals("Init", r.header[0].process);
        assertEquals("Decompose", r.header[1].process);
        assertEquals("Analyze", r.header[2].process);
        assertEquals("Finish", r.header[3].process);

        // Lines and cell placement should still work correctly
        assertEquals(3, r.lines.length);
        assertEquals("1", r.lines[0].context);
        assertEquals("1,1", r.lines[1].context);
        assertEquals("1,2", r.lines[2].context);

        // task placement should remain the same — processCode-to-column mapping still works
        assertFalse(r.lines[0].cells[0].empty);
        assertEquals(100L, r.lines[0].cells[0].taskId);
        assertFalse(r.lines[0].cells[3].empty);
        assertEquals(190L, r.lines[0].cells[3].taskId);
    }

    // Option 5d: dynamic sub-processes where TaskState.processCode contains function names, not SourceCode process codes
    @Test
    public void test_dynamicSubProcesses_functionNamesAsProcessCode() {
        // In dynamic sub-process scenario, tasks are created with processCode = function name
        // e.g. "fn-read-data", "fn-transform", "fn-store" instead of "process-1", "process-2", "process-3"
        // processCodes list and columnNames must match what's in TaskState.processCode

        // task 1: init task, processCode = function name "fn-read-data"
        // task 2: dynamic sub-process task, processCode = function name "fn-transform"
        // task 3: dynamic sub-process task SKIPPED, processCode = function name "fn-store" (no VariableState)
        // task 4: mh.finish

        // Only tasks 1, 2, 4 have VariableState; task 3 was SKIPPED before init
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(1L, 1111L, 1001L, "1", "fn-read-data", "fn-read-data", null, null),
                new ExecContextApiData.VariableState(2L, 1111L, 1001L, "1", "fn-transform", "fn-transform", null, null),
                new ExecContextApiData.VariableState(4L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        // All tasks in DB — task 3 is SKIPPED, no VariableState
        Map<Long, TaskApiData.TaskState> states = Map.of(
                1L, new TaskApiData.TaskState(1L, TaskExecState.OK.value, 0L, false, "1", "fn-read-data"),
                2L, new TaskApiData.TaskState(2L, TaskExecState.OK.value, 0L, false, "1", "fn-transform"),
                3L, new TaskApiData.TaskState(3L, TaskExecState.SKIPPED.value, 0L, false, "1", "fn-store"),
                4L, new TaskApiData.TaskState(4L, TaskExecState.OK.value, 0L, false, "1", "mh.finish")
        );

        // processCodes contains function names (as they appear in the dynamic process graph)
        List<String> processCodes = List.of("fn-read-data", "fn-transform", "fn-store", "mh.finish");

        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "dynamic-sc", true, states);

        // Option 5d: columnNames defines display names for columns
        raw.columnNames = new java.util.LinkedHashMap<>();
        raw.columnNames.put(0, "Read Data");
        raw.columnNames.put(1, "Transform");
        raw.columnNames.put(2, "Store");
        raw.columnNames.put(3, "Finish");

        ExecContextApiData.ExecContextStateResult r = ExecContextUtils.getExecContextStateResult(42L, raw, true);

        assertNotNull(r);
        assertEquals(4, r.header.length);
        assertEquals("Read Data", r.header[0].process);
        assertEquals("Transform", r.header[1].process);
        assertEquals("Store", r.header[2].process);
        assertEquals("Finish", r.header[3].process);

        assertEquals(1, r.lines.length);
        assertEquals("1", r.lines[0].context);

        // task 1 at col 0 — OK
        assertFalse(r.lines[0].cells[0].empty);
        assertEquals(1L, r.lines[0].cells[0].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[0].cells[0].state);

        // task 2 at col 1 — OK
        assertFalse(r.lines[0].cells[1].empty);
        assertEquals(2L, r.lines[0].cells[1].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[0].cells[1].state);

        // task 3 at col 2 — SKIPPED (no VariableState, placed via TaskState)
        assertFalse(r.lines[0].cells[2].empty);
        assertEquals(3L, r.lines[0].cells[2].taskId);
        assertEquals(TaskExecState.SKIPPED.toString(), r.lines[0].cells[2].state);

        // task 4 at col 3 — OK
        assertFalse(r.lines[0].cells[3].empty);
        assertEquals(4L, r.lines[0].cells[3].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[0].cells[3].state);
    }

    // Same as above but without columnNames (legacy path with findOrAssignCol)
    @Test
    public void test_dynamicSubProcesses_functionNames_legacyPath() {
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(1L, 1111L, 1001L, "1", "fn-read-data", "fn-read-data", null, null),
                new ExecContextApiData.VariableState(4L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        // task 2 and 3 have no VariableState (SKIPPED)
        Map<Long, TaskApiData.TaskState> states = Map.of(
                1L, new TaskApiData.TaskState(1L, TaskExecState.OK.value, 0L, false, "1", "fn-read-data"),
                2L, new TaskApiData.TaskState(2L, TaskExecState.SKIPPED.value, 0L, false, "1", "fn-transform"),
                3L, new TaskApiData.TaskState(3L, TaskExecState.SKIPPED.value, 0L, false, "1", "fn-store"),
                4L, new TaskApiData.TaskState(4L, TaskExecState.OK.value, 0L, false, "1", "mh.finish")
        );

        List<String> processCodes = List.of("fn-read-data", "fn-transform", "fn-store", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "dynamic-legacy", true, states);

        // No columnNames — legacy path
        ExecContextApiData.ExecContextStateResult r = ExecContextUtils.getExecContextStateResult(42L, raw, true);

        assertNotNull(r);
        assertEquals(4, r.header.length);
        assertEquals(1, r.lines.length);

        // task 1 — OK
        assertFalse(r.lines[0].cells[0].empty);
        assertEquals(1L, r.lines[0].cells[0].taskId);

        // task 2 — SKIPPED, placed via TaskState.processCode
        assertFalse(r.lines[0].cells[1].empty);
        assertEquals(2L, r.lines[0].cells[1].taskId);
        assertEquals(TaskExecState.SKIPPED.toString(), r.lines[0].cells[1].state);

        // task 3 — SKIPPED
        assertFalse(r.lines[0].cells[2].empty);
        assertEquals(3L, r.lines[0].cells[2].taskId);
        assertEquals(TaskExecState.SKIPPED.toString(), r.lines[0].cells[2].state);

        // task 4 — OK
        assertFalse(r.lines[0].cells[3].empty);
        assertEquals(4L, r.lines[0].cells[3].taskId);
    }
}
