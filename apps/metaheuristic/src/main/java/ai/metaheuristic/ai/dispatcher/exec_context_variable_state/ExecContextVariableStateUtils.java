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

import ai.metaheuristic.api.data.exec_context.ExecContextApiData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Sergio Lissner
 */
public class ExecContextVariableStateUtils {

    private record InitedState(boolean inited, boolean nullified) {}

    /**
     * Merge freshly created task states into the current list of states.
     * Existing tasks (by taskId) have their inputs/outputs replaced; unknown tasks are appended.
     * <p>
     * The forward propagation in {@link ExecContextVariableStateService} only fires at
     * variable-upload time over the tasks that exist at that moment. Tasks created later (e.g. a
     * recursive '-r' branch expanded via graft) miss any upload that already happened, so their
     * inputs referencing an already-inited/nullified variable would stay {@code inited=false}.
     * To close that gap we back-fill each new input's inited/nullified from the already-known state
     * of the same variable, computed from the states that exist before this merge.
     */
    public static void registerCreatedTasks(
            List<ExecContextApiData.VariableState> states,
            List<ExecContextApiData.VariableState> events) {
        Map<Long, InitedState> known = collectKnownInitedStates(states);
        for (ExecContextApiData.VariableState event : events) {
            backfillInputs(event, known);
            boolean isNew = true;
            for (ExecContextApiData.VariableState state : states) {
                if (state.taskId.equals(event.taskId)) {
                    isNew = false;
                    if (state.inputs != null && !state.inputs.isEmpty()) {
                        state.inputs.clear();
                    }
                    if (state.outputs != null && !state.outputs.isEmpty()) {
                        state.outputs.clear();
                    }
                    state.inputs = event.inputs;
                    state.outputs = event.outputs;
                    break;
                }
            }
            if (isNew) {
                states.add(event);
            }
        }
    }

    /**
     * Collect the already-known inited/nullified state per variableId from the current states.
     * Producer outputs are authoritative; a dispatcher-seeded variable has no producer output, so
     * inited inputs of earlier tasks are used as the fallback source.
     */
    private static Map<Long, InitedState> collectKnownInitedStates(List<ExecContextApiData.VariableState> states) {
        Map<Long, InitedState> known = new HashMap<>();
        for (ExecContextApiData.VariableState state : states) {
            if (state.outputs != null) {
                for (ExecContextApiData.VariableInfo out : state.outputs) {
                    if (out.inited) {
                        known.put(out.id, new InitedState(out.inited, out.nullified));
                    }
                }
            }
        }
        for (ExecContextApiData.VariableState state : states) {
            if (state.inputs != null) {
                for (ExecContextApiData.VariableInfo in : state.inputs) {
                    if (in.inited) {
                        known.putIfAbsent(in.id, new InitedState(in.inited, in.nullified));
                    }
                }
            }
        }
        return known;
    }

    private static void backfillInputs(ExecContextApiData.VariableState event, Map<Long, InitedState> known) {
        if (event.inputs == null) {
            return;
        }
        for (ExecContextApiData.VariableInfo input : event.inputs) {
            InitedState src = known.get(input.id);
            if (src != null) {
                input.inited = src.inited();
                input.nullified = src.nullified();
            }
        }
    }
}
