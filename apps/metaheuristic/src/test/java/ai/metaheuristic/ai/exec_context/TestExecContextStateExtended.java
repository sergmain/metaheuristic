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

import java.util.List;
import java.util.Map;

import static ai.metaheuristic.api.EnumsApi.SourceCodeType;
import static ai.metaheuristic.api.EnumsApi.TaskExecState;
import ai.metaheuristic.api.EnumsApi;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended tests for ExecContextUtils.getExecContextStateResult()
 * capturing baseline behavior before Option 5d refactoring.
 *
 * @author Sergio Lissner
 * Date: 2/16/2026
 */
public class TestExecContextStateExtended {

    // === P0: findOrAssignCol dedicated tests ===

    @Test
    public void test_findOrAssignCol_matchExistingProcess() {
        ExecContextApiData.ColumnHeader[] headers = new ExecContextApiData.ColumnHeader[]{
                new ExecContextApiData.ColumnHeader("p-1", "f-1"),
                new ExecContextApiData.ColumnHeader("p-2", "f-2"),
                new ExecContextApiData.ColumnHeader("p-3", "f-3")
        };

        assertEquals(0, ExecContextUtils.findOrAssignCol(headers, "p-1"));
        assertEquals(1, ExecContextUtils.findOrAssignCol(headers, "p-2"));
        assertEquals(2, ExecContextUtils.findOrAssignCol(headers, "p-3"));
    }

    @Test
    public void test_findOrAssignCol_assignToFirstNullSlot() {
        ExecContextApiData.ColumnHeader[] headers = new ExecContextApiData.ColumnHeader[]{
                new ExecContextApiData.ColumnHeader("p-1", "f-1"),
                new ExecContextApiData.ColumnHeader(null, null),
                new ExecContextApiData.ColumnHeader(null, null)
        };

        int idx = ExecContextUtils.findOrAssignCol(headers, "p-new");
        assertEquals(1, idx);
        assertEquals("p-new", headers[1].process);
    }

    @Test
    public void test_findOrAssignCol_throwsWhenNoSlots() {
        ExecContextApiData.ColumnHeader[] headers = new ExecContextApiData.ColumnHeader[]{
                new ExecContextApiData.ColumnHeader("p-1", "f-1"),
                new ExecContextApiData.ColumnHeader("p-2", "f-2")
        };

        assertThrows(IllegalStateException.class, () -> ExecContextUtils.findOrAssignCol(headers, "p-unknown"));
    }

    @Test
    public void test_findOrAssignCol_assignMultipleSequentially() {
        ExecContextApiData.ColumnHeader[] headers = new ExecContextApiData.ColumnHeader[]{
                new ExecContextApiData.ColumnHeader(null, null),
                new ExecContextApiData.ColumnHeader(null, null),
                new ExecContextApiData.ColumnHeader(null, null)
        };

        assertEquals(0, ExecContextUtils.findOrAssignCol(headers, "a"));
        assertEquals(1, ExecContextUtils.findOrAssignCol(headers, "b"));
        assertEquals(2, ExecContextUtils.findOrAssignCol(headers, "c"));

        // now all assigned, should find by match
        assertEquals(0, ExecContextUtils.findOrAssignCol(headers, "a"));
        assertEquals(1, ExecContextUtils.findOrAssignCol(headers, "b"));
        assertEquals(2, ExecContextUtils.findOrAssignCol(headers, "c"));
    }

    // Test deep context hierarchy (4 levels) - baseline for dynamic depth scenarios
    @Test
    public void test_deepContextHierarchy() {
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(1L, 1111L, 1001L, "1", "p-1", "f-1", null, null),
                new ExecContextApiData.VariableState(2L, 1111L, 1001L, "1,2", "p-2", "f-2", null, null),
                new ExecContextApiData.VariableState(3L, 1111L, 1001L, "1,2,3", "p-3", "f-3", null, null),
                new ExecContextApiData.VariableState(4L, 1111L, 1001L, "1,2,3,4", "p-4", "f-4", null, null),
                new ExecContextApiData.VariableState(5L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                1L, new TaskApiData.TaskState(1L, TaskExecState.OK.value, 0L, false),
                2L, new TaskApiData.TaskState(2L, TaskExecState.OK.value, 0L, false),
                3L, new TaskApiData.TaskState(3L, TaskExecState.IN_PROGRESS.value, 0L, false),
                4L, new TaskApiData.TaskState(4L, TaskExecState.NONE.value, 0L, false),
                5L, new TaskApiData.TaskState(5L, TaskExecState.NONE.value, 0L, false)
        );

        List<String> processCodes = List.of("p-1", "p-2", "p-3", "p-4", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "test-deep", true, states);

        ExecContextApiData.ExecContextStateResult result = ExecContextUtils.getExecContextStateResult(42L, raw, true);

        assertNotNull(result);
        assertEquals(5, result.header.length);
        assertEquals(4, result.lines.length);

        // verify column order
        assertEquals("p-1", result.header[0].process);
        assertEquals("p-2", result.header[1].process);
        assertEquals("p-3", result.header[2].process);
        assertEquals("p-4", result.header[3].process);
        assertEquals("mh.finish", result.header[4].process);

        // verify row order (sorted by context)
        assertEquals("1", result.lines[0].context);
        assertEquals("1,2", result.lines[1].context);
        assertEquals("1,2,3", result.lines[2].context);
        assertEquals("1,2,3,4", result.lines[3].context);

        // row "1" has p-1 and mh.finish, rest empty
        assertFalse(result.lines[0].cells[0].empty);
        assertTrue(result.lines[0].cells[1].empty);
        assertTrue(result.lines[0].cells[2].empty);
        assertTrue(result.lines[0].cells[3].empty);
        assertFalse(result.lines[0].cells[4].empty);

        // row "1,2" has only p-2
        assertTrue(result.lines[1].cells[0].empty);
        assertFalse(result.lines[1].cells[1].empty);
        assertTrue(result.lines[1].cells[2].empty);
        assertTrue(result.lines[1].cells[3].empty);
        assertTrue(result.lines[1].cells[4].empty);

        // row "1,2,3" has only p-3
        assertTrue(result.lines[2].cells[0].empty);
        assertTrue(result.lines[2].cells[1].empty);
        assertFalse(result.lines[2].cells[2].empty);
        assertTrue(result.lines[2].cells[3].empty);
        assertTrue(result.lines[2].cells[4].empty);

        // row "1,2,3,4" has only p-4
        assertTrue(result.lines[3].cells[0].empty);
        assertTrue(result.lines[3].cells[1].empty);
        assertTrue(result.lines[3].cells[2].empty);
        assertFalse(result.lines[3].cells[3].empty);
        assertTrue(result.lines[3].cells[4].empty);

        // verify task placement
        assertEquals(1L, result.lines[0].cells[0].taskId);
        assertEquals(2L, result.lines[1].cells[1].taskId);
        assertEquals(3L, result.lines[2].cells[2].taskId);
        assertEquals(4L, result.lines[3].cells[3].taskId);
        assertEquals(5L, result.lines[0].cells[4].taskId);
    }

    // Test wide fan-out with #N suffixes - the BatchLineSplitter multiplication pattern
    @Test
    public void test_wideFanOutWithInstanceSuffix() {
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(10L, 1111L, 1001L, "1", "splitter", "mh.batch-line-splitter", null, null),
                new ExecContextApiData.VariableState(11L, 1111L, 1001L, "1,2#1", "worker", "cc-agent", null, null),
                new ExecContextApiData.VariableState(12L, 1111L, 1001L, "1,2#2", "worker", "cc-agent", null, null),
                new ExecContextApiData.VariableState(13L, 1111L, 1001L, "1,2#3", "worker", "cc-agent", null, null),
                new ExecContextApiData.VariableState(20L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                10L, new TaskApiData.TaskState(10L, TaskExecState.OK.value, 0L, false),
                11L, new TaskApiData.TaskState(11L, TaskExecState.OK.value, 0L, false),
                12L, new TaskApiData.TaskState(12L, TaskExecState.IN_PROGRESS.value, 0L, false),
                13L, new TaskApiData.TaskState(13L, TaskExecState.NONE.value, 0L, false),
                20L, new TaskApiData.TaskState(20L, TaskExecState.NONE.value, 0L, false)
        );

        List<String> processCodes = List.of("splitter", "worker", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "test-fanout", true, states);

        ExecContextApiData.ExecContextStateResult result = ExecContextUtils.getExecContextStateResult(42L, raw, true);

        assertNotNull(result);
        assertEquals(3, result.header.length);
        assertEquals(4, result.lines.length);

        assertEquals("splitter", result.header[0].process);
        assertEquals("worker", result.header[1].process);
        assertEquals("mh.finish", result.header[2].process);

        // row "1" has splitter and mh.finish
        assertEquals("1", result.lines[0].context);
        assertFalse(result.lines[0].cells[0].empty);
        assertTrue(result.lines[0].cells[1].empty);
        assertFalse(result.lines[0].cells[2].empty);

        // rows "1,2#1", "1,2#2", "1,2#3" each have worker in column 1
        assertEquals("1,2#1", result.lines[1].context);
        assertTrue(result.lines[1].cells[0].empty);
        assertFalse(result.lines[1].cells[1].empty);
        assertEquals(11L, result.lines[1].cells[1].taskId);

        assertEquals("1,2#2", result.lines[2].context);
        assertTrue(result.lines[2].cells[0].empty);
        assertFalse(result.lines[2].cells[1].empty);
        assertEquals(12L, result.lines[2].cells[1].taskId);

        assertEquals("1,2#3", result.lines[3].context);
        assertTrue(result.lines[3].cells[0].empty);
        assertFalse(result.lines[3].cells[1].empty);
        assertEquals(13L, result.lines[3].cells[1].taskId);
    }

    // Test same function code used in multiple process codes (the RG template pattern)
    @Test
    public void test_sameFunctionDifferentProcessCodes() {
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(1L, 1111L, 1001L, "1", "rg-level-1", "cc-agent", null, null),
                new ExecContextApiData.VariableState(2L, 1111L, 1001L, "1,2", "rg-level-2", "cc-agent", null, null),
                new ExecContextApiData.VariableState(3L, 1111L, 1001L, "1,2,3", "rg-level-3", "cc-agent", null, null),
                new ExecContextApiData.VariableState(4L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                1L, new TaskApiData.TaskState(1L, TaskExecState.OK.value, 0L, false),
                2L, new TaskApiData.TaskState(2L, TaskExecState.OK.value, 0L, false),
                3L, new TaskApiData.TaskState(3L, TaskExecState.IN_PROGRESS.value, 0L, false),
                4L, new TaskApiData.TaskState(4L, TaskExecState.NONE.value, 0L, false)
        );

        List<String> processCodes = List.of("rg-level-1", "rg-level-2", "rg-level-3", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "test-rg", true, states);

        ExecContextApiData.ExecContextStateResult result = ExecContextUtils.getExecContextStateResult(42L, raw, true);

        assertNotNull(result);
        assertEquals(4, result.header.length);
        assertEquals(3, result.lines.length);

        // column headers use processCode for both process and functionCode fields
        assertEquals("rg-level-1", result.header[0].functionCode);
        assertEquals("rg-level-2", result.header[1].functionCode);
        assertEquals("rg-level-3", result.header[2].functionCode);

        // but process codes are distinct
        assertEquals("rg-level-1", result.header[0].process);
        assertEquals("rg-level-2", result.header[1].process);
        assertEquals("rg-level-3", result.header[2].process);

        // each task goes to its own column
        assertFalse(result.lines[0].cells[0].empty);
        assertEquals(1L, result.lines[0].cells[0].taskId);

        assertFalse(result.lines[1].cells[1].empty);
        assertEquals(2L, result.lines[1].cells[1].taskId);

        assertFalse(result.lines[2].cells[2].empty);
        assertEquals(3L, result.lines[2].cells[2].taskId);
    }

    // Test fan-out combined with depth - realistic RG scenario
    @Test
    public void test_depthWithFanOut() {
        List<ExecContextApiData.VariableState> infos = List.of(
                // level 1: single task
                new ExecContextApiData.VariableState(1L, 1111L, 1001L, "1", "rg-analyze", "cc-agent", null, null),
                // level 2: fan-out to 2 instances
                new ExecContextApiData.VariableState(2L, 1111L, 1001L, "1,2#1", "rg-decompose", "cc-agent", null, null),
                new ExecContextApiData.VariableState(3L, 1111L, 1001L, "1,2#2", "rg-decompose", "cc-agent", null, null),
                // level 3: each instance fans out again
                new ExecContextApiData.VariableState(4L, 1111L, 1001L, "1,2,3#1", "rg-process", "cc-agent", null, null),
                new ExecContextApiData.VariableState(5L, 1111L, 1001L, "1,2,3#2", "rg-process", "cc-agent", null, null),
                // finish
                new ExecContextApiData.VariableState(99L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                1L, new TaskApiData.TaskState(1L, TaskExecState.OK.value, 0L, false),
                2L, new TaskApiData.TaskState(2L, TaskExecState.OK.value, 0L, false),
                3L, new TaskApiData.TaskState(3L, TaskExecState.OK.value, 0L, false),
                4L, new TaskApiData.TaskState(4L, TaskExecState.IN_PROGRESS.value, 0L, false),
                5L, new TaskApiData.TaskState(5L, TaskExecState.NONE.value, 0L, false),
                99L, new TaskApiData.TaskState(99L, TaskExecState.NONE.value, 0L, false)
        );

        List<String> processCodes = List.of("rg-analyze", "rg-decompose", "rg-process", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "test-rg-full", true, states);

        ExecContextApiData.ExecContextStateResult result = ExecContextUtils.getExecContextStateResult(42L, raw, true);

        assertNotNull(result);
        assertEquals(4, result.header.length);
        // 1 + 2 fan-out + 2 fan-out = 5 unique contexts, but "1" has 2 tasks (rg-analyze + mh.finish)
        assertEquals(5, result.lines.length);

        // verify all contexts present
        assertEquals("1", result.lines[0].context);

        // verify task-to-column mapping
        // row "1": rg-analyze at col 0, mh.finish at col 3
        assertEquals(1L, result.lines[0].cells[0].taskId);
        assertFalse(result.lines[0].cells[0].empty);
        assertEquals(99L, result.lines[0].cells[3].taskId);
        assertFalse(result.lines[0].cells[3].empty);
    }

    // Test managerRole=false hides output variables for completed tasks
    @Test
    public void test_managerRoleFalseHidesOutputs() {
        ExecContextApiData.VariableInfo outVar = new ExecContextApiData.VariableInfo(500L, "result.txt", EnumsApi.VariableContext.local, ".txt");

        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(1L, 1111L, 1001L, "1", "p-1", "f-1", null, List.of(outVar)),
                new ExecContextApiData.VariableState(2L, 1111L, 1001L, "1", "mh.finish", "mh.finish", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                1L, new TaskApiData.TaskState(1L, TaskExecState.OK.value, 0L, false),
                2L, new TaskApiData.TaskState(2L, TaskExecState.OK.value, 0L, false)
        );

        List<String> processCodes = List.of("p-1", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.common, "test-role", true, states);

        // managerRole = true -> outputs visible
        ExecContextApiData.ExecContextStateResult resultManager = ExecContextUtils.getExecContextStateResult(42L, raw, true);
        assertNotNull(resultManager.lines[0].cells[0].outs);
        assertEquals(1, resultManager.lines[0].cells[0].outs.size());

        // managerRole = false -> outputs null
        ExecContextApiData.ExecContextStateResult resultOperator = ExecContextUtils.getExecContextStateResult(42L, raw, false);
        assertNull(resultOperator.lines[0].cells[0].outs);
    }
}
