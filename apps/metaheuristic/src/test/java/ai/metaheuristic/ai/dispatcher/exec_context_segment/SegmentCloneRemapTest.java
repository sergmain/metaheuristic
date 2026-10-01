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
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 041-EXEC-CONTEXT-SEGMENTS-PLAN Phase 13: {@link SegmentClone} - the segment params of a cloned ExecContext. Structures come
 * from the real DOT fixtures (S1 = ExecContext 325, S3 = a clone with two line bases); ids are shifted by a fixed offset.
 */
@Execution(ExecutionMode.CONCURRENT)
public class SegmentCloneRemapTest {

    private static final long OFFSET = 1_000_000L;
    private static final long NEW_EC = 777L;

    private static List<SegmentData.Line> linesOf(SegmentFixtureShapes.DotShape shape) {
        return SegmentAlgebra.decompose(SegmentDotUtils.parse(shape.dot()));
    }

    /** Every Task of the lines -> id + OFFSET. */
    private static Map<Long, Long> shifted(List<SegmentData.Line> lines) {
        final Map<Long, Long> m = new HashMap<>();
        for (SegmentData.Line l : lines) {
            for (SegmentData.Vertex v : l.tasks()) {
                m.put(v.taskId(), v.taskId() + OFFSET);
            }
        }
        return m;
    }

    private static List<SegmentData.Line> shiftedLines(List<SegmentData.Line> lines) {
        return lines.stream().map(l -> new SegmentData.Line(l.ctx(), l.forkTaskId() == null ? null : l.forkTaskId() + OFFSET,
                l.tasks().stream().map(v -> new SegmentData.Vertex(v.taskId() + OFFSET, v.tag())).toList())).toList();
    }

    private static ExecContextApiData.VariableInfo info(Long id, boolean inited, boolean nullified) {
        final ExecContextApiData.VariableInfo v = new ExecContextApiData.VariableInfo(id, "v" + id, EnumsApi.VariableContext.local, ".x");
        v.inited = inited;
        v.nullified = nullified;
        return v;
    }

    private static void assertStructureShifted(SegmentFixtureShapes.DotShape shape) {
        final List<SegmentData.Line> lines = linesOf(shape);
        final ExecContextSegmentParams remapped = SegmentClone.remap(SegmentParamsConverter.params(lines), shifted(lines), Map.of(), NEW_EC);
        assertEquals(shiftedLines(lines), SegmentParamsConverter.lines(remapped),
                shape.id() + ": every Task id and fork shifted; ctx, order and tags kept");
        final SegmentData.Line root = lines.getFirst();
        assertEquals(
                SegmentStructureHash.structureHash(new SegmentData.Segment(root.ctx(), null, shiftedLines(lines))),
                SegmentStructureHash.structureHash(SegmentParamsConverter.segment(root.ctx(), null, remapped)),
                shape.id() + ": the hash of the remapped params is the hash of the shifted structure");
    }

    @Test public void test_structureShifted_S1() { assertStructureShifted(SegmentFixtureShapes.DOT_S1); }
    @Test public void test_structureShifted_S3_twoLineBases() { assertStructureShifted(SegmentFixtureShapes.DOT_S3); }
    @Test public void test_structureShifted_S6() { assertStructureShifted(SegmentFixtureShapes.DOT_S6); }

    @Test
    public void test_terminalTagKept_S1() {
        final List<SegmentData.Line> lines = linesOf(SegmentFixtureShapes.DOT_S1);
        final ExecContextSegmentParams remapped = SegmentClone.remap(SegmentParamsConverter.params(lines), shifted(lines), Map.of(), NEW_EC);
        final List<Long> tagged = remapped.lines.stream().flatMap(l -> l.tasks.stream())
                .filter(v -> "terminal".equals(v.tag)).map(v -> v.taskId).toList();
        assertEquals(List.of(191554L + OFFSET), tagged, "the join keeps tag terminal under its new id");
    }

    @Test
    public void test_statesTriesRegisteredAndEntries() {
        final ExecContextSegmentParams source = new ExecContextSegmentParams();
        final ExecContextSegmentParams.Line line = new ExecContextSegmentParams.Line("1,2#1", 10L);
        line.tasks.add(new ExecContextSegmentParams.Vertex(11L, null));
        line.tasks.add(new ExecContextSegmentParams.Vertex(12L, "terminal"));
        line.registered = true;
        source.lines.add(line);
        source.states.put(11L, EnumsApi.TaskExecState.OK);
        source.states.put(12L, EnumsApi.TaskExecState.SKIPPED);
        source.triesWasMade.put(11L, 2);
        final ExecContextApiData.VariableState e = new ExecContextApiData.VariableState();
        e.taskId = 11L;
        e.processorId = 5L;
        e.execContextId = 3L;
        e.taskContextId = "1,2#1";
        e.process = "p";
        e.functionCode = "f";
        e.inputs = new ArrayList<>(List.of(info(100L, true, false)));
        e.outputs = new ArrayList<>(List.of(info(101L, true, true), info(102L, false, false)));
        source.variableStates.add(e);

        final ExecContextSegmentParams r = SegmentClone.remap(source,
                Map.of(10L, 20L, 11L, 21L, 12L, 22L), Map.of(100L, 200L, 101L, 201L), NEW_EC);

        final ExecContextSegmentParams.Line rl = r.lines.getFirst();
        assertEquals("1,2#1", rl.ctx, "ctx kept");
        assertEquals(20L, rl.forkTaskId, "fork remapped");
        assertTrue(rl.registered, "registered flag kept");
        assertEquals(List.of(21L, 22L), rl.tasks.stream().map(v -> v.taskId).toList());
        assertEquals(Map.of(21L, EnumsApi.TaskExecState.OK, 22L, EnumsApi.TaskExecState.SKIPPED), r.states);
        assertEquals(Map.of(21L, 2), r.triesWasMade);

        final ExecContextApiData.VariableState re = r.variableStates.getFirst();
        assertEquals(21L, re.taskId, "entry Task remapped");
        assertEquals(NEW_EC, re.execContextId, "entry carries the clone's ExecContext id");
        assertEquals(List.of(5L, "1,2#1", "p", "f"), List.of(re.processorId, re.taskContextId, re.process, re.functionCode));
        assertEquals(200L, re.inputs.getFirst().id, "input Variable remapped");
        assertTrue(re.inputs.getFirst().inited);
        assertEquals(List.of(201L, 102L), re.outputs.stream().map(v -> v.id).toList(), "an unmapped Variable id stays");
        assertTrue(re.outputs.getFirst().nullified, "flags kept");
        assertEquals(".x", re.outputs.getFirst().ext, "ext kept");
    }

    @Test
    public void test_unmappedTaskIdsStay_sourceUntouched() {
        final ExecContextSegmentParams source = new ExecContextSegmentParams();
        final ExecContextSegmentParams.Line line = new ExecContextSegmentParams.Line("1", null);
        line.tasks.add(new ExecContextSegmentParams.Vertex(1L, null));
        line.tasks.add(new ExecContextSegmentParams.Vertex(2L, null));
        source.lines.add(line);
        source.states.put(2L, EnumsApi.TaskExecState.NONE);

        final ExecContextSegmentParams r = SegmentClone.remap(source, Map.of(1L, 9L), Map.of(), NEW_EC);
        assertEquals(List.of(9L, 2L), r.lines.getFirst().tasks.stream().map(v -> v.taskId).toList(), "an unmapped Task id stays");
        assertNull(r.lines.getFirst().forkTaskId, "the root line keeps no fork");
        assertEquals(Map.of(2L, EnumsApi.TaskExecState.NONE), r.states);
        assertEquals(List.of(1L, 2L), source.lines.getFirst().tasks.stream().map(v -> v.taskId).toList(), "the source is untouched");
        assertNotSame(source.lines.getFirst(), r.lines.getFirst());
    }

    @Test
    public void test_referencedVariableIds_inputsAndOutputs() {
        final ExecContextApiData.VariableState a = new ExecContextApiData.VariableState();
        a.inputs = new ArrayList<>(List.of(info(7L, false, false)));
        a.outputs = new ArrayList<>(List.of(info(8L, false, false)));
        final ExecContextApiData.VariableState b = new ExecContextApiData.VariableState();
        b.inputs = new ArrayList<>(List.of(info(8L, false, false), info(9L, false, false)));
        final ExecContextApiData.VariableState c = new ExecContextApiData.VariableState();
        assertEquals(Set.of(7L, 8L, 9L), SegmentClone.referencedVariableIds(List.of(a, b, c)));
    }
}
