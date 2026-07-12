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

    // DSL v2 (EC #26): a recursive-group body process is grafted at runtime and is absent from the
    // static process topology (raw.processCodes). Characterization of current behavior on the legacy
    // path (no columnNames): getExecContextStateResult throws IllegalStateException "(idx==-1)" from
    // findOrAssignCol because the grafted task's processCode has no column.
    @Test
    public void test_graftedRecursiveGroupProcess_missingFromTopology() {
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(1L, 1111L, 1001L, "1", "mhdg-rg.init", "f-init", null, null),
                new ExecContextApiData.VariableState(2L, 1111L, 1001L, "1,2", "mhdg-rg.store-req", "f-store", null, null),
                new ExecContextApiData.VariableState(3L, 1111L, 1001L, "1,2,3|1#0", "mhdg-rg.store-req-r", "f-store", null, null),
                new ExecContextApiData.VariableState(4L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                1L, new TaskApiData.TaskState(1L, TaskExecState.OK.value, 0L, false, "1", "mhdg-rg.init"),
                2L, new TaskApiData.TaskState(2L, TaskExecState.OK.value, 0L, false, "1,2", "mhdg-rg.store-req"),
                3L, new TaskApiData.TaskState(3L, TaskExecState.OK.value, 0L, false, "1,2,3|1#0", "mhdg-rg.store-req-r"),
                4L, new TaskApiData.TaskState(4L, TaskExecState.OK.value, 0L, false, "1", "mh.finish")
        );

        // static process topology does NOT contain the grafted recursive-group body process "mhdg-rg.store-req-r"
        List<String> processCodes = List.of("mhdg-rg.init", "mhdg-rg.store-req", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "mhdg-rg", true, states);

        // legacy path (no columnNames) — must NOT throw; the grafted process code gets an appended
        // column after the topology columns, and its task is placed there.
        ExecContextApiData.ExecContextStateResult r = ExecContextUtils.getExecContextStateResult(26L, raw, true);

        assertNotNull(r);
        // 3 topology columns + 1 appended grafted column
        assertEquals(4, r.header.length);
        assertEquals("mhdg-rg.init", r.header[0].process);
        assertEquals("mhdg-rg.store-req", r.header[1].process);
        assertEquals("mh.finish", r.header[2].process);
        assertEquals("mhdg-rg.store-req-r", r.header[3].process);

        // contexts: "1", "1,2", "1,2,3|1#0"
        assertEquals(3, r.lines.length);
        assertEquals("1", r.lines[0].context);
        assertEquals("1,2", r.lines[1].context);
        assertEquals("1,2,3|1#0", r.lines[2].context);

        // row "1": init at col 0, finish at col 2
        assertFalse(r.lines[0].cells[0].empty);
        assertEquals(1L, r.lines[0].cells[0].taskId);
        assertFalse(r.lines[0].cells[2].empty);
        assertEquals(4L, r.lines[0].cells[2].taskId);

        // row "1,2": store-req at col 1
        assertFalse(r.lines[1].cells[1].empty);
        assertEquals(2L, r.lines[1].cells[1].taskId);

        // row "1,2,3|1#0": grafted store-req-r at the appended col 3
        assertFalse(r.lines[2].cells[3].empty);
        assertEquals(3L, r.lines[2].cells[3].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[2].cells[3].state);
    }
    // Green-1 (Characterization Test): documents CURRENT behavior when the real task DAG
    // (taskEdges) IS available. Today getExecContextStateResult ignores taskEdges for header
    // ordering, so the grafted recursive-group body process is appended AFTER mh.finish and
    // mh.finish is NOT the last column. This is the wrong order observed for ExecContext #26.
    @Test
    public void test_graftedRecursiveGroupProcess_orderedByTaskDag() {
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(1L, 1111L, 1001L, "1", "mhdg-rg.init", "f-init", null, null),
                new ExecContextApiData.VariableState(2L, 1111L, 1001L, "1,2", "mhdg-rg.store-req", "f-store", null, null),
                new ExecContextApiData.VariableState(3L, 1111L, 1001L, "1,2,3|1#0", "mhdg-rg.store-req-r", "f-store", null, null),
                new ExecContextApiData.VariableState(4L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                1L, new TaskApiData.TaskState(1L, TaskExecState.OK.value, 0L, false, "1", "mhdg-rg.init"),
                2L, new TaskApiData.TaskState(2L, TaskExecState.OK.value, 0L, false, "1,2", "mhdg-rg.store-req"),
                3L, new TaskApiData.TaskState(3L, TaskExecState.OK.value, 0L, false, "1,2,3|1#0", "mhdg-rg.store-req-r"),
                4L, new TaskApiData.TaskState(4L, TaskExecState.OK.value, 0L, false, "1", "mh.finish")
        );

        // static process topology does NOT contain the grafted recursive-group body process "mhdg-rg.store-req-r"
        List<String> processCodes = List.of("mhdg-rg.init", "mhdg-rg.store-req", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "mhdg-rg", true, states);

        // real runtime task DAG: init(1) -> store-req(2) -> store-req-r(3) -> mh.finish(4)
        raw.taskEdges = List.of(new long[]{1L, 2L}, new long[]{2L, 3L}, new long[]{3L, 4L});

        ExecContextApiData.ExecContextStateResult r = ExecContextUtils.getExecContextStateResult(26L, raw, true);

        assertNotNull(r);
        assertEquals(4, r.header.length);

        // DESIRED order: columns follow the runtime task DAG; grafted store-req-r comes before mh.finish; mh.finish is LAST
        assertEquals("mhdg-rg.init", r.header[0].process);
        assertEquals("mhdg-rg.store-req", r.header[1].process);
        assertEquals("mhdg-rg.store-req-r", r.header[2].process);
        assertEquals("mh.finish", r.header[3].process);
    }
    // Green-1 (Characterization Test): a STATIC process that produced no task (a graft point such as
    // mh.graft.req-rung, present in the static topology but never materialised as a task) is currently
    // shoved to the very END of the header by the "append leftover static processes last" step. This is
    // the wrong order observed for ExecContext #30 where mh.graft.req-rung ended up as the last column.
    @Test
    public void test_staticGraftPointWithoutTask_keepsStaticPosition() {
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(10L, 1111L, 1001L, "1", "p-open", "f-open", null, null),
                new ExecContextApiData.VariableState(11L, 1111L, 1001L, "1", "p-batch", "f-batch", null, null),
                new ExecContextApiData.VariableState(12L, 1111L, 1001L, "1", "p-post", "f-post", null, null),
                new ExecContextApiData.VariableState(13L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null),
                new ExecContextApiData.VariableState(20L, 1111L, 1001L, "1,2#1", "p-store", "f-store", null, null),
                new ExecContextApiData.VariableState(21L, 1111L, 1001L, "1,2#1", "p-set", "f-set", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                10L, new TaskApiData.TaskState(10L, TaskExecState.OK.value, 0L, false, "1", "p-open"),
                11L, new TaskApiData.TaskState(11L, TaskExecState.OK.value, 0L, false, "1", "p-batch"),
                12L, new TaskApiData.TaskState(12L, TaskExecState.OK.value, 0L, false, "1", "p-post"),
                13L, new TaskApiData.TaskState(13L, TaskExecState.OK.value, 0L, false, "1", "mh.finish"),
                20L, new TaskApiData.TaskState(20L, TaskExecState.OK.value, 0L, false, "1,2#1", "p-store"),
                21L, new TaskApiData.TaskState(21L, TaskExecState.OK.value, 0L, false, "1,2#1", "p-set")
        );

        // static topology contains the graft point "mh.graft.req-rung" (between p-batch and p-post) which
        // produces NO task; the grafted body p-store/p-set are absent from the static topology
        List<String> processCodes = List.of("p-open", "p-batch", "mh.graft.req-rung", "p-post", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "mhdg-rg", true, states);

        // real runtime task DAG (mirrors EC #30):
        //   p-open(10) -> p-batch(11) -> p-post(12) -> mh.finish(13)
        //   p-batch(11) -> p-store(20) -> p-set(21) -> p-post(12)
        raw.taskEdges = List.of(
                new long[]{10L, 11L}, new long[]{11L, 12L}, new long[]{12L, 13L},
                new long[]{11L, 20L}, new long[]{20L, 21L}, new long[]{21L, 12L});

        ExecContextApiData.ExecContextStateResult r = ExecContextUtils.getExecContextStateResult(30L, raw, true);

        assertNotNull(r);
        assertEquals(7, r.header.length);

        // CURRENT (buggy) order: the task-less static graft point is appended LAST
        // DESIRED order: the task-less static graft point keeps its authored static position
        // (immediately before p-post, its static successor) instead of being appended last
        assertEquals("p-open", r.header[0].process);
        assertEquals("p-batch", r.header[1].process);
        assertEquals("p-store", r.header[2].process);
        assertEquals("p-set", r.header[3].process);
        assertEquals("mh.graft.req-rung", r.header[4].process);
        assertEquals("p-post", r.header[5].process);
        assertEquals("mh.finish", r.header[6].process);
    }
    // Green-1 (Characterization Test): a STATIC control process (here the graft node
    // "mh.graft.req-rung-0.50", present in raw.processCodes) that is a high-task-id LEAF in the real
    // task DAG. The current task-DAG topological ordering floats that leaf to the very end, AFTER
    // mh.finish — the wrong position reported for ExecContext #26. The static topology already places
    // it correctly (between batch-lin and post-processing), so ordering must not move it.
    @Test
    public void test_staticGraftLeafProcess_notPlacedLast() {
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(10L, 1111L, 1001L, "1", "mhdg-rg.batch-lin", "f", null, null),
                new ExecContextApiData.VariableState(11L, 1111L, 1001L, "1,2", "mhdg-rg.store-req-r", "f", null, null),
                new ExecContextApiData.VariableState(12L, 1111L, 1001L, "1", "mhdg-rg.post-processing", "f", null, null),
                new ExecContextApiData.VariableState(13L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null),
                new ExecContextApiData.VariableState(99L, 1111L, 1001L, "1", "mh.graft.req-rung-0.50", "mh.graft", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                10L, new TaskApiData.TaskState(10L, TaskExecState.OK.value, 0L, false, "1", "mhdg-rg.batch-lin"),
                11L, new TaskApiData.TaskState(11L, TaskExecState.OK.value, 0L, false, "1,2", "mhdg-rg.store-req-r"),
                12L, new TaskApiData.TaskState(12L, TaskExecState.OK.value, 0L, false, "1", "mhdg-rg.post-processing"),
                13L, new TaskApiData.TaskState(13L, TaskExecState.OK.value, 0L, false, "1", "mh.finish"),
                99L, new TaskApiData.TaskState(99L, TaskExecState.OK.value, 0L, false, "1", "mh.graft.req-rung-0.50")
        );

        // static topology contains graft (authored between batch-lin and post-processing); the grafted
        // recursive-group body process "mhdg-rg.store-req-r" is absent from it
        List<String> processCodes = List.of("mhdg-rg.batch-lin", "mh.graft.req-rung-0.50", "mhdg-rg.post-processing", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "mhdg-rg", true, states);

        // real task DAG: batch-lin(10) -> store-req-r(11) -> post(12) -> finish(13);
        // graft(99) is a high-task-id LEAF hanging off batch-lin (marks the graft point, no descendants)
        raw.taskEdges = List.of(new long[]{10L, 11L}, new long[]{11L, 12L}, new long[]{12L, 13L}, new long[]{10L, 99L});

        ExecContextApiData.ExecContextStateResult r = ExecContextUtils.getExecContextStateResult(26L, raw, true);

        assertNotNull(r);
        assertEquals(5, r.header.length);

        // DESIRED order: static topology keeps graft in its authored position (grafted store-req-r
        // is inserted after its nearest static ancestor batch-lin); graft is NOT last, mh.finish is last
        assertEquals("mhdg-rg.batch-lin", r.header[0].process);
        assertEquals("mhdg-rg.store-req-r", r.header[1].process);
        assertEquals("mh.graft.req-rung-0.50", r.header[2].process);
        assertEquals("mhdg-rg.post-processing", r.header[3].process);
        assertEquals("mh.finish", r.header[4].process);
    }
}
