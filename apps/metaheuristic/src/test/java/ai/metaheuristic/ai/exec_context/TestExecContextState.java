/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 8/20/2020
 * Time: 12:02 AM
 */
public class TestExecContextState {

    @Test
    public void test() {


/*
        Long sourceCodeId, List<TaskData.SimpleTaskInfo> infos, List<List< ExecContextData.TaskVertex>> vertices,
                List<String> processCodes, EnumsApi.SourceCodeType sourceCodeType, String sourceCodeUid, boolean sourceCodeValid
*/

/*
        public Long taskId;
        public String state;
        public String context;
        public String process;
        public String functionCode;
*/
        List<TaskData.SimpleTaskInfo> infos = List.of(
                new TaskData.SimpleTaskInfo(100L, TaskExecState.OK.toString(), "ctx-1", "process-1", "function-1"),
                new TaskData.SimpleTaskInfo(120L, TaskExecState.IN_PROGRESS.toString(), "ctx-1.1", "process-2", "function-2"),
                new TaskData.SimpleTaskInfo(140L, TaskExecState.NONE.toString(), "ctx-1.1", "process-3", "function-3"),
                new TaskData.SimpleTaskInfo(160L, TaskExecState.OK.toString(), "ctx-1.2", "process-2", "function-2"),
                new TaskData.SimpleTaskInfo(180L, TaskExecState.ERROR.toString(), "ctx-1.2", "process-3", "function-3"),
                new TaskData.SimpleTaskInfo(190L, TaskExecState.OK.toString(), "ctx-1", "mh.finish", "mh.finish")
        );
        List<String> processCodes = List.of("process-1", "process-2", "process-3", "mh.finish");
        ExecContextApiData.ExecContextStateResult r = ExecContextTopLevelService.getExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.batch, "test-source-code", true);

        assertNotNull(r);
        assertNotNull(r.header);
        assertNotNull(r.lines);
        assertEquals(4, r.header.length);
        assertEquals(3, r.lines.length);

        assertEquals("process-1", r.header[0].process);
        assertEquals("process-2", r.header[1].process);
        assertEquals("process-3", r.header[2].process);
        assertEquals("mh.finish", r.header[3].process);

        assertEquals("ctx-1", r.lines[0].context);
        assertEquals("ctx-1.1", r.lines[1].context);
        assertEquals("ctx-1.2", r.lines[2].context);

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
        assertEquals("ctx-1", r.lines[0].cells[0].context);

        assertEquals(190L, r.lines[0].cells[3].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[0].cells[3].state);
        assertEquals("ctx-1", r.lines[0].cells[3].context);

        assertEquals(120L, r.lines[1].cells[1].taskId);
        assertEquals(TaskExecState.IN_PROGRESS.toString(), r.lines[1].cells[1].state);
        assertEquals("ctx-1.1", r.lines[1].cells[1].context);

        assertEquals(140L, r.lines[1].cells[2].taskId);
        assertEquals(TaskExecState.NONE.toString(), r.lines[1].cells[2].state);
        assertEquals("ctx-1.1", r.lines[1].cells[2].context);

        assertEquals(160L, r.lines[2].cells[1].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[2].cells[1].state);
        assertEquals("ctx-1.2", r.lines[2].cells[1].context);

        assertEquals(180L, r.lines[2].cells[2].taskId);
        assertEquals(TaskExecState.ERROR.toString(), r.lines[2].cells[2].state);
        assertEquals("ctx-1.2", r.lines[2].cells[2].context);


        System.out.println(Arrays.toString(r.header));
        for (ExecContextApiData.LineWithState line : r.lines) {
            System.out.println(line.context+", "+ Arrays.toString(line.cells));
        }
    }
}
