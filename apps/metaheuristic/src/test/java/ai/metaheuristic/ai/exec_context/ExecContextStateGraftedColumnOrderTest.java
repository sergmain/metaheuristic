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

package ai.metaheuristic.ai.exec_context;

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.api.EnumsApi.SourceCodeType;
import static ai.metaheuristic.api.EnumsApi.TaskExecState;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Column order for runtime-GRAFTED rung processes (absent from the static processCodes) is decided by
 * the grafted-insertion walk in {@link ExecContextUtils#getExecContextStateResult}.
 *
 * Reproduces execContext #106: two requirement lanes grafted from the same graft node.
 *  - lane A had NO objectives, so it produced only resolve-requirement-status (low task id).
 *  - lane B had objectives, so it produced store-objective-result -> resolve-requirement-status
 *    (higher task ids), with a real task edge store -> resolve.
 *
 * The runtime edge store(20) -> resolve(21) means the store column must sit LEFT of the resolve
 * column. But "first occurrence wins" records resolve (lane A, id 10) before store (lane B, id 20),
 * so the resolve column is emitted first and the edge renders right-to-left.
 */
@Execution(CONCURRENT)
public class ExecContextStateGraftedColumnOrderTest {

    private static final String GRAFT = "mh.graft.req-rung-0.1";
    private static final String STORE = "store-objective-result";
    private static final String RESOLVE = "resolve-requirement-status";
    private static final String FINISH = "mh.finish";

    private static int colOf(ExecContextApiData.ExecContextStateResult r, String process) {
        for (int i = 0; i < r.header.length; i++) {
            if (process.equals(r.header[i].process)) {
                return i;
            }
        }
        return -1;
    }

    private static ExecContextApiData.ExecContextStateResult buildResult() {
        List<ExecContextApiData.VariableState> infos = List.of(
                new ExecContextApiData.VariableState(1L, 1111L, 1001L, "1", GRAFT, "mh.nop", null, null),
                new ExecContextApiData.VariableState(10L, 1111L, 1001L, "1,2,3|1#0", RESOLVE, "f", null, null),
                new ExecContextApiData.VariableState(20L, 1111L, 1001L, "1,2,3,4,5|2|0|0#0", STORE, "f", null, null),
                new ExecContextApiData.VariableState(21L, 1111L, 1001L, "1,2,3|2#0", RESOLVE, "f", null, null),
                new ExecContextApiData.VariableState(99L, 1111L, 1001L, "1", FINISH, FINISH, null, null)
        );
        Map<Long, TaskApiData.TaskState> states = Map.of(
                1L,  new TaskApiData.TaskState(1L,  TaskExecState.OK.value, 0L, false, "1", GRAFT),
                10L, new TaskApiData.TaskState(10L, TaskExecState.OK.value, 0L, false, "1,2,3|1#0", RESOLVE),
                20L, new TaskApiData.TaskState(20L, TaskExecState.OK.value, 0L, false, "1,2,3,4,5|2|0|0#0", STORE),
                21L, new TaskApiData.TaskState(21L, TaskExecState.OK.value, 0L, false, "1,2,3|2#0", RESOLVE),
                99L, new TaskApiData.TaskState(99L, TaskExecState.OK.value, 0L, false, "1", FINISH)
        );
        List<String> processCodes = List.of(GRAFT, FINISH);
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.batch, "test-source-code", true, states);
        raw.taskEdges = new ArrayList<>(List.of(
                new long[]{1L, 10L}, new long[]{1L, 20L}, new long[]{20L, 21L},
                new long[]{10L, 99L}, new long[]{21L, 99L}
        ));
        return ExecContextUtils.getExecContextStateResult(42L, raw, true);
    }

    @Test
    public void test_graftedStoreColumnLeftOfResolveColumn() {
        ExecContextApiData.ExecContextStateResult r = buildResult();
        List<String> header = new ArrayList<>();
        for (ExecContextApiData.ColumnHeader h : r.header) {
            header.add(h.process);
        }
        System.out.println("header = " + header);

        int store = colOf(r, STORE);
        int resolve = colOf(r, RESOLVE);
        assertTrue(store >= 0 && resolve >= 0, "both columns present: " + header);

        // the runtime edge store -> resolve requires store's column to be LEFT of resolve's.
        assertTrue(store < resolve,
                "store-objective-result column must be LEFT of resolve-requirement-status, header = " + header);
    }
}
