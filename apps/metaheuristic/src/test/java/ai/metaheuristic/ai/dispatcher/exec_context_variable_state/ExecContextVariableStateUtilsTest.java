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

package ai.metaheuristic.ai.dispatcher.exec_context_variable_state;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class ExecContextVariableStateUtilsTest {

    private static ExecContextApiData.VariableInfo input(Long id, boolean inited, boolean nullified) {
        ExecContextApiData.VariableInfo vi = new ExecContextApiData.VariableInfo(id, "decomposeReqCount", EnumsApi.VariableContext.local, null);
        vi.inited = inited;
        vi.nullified = nullified;
        return vi;
    }

    private static ExecContextApiData.VariableState task(Long taskId, List<ExecContextApiData.VariableInfo> inputs) {
        return new ExecContextApiData.VariableState(
                taskId, null, 65L, "1", "process-" + taskId, "func", inputs, new ArrayList<>());
    }

    /**
     * A dispatcher-seeded variable #1759 was already uploaded and nullified before the recursive
     * '-r' task was grafted. When the new task is registered, its input for #1759 must inherit the
     * already-known inited/nullified state.
     */
    @Test
    public void test_registerCreatedTasks_backfillsNullifiedFromExistingStates() {
        // existing task already carries #1759 as inited & nullified (the correct, non-recursive pass)
        List<ExecContextApiData.VariableState> states = new ArrayList<>();
        states.add(task(1549L, new ArrayList<>(List.of(input(1759L, true, true)))));

        // freshly grafted recursive task references #1759 but with default (not-yet-known) flags
        ExecContextApiData.VariableState newTask = task(1575L, new ArrayList<>(List.of(input(1759L, false, false))));
        List<ExecContextApiData.VariableState> events = new ArrayList<>(List.of(newTask));

        ExecContextVariableStateUtils.registerCreatedTasks(states, events);

        ExecContextApiData.VariableState merged = states.stream()
                .filter(s -> s.taskId.equals(1575L)).findFirst().orElseThrow();
        ExecContextApiData.VariableInfo dec = merged.inputs.stream()
                .filter(i -> i.id.equals(1759L)).findFirst().orElseThrow();

        assertTrue(dec.inited);
        assertTrue(dec.nullified);
    }
}
