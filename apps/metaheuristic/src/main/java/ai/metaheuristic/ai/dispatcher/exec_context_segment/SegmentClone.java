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

import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Segment params of a cloned ExecContext (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 13, decision 15). Pure; the source params
 * are never modified - every result is a new object.
 *
 * <p>The remap rules are the ones the whole-ExecContext records' clone rewrite used: a Task id or Variable id present in
 * its map is replaced, an id absent from it stays as it is. Two differences from that rewrite, both on purpose: every
 * variable-state entry carries the clone's ExecContext id (the record rewrite kept the source's), and tags are kept (the
 * record's DOT rewrite dropped them).
 */
public final class SegmentClone {

    private SegmentClone() {
    }

    /** {@code id} through {@code map}, or {@code id} itself when the map has no entry for it. */
    public static Long remapped(Long id, Map<Long, Long> map) {
        return map.getOrDefault(id, id);
    }

    /**
     * A copy of {@code source} for the clone: lines (ctx, fork, Tasks with their tags, the {@code registered} flag),
     * states and tries keyed by the new Task ids, variable-state entries with new Task and Variable ids and
     * {@code newExecContextId}.
     */
    public static ExecContextSegmentParams remap(ExecContextSegmentParams source, Map<Long, Long> taskIdMap,
                                                 Map<Long, Long> variableIdMap, Long newExecContextId) {
        final ExecContextSegmentParams target = new ExecContextSegmentParams();
        for (ExecContextSegmentParams.Line line : source.lines) {
            final ExecContextSegmentParams.Line copy = new ExecContextSegmentParams.Line(line.ctx,
                    line.forkTaskId == null ? null : remapped(line.forkTaskId, taskIdMap));
            copy.registered = line.registered;
            for (ExecContextSegmentParams.Vertex v : line.tasks) {
                copy.tasks.add(new ExecContextSegmentParams.Vertex(remapped(v.taskId, taskIdMap), v.tag));
            }
            target.lines.add(copy);
        }
        source.states.forEach((taskId, state) -> target.states.put(remapped(taskId, taskIdMap), state));
        source.triesWasMade.forEach((taskId, tries) -> target.triesWasMade.put(remapped(taskId, taskIdMap), tries));
        for (ExecContextApiData.VariableState e : source.variableStates) {
            target.variableStates.add(entry(e, taskIdMap, variableIdMap, newExecContextId));
        }
        return target;
    }

    /** Every Variable id an input or output of the entries refers to - what the clone copies (decision 15: as today). */
    public static Set<Long> referencedVariableIds(Collection<ExecContextApiData.VariableState> entries) {
        final Set<Long> ids = new TreeSet<>();
        for (ExecContextApiData.VariableState e : entries) {
            addIds(e.inputs, ids);
            addIds(e.outputs, ids);
        }
        return ids;
    }

    private static void addIds(@Nullable List<ExecContextApiData.VariableInfo> infos, Set<Long> ids) {
        if (infos == null) {
            return;
        }
        for (ExecContextApiData.VariableInfo vi : infos) {
            if (vi.id != null) {
                ids.add(vi.id);
            }
        }
    }

    private static ExecContextApiData.VariableState entry(ExecContextApiData.VariableState e, Map<Long, Long> taskIdMap,
                                                          Map<Long, Long> variableIdMap, Long newExecContextId) {
        final ExecContextApiData.VariableState c = new ExecContextApiData.VariableState();
        c.taskId = e.taskId == null ? null : remapped(e.taskId, taskIdMap);
        c.processorId = e.processorId;
        c.execContextId = newExecContextId;
        c.taskContextId = e.taskContextId;
        c.process = e.process;
        c.functionCode = e.functionCode;
        c.inputs = infos(e.inputs, variableIdMap);
        c.outputs = infos(e.outputs, variableIdMap);
        return c;
    }

    @Nullable
    private static List<ExecContextApiData.VariableInfo> infos(@Nullable List<ExecContextApiData.VariableInfo> source,
                                                               Map<Long, Long> variableIdMap) {
        if (source == null) {
            return null;
        }
        final List<ExecContextApiData.VariableInfo> out = new ArrayList<>(source.size());
        for (ExecContextApiData.VariableInfo vi : source) {
            final ExecContextApiData.VariableInfo c = new ExecContextApiData.VariableInfo(
                    vi.id == null ? null : remapped(vi.id, variableIdMap), vi.name, vi.context, vi.ext);
            c.inited = vi.inited;
            c.nullified = vi.nullified;
            out.add(c);
        }
        return out;
    }
}
