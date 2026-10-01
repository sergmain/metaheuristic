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

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextJoin;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextSegment;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCloneService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeInitService;
import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 041-EXEC-CONTEXT-SEGMENTS-PLAN Phase 13 (section 8.6, decision 15): a clone copies every segment and join record with
 * remapped ids and recomputed structure hashes.
 *
 * <p>Every check is independent of the remap code under test: lines are compared through the clone's Task-id map
 * directly, join records and DOT vertices by {@code processCode@ctx} (from {@code MH_TASK}), and the repeated-clone
 * property as "the clone's structure mapped back through the inverse Task-id map hashes to the source's stored
 * {@code STRUCTURE_HASH}".
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SegmentCloneTest extends PreparingSourceCode {

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private SegmentInvariantAsserts invariants;
    @Autowired private ExecContextCloneService execContextCloneService;
    @Autowired private ExecContextSegmentRepository segmentRepository;
    @Autowired private ExecContextJoinRepository joinRepository;
    @Autowired private ExecContextSegmentReadService segmentReadService;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s6", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S6.mhsc());
    }

    private Long runToFinished(SegmentFixtureShapes.Shape shape) {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(shape.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextBaselineSupport.StepRun run = support.runStepByStep(() -> {
            final ExecContextImpl ec = support.createAndStart(data, shape.items());
            setExecContextForTest(ec);
            return ec.id;
        }, 80);
        assertEquals(EnumsApi.ExecContextState.FINISHED, support.state(run.execContextId()), shape.id() + ": the source must reach FINISHED");
        return run.execContextId();
    }

    private Map<String, ExecContextSegment> segmentsByCtx(Long ecId) {
        final Map<String, ExecContextSegment> m = new TreeMap<>();
        for (Long id : segmentRepository.findIdsByExecContextId(ecId)) {
            final ExecContextSegment s = segmentRepository.findById(id).orElseThrow();
            m.put(s.lineCtxId, s);
        }
        return m;
    }

    private Map<Long, String> keyById(Long ecId) {
        return support.rows(ecId).stream().collect(Collectors.toMap(ExecContextBaselineSupport.TaskRow::id, ExecContextBaselineSupport.TaskRow::key));
    }

    /** Join records as {@code joinKey: registered/finished/dead closed=...}, sorted. */
    private List<String> joinsDescribed(Long ecId) {
        final Map<Long, String> key = keyById(ecId);
        final List<String> out = new ArrayList<>();
        for (Long id : joinRepository.findIdsByExecContextId(ecId)) {
            final ExecContextJoin j = joinRepository.findById(id).orElseThrow();
            out.add(key.get(j.joinTaskId) + ": " + j.linesRegistered + "/" + j.linesFinished + "/" + j.linesDead + " closed=" + j.closed);
        }
        out.sort(String::compareTo);
        return out;
    }

    /** Derived DOT edges and tagged vertices as {@code processCode@ctx} text, sorted. */
    private String dotDescribed(Long ecId) {
        final Map<Long, String> key = keyById(ecId);
        final SegmentData.Graph g = SegmentDotUtils.parse(segmentReadService.dot(ecId));
        final List<String> lines = new ArrayList<>();
        g.edges().forEach(e -> lines.add(key.get(e.from()) + " -> " + key.get(e.to())));
        g.nodes().values().stream().filter(n -> n.tag() != null).forEach(n -> lines.add(key.get(n.taskId()) + " [" + n.tag() + "]"));
        lines.sort(String::compareTo);
        return String.join("\n", lines);
    }

    private static void assertHashMatchesContent(ExecContextSegment s, String label) {
        assertEquals(SegmentStructureHash.structureHash(SegmentParamsConverter.segment(s.lineCtxId, s.forkTaskId, s.getExecContextSegmentParams())),
                s.structureHash, label + ": segment " + s.lineCtxId + " - the stored hash is the hash of its stored structure");
    }

    @Test
    public void test_S6_clone_segmentsAndJoinsCopiedWithRemappedIds() {
        final Long sourceId = runToFinished(SegmentFixtureShapes.S6);
        final ExecContextCloneService.CloneResult cr = execContextCloneService.cloneExecContext(sourceId);
        final Long cloneId = cr.clonedExecContextId();
        final Map<Long, Long> map = cr.taskIdMap();

        final Map<String, ExecContextSegment> source = segmentsByCtx(sourceId);
        final Map<String, ExecContextSegment> clone = segmentsByCtx(cloneId);
        assertEquals(source.keySet(), clone.keySet(), "S6: one clone segment per source segment, same LINE_CTX_ID");
        assertEquals(1 + 2 + 4 + 8, clone.size(), "S6: the root segment and one per grafted line on three levels");

        for (String ctx : source.keySet()) {
            final ExecContextSegment s = source.get(ctx);
            final ExecContextSegment c = clone.get(ctx);
            assertNotEquals(s.id, c.id, ctx + ": a new segment record");
            assertEquals(cloneId, c.execContextId, ctx + ": in the clone");
            assertEquals(s.forkTaskId == null ? null : map.get(s.forkTaskId), c.forkTaskId, ctx + ": fork through the Task-id map");
            assertHashMatchesContent(c, "clone");

            final ExecContextSegmentParams sp = s.getExecContextSegmentParams();
            final ExecContextSegmentParams cp = c.getExecContextSegmentParams();
            assertEquals(sp.lines.size(), cp.lines.size(), ctx + ": same number of lines");
            for (int i = 0; i < sp.lines.size(); i++) {
                final ExecContextSegmentParams.Line sl = sp.lines.get(i);
                final ExecContextSegmentParams.Line cl = cp.lines.get(i);
                assertEquals(sl.ctx, cl.ctx, ctx + ": line ctx kept");
                assertEquals(sl.forkTaskId == null ? null : map.get(sl.forkTaskId), cl.forkTaskId, sl.ctx + ": line fork mapped");
                assertEquals(sl.registered, cl.registered, sl.ctx + ": registered kept");
                assertEquals(sl.tasks.stream().map(v -> map.get(v.taskId)).toList(), cl.tasks.stream().map(v -> v.taskId).toList(), sl.ctx + ": Tasks mapped, order kept");
                assertEquals(sl.tasks.stream().map(v -> String.valueOf(v.tag)).toList(), cl.tasks.stream().map(v -> String.valueOf(v.tag)).toList(), sl.ctx + ": tags kept");
            }
            final Map<Long, EnumsApi.TaskExecState> expectedStates = new HashMap<>();
            sp.states.forEach((k, v) -> expectedStates.put(map.get(k), v));
            assertEquals(expectedStates, cp.states, ctx + ": states keyed by the clone's Task ids");
            assertEquals(sp.variableStates.size(), cp.variableStates.size(), ctx + ": one entry per source entry");
            for (int i = 0; i < sp.variableStates.size(); i++) {
                final ExecContextApiData.VariableState se = sp.variableStates.get(i);
                final ExecContextApiData.VariableState ce = cp.variableStates.get(i);
                assertEquals(cloneId, ce.execContextId, ctx + ": every entry carries the clone's ExecContext id");
                assertEquals(map.get(se.taskId), ce.taskId, ctx + ": entry Task mapped");
            }
        }

        assertEquals(joinsDescribed(sourceId), joinsDescribed(cloneId), "S6: every join record copied with its counts, under the mapped join Task");
        assertFalse(joinsDescribed(cloneId).isEmpty(), "S6: the lines' join has a record");
        assertEquals(dotDescribed(sourceId), dotDescribed(cloneId), "S6: the clone's derived DOT equals the source's modulo ids, tags included");

        invariants.assertAll(sourceId);
        invariants.assertAll(cloneId);
    }

    @Test
    public void test_S1_repeatedClones_structureEqualModuloIdMap_terminalTagKept() {
        final Long sourceId = runToFinished(SegmentFixtureShapes.S1);
        final Map<String, ExecContextSegment> source = segmentsByCtx(sourceId);
        final List<Long> clones = new ArrayList<>();
        for (int round = 1; round <= 2; round++) {
            final ExecContextCloneService.CloneResult cr = execContextCloneService.cloneExecContext(sourceId);
            clones.add(cr.clonedExecContextId());
            final Map<Long, Long> inverse = new HashMap<>();
            cr.taskIdMap().forEach((from, to) -> inverse.put(to, from));
            final Map<String, ExecContextSegment> clone = segmentsByCtx(cr.clonedExecContextId());
            assertEquals(source.keySet(), clone.keySet(), "clone " + round + ": same segments");
            for (Map.Entry<String, ExecContextSegment> en : clone.entrySet()) {
                final ExecContextSegment c = en.getValue();
                final ExecContextSegmentParams back = SegmentClone.remap(c.getExecContextSegmentParams(), inverse, Map.of(), sourceId);
                final Long backFork = c.forkTaskId == null ? null : inverse.get(c.forkTaskId);
                assertEquals(source.get(en.getKey()).structureHash,
                        SegmentStructureHash.structureHash(SegmentParamsConverter.segment(c.lineCtxId, backFork, back)),
                        "clone " + round + ", segment " + en.getKey() + ": mapped back, the structure hashes to the source's stored hash");
            }
            assertTrue(dotDescribed(cr.clonedExecContextId()).contains("post@1 [terminal]"), "clone " + round + ": the join keeps tag terminal");
        }
        assertNotEquals(clones.get(0), clones.get(1));
        invariants.assertAll(sourceId);
        clones.forEach(invariants::assertAll);
    }

    @Test
    public void test_S1_withManyLines_cloneCompletes_bothPassTheInvariants() {
        final int lines = 1000;
        final String items = IntStream.rangeClosed(1, lines).mapToObj(i -> "L" + i).collect(Collectors.joining("\n"));
        final SegmentFixtureShapes.Shape many = new SegmentFixtureShapes.Shape("S1x" + lines, SegmentFixtureShapes.S1.mhsc(), items);
        final Long sourceId = runToFinished(many);
        final ExecContextCloneService.CloneResult cr = execContextCloneService.cloneExecContext(sourceId);
        final Long cloneId = cr.clonedExecContextId();
        assertEquals(EnumsApi.ExecContextState.FINISHED, support.state(cloneId), "the clone is FINISHED");
        assertEquals(1 + lines, segmentsByCtx(cloneId).size(), "the root segment and one per grafted line");
        assertEquals(joinsDescribed(sourceId), joinsDescribed(cloneId), "join records copied");
        invariants.assertAll(sourceId);
        invariants.assertAll(cloneId);
    }
}
