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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.S;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Variable-state entries as readers see them (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 12). Pure; the stored entries are
 * never modified - every result is a copy.
 *
 * <p>The whole-ExecContext variable-state record copied flags across Tasks: an uploaded output marked the matching
 * input of every Task, and a newly registered entry back-filled its inputs from the entries already there (an inited
 * output first, else an inited input). Segments store each entry with its own Task only (Phase 9), so
 * {@link #withDerivedInputs} derives the same flags at read time: per variable id, the known state is an inited
 * output's (inited, nullified), else an inited input's; every input of a known variable takes that state, every other
 * input keeps what its entry stored.
 */
public final class SegmentVariableStates {

    private SegmentVariableStates() {
    }

    private record Known(boolean inited, boolean nullified) {
    }

    /** The entries in the same order, copied, with every input's inited / nullified derived as the class describes. */
    public static List<ExecContextApiData.VariableState> withDerivedInputs(Collection<ExecContextApiData.VariableState> entries) {
        final Map<Long, Known> known = new HashMap<>();
        for (ExecContextApiData.VariableState e : entries) {
            if (e.outputs != null) {
                for (ExecContextApiData.VariableInfo out : e.outputs) {
                    if (out.inited) {
                        known.put(out.id, new Known(true, out.nullified));
                    }
                }
            }
        }
        for (ExecContextApiData.VariableState e : entries) {
            if (e.inputs != null) {
                for (ExecContextApiData.VariableInfo in : e.inputs) {
                    if (in.inited) {
                        known.putIfAbsent(in.id, new Known(true, in.nullified));
                    }
                }
            }
        }
        final List<ExecContextApiData.VariableState> result = new ArrayList<>(entries.size());
        for (ExecContextApiData.VariableState e : entries) {
            result.add(copy(e, known));
        }
        return result;
    }

    /** The non-blank {@code ext} of {@code variableId} as the output of some entry, or null - what the extension lookup read. */
    @Nullable
    public static String outputExt(Collection<ExecContextApiData.VariableState> entries, Long variableId) {
        for (ExecContextApiData.VariableState e : entries) {
            if (e.outputs == null) {
                continue;
            }
            for (ExecContextApiData.VariableInfo out : e.outputs) {
                if (variableId.equals(out.id) && !S.b(out.ext)) {
                    return out.ext;
                }
            }
        }
        return null;
    }

    private static ExecContextApiData.VariableState copy(ExecContextApiData.VariableState e, Map<Long, Known> known) {
        final ExecContextApiData.VariableState c = new ExecContextApiData.VariableState();
        c.taskId = e.taskId;
        c.processorId = e.processorId;
        c.execContextId = e.execContextId;
        c.taskContextId = e.taskContextId;
        c.process = e.process;
        c.functionCode = e.functionCode;
        if (e.outputs != null) {
            final List<ExecContextApiData.VariableInfo> outputs = new ArrayList<>(e.outputs.size());
            for (ExecContextApiData.VariableInfo out : e.outputs) {
                outputs.add(copy(out, null));
            }
            c.outputs = outputs;
        }
        if (e.inputs != null) {
            final List<ExecContextApiData.VariableInfo> inputs = new ArrayList<>(e.inputs.size());
            for (ExecContextApiData.VariableInfo in : e.inputs) {
                inputs.add(copy(in, known.get(in.id)));
            }
            c.inputs = inputs;
        }
        return c;
    }

    private static ExecContextApiData.VariableInfo copy(ExecContextApiData.VariableInfo v, @Nullable Known known) {
        final ExecContextApiData.VariableInfo c = new ExecContextApiData.VariableInfo(v.id, v.name, v.context, v.ext);
        c.inited = known != null ? known.inited() : v.inited;
        c.nullified = known != null ? known.nullified() : v.nullified;
        return c;
    }
}
