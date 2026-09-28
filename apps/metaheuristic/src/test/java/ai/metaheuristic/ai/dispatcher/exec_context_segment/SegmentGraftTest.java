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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextSegment;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraftService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
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

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 8 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.6): a graft writes one new segment.
 *
 * <p>These cases graft out of band, PLACE_NOW, into a freshly produced S1 ExecContext (not run), under its splitter: the
 * graft needs no run, so they are green from Phase 8. Each checks the one new segment row - its ctx is
 * {@code 1,2#(seed + its own id)} (decision 10), and no line existed at {@code 1,2} before, so the seed is 0 - that no
 * other segment's {@code VERSION} moved and that no join record was written (a line born SKIPPED is never registered).
 * RUN_NOW (reset, Phase 11) and in-band grafts (a run, Phase 10) are pinned by the Phase 1 baseline and added here with
 * those phases.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SegmentGraftTest extends PreparingSourceCode {

    private static final String BASE = "1,2";

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private SegmentInvariantAsserts invariants;
    @Autowired private ExecContextGraftService execContextGraftService;
    @Autowired private ExecContextSegmentLineCtxService lineCtxService;
    @Autowired private ExecContextSegmentRepository segmentRepository;
    @Autowired private ExecContextJoinRepository joinRepository;
    @Autowired private VariableRepository variableRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S1.mhsc());
    }

    /** A produced, started, not run S1 ExecContext. */
    private ExecContextImpl producedS1() {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(SegmentFixtureShapes.S1.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextImpl ec = support.createAndStart(data, SegmentFixtureShapes.S1.items());
        setExecContextForTest(ec);
        return ec;
    }

    private Map<String, ExecContextSegment> segmentsByCtx(Long ecId) {
        return segmentRepository.findIdsByExecContextId(ecId).stream()
                .map(id -> segmentRepository.findById(id).orElseThrow())
                .collect(Collectors.toMap(s -> s.lineCtxId, s -> s, (a, b) -> a, TreeMap::new));
    }

    private static Map<String, Integer> versions(Map<String, ExecContextSegment> segments) {
        final Map<String, Integer> m = new TreeMap<>();
        segments.forEach((ctx, s) -> m.put(ctx, s.version));
        return m;
    }

    private static Map<String, Long> idByProcessCode(List<ExecContextBaselineSupport.TaskRow> rows) {
        final Map<String, Long> m = new HashMap<>();
        rows.forEach(r -> m.put(r.processCode(), r.id()));
        return m;
    }

    private ExecContextGraftService.GraftResult placeNow(Long ecId, Long splitterId,
                                                         List<ExecContextGraftService.InputBinding> inputs,
                                                         List<ExecContextGraftService.OutputMaterialization> outputs) {
        return execContextGraftService.attachGroup(ecId, splitterId, new ExecContextGraftService.GroupRef("line"),
                inputs, outputs, ExecContextGraftService.Driver.PLACE_NOW, "mh.nop");
    }

    /** The segment rows present now that were not in {@code before}. */
    private static List<ExecContextSegment> added(Map<String, ExecContextSegment> after, Map<String, ExecContextSegment> before) {
        return after.values().stream().filter(s -> !before.containsKey(s.lineCtxId)).toList();
    }

    @Test
    public void test_placeNow_outOfBand_writesExactlyOneSegment() {
        final ExecContextImpl ec = producedS1();
        final Long splitterId = idByProcessCode(support.rows(ec.id)).get("splitter");
        final Map<String, ExecContextSegment> before = segmentsByCtx(ec.id);

        final ExecContextGraftService.GraftResult gr = placeNow(ec.id, splitterId, List.of(), List.of());

        final Map<String, ExecContextSegment> after = segmentsByCtx(ec.id);
        final List<ExecContextSegment> added = added(after, before);
        assertEquals(1, added.size(), "PLACE_NOW writes exactly one new segment row, added: " + added.stream().map(s -> s.lineCtxId).toList());
        final ExecContextSegment seg = added.getFirst();

        assertEquals(BASE + "#" + seg.id, gr.lineCtxId(),
                "decision 10: the line ctx is base#(seed + the segment's id); no line existed at " + BASE + ", so the seed is 0");
        assertEquals(gr.lineCtxId(), seg.lineCtxId, "the new segment is the grafted line");
        assertEquals(splitterId, seg.forkTaskId, "the grafted line forks from the graft target");
        assertEquals(versions(before), versions(after).entrySet().stream().filter(e -> before.containsKey(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new)),
                "no segment that existed before the graft is written - its VERSION is unchanged");
        assertEquals(List.of(), joinRepository.findIdsByExecContextId(ec.id),
                "a line born SKIPPED is never registered: no join record is written");
        assertEquals(List.of(), gr.unwiredTails(), "an out-of-band graft reports no unwired tail");

        final Map<String, Long> id = idByProcessCode(support.rows(ec.id).stream()
                .filter(r -> gr.lineCtxId().equals(r.ctx())).toList());
        final ExecContextSegmentParams p = seg.getExecContextSegmentParams();
        assertEquals(1, p.lines.size(), "a flat graft is one line");
        final ExecContextSegmentParams.Line line = p.lines.getFirst();
        assertEquals(gr.lineCtxId(), line.ctx);
        assertEquals(splitterId, line.forkTaskId);
        assertEquals(List.of(id.get("lineHead"), id.get("lineTail")), line.tasks.stream().map(v -> v.taskId).toList(),
                "the line is the group body in chain order");
        assertEquals(id.get("lineHead"), gr.headTaskId(), "the head is the line's first Task");
        assertEquals(id.get("lineHead"), gr.resetPointTaskId(), "the reset point (mh.nop at the line ctx, first created) is found inside the segment");
        assertEquals(Map.of(id.get("lineHead"), EnumsApi.TaskExecState.SKIPPED, id.get("lineTail"), EnumsApi.TaskExecState.SKIPPED), p.states,
                "the line is SKIPPED in its segment");
        assertEquals(Set.of(EnumsApi.TaskExecState.SKIPPED), support.rows(ec.id).stream()
                        .filter(r -> gr.lineCtxId().equals(r.ctx())).map(ExecContextBaselineSupport.TaskRow::state).collect(Collectors.toSet()),
                "the line's Tasks are SKIPPED in MH_TASK");
        assertEquals(List.of(), p.variableStates, "no output was materialized, so no variable-state entry");

        invariants.assertAll(ec.id);
    }

    @Test
    public void test_placeNow_outOfBand_bindingsAndOutputsAtTheLineCtx() {
        final ExecContextImpl ec = producedS1();
        final Long splitterId = idByProcessCode(support.rows(ec.id)).get("splitter");

        final ExecContextGraftService.GraftResult gr = placeNow(ec.id, splitterId,
                List.of(new ExecContextGraftService.InputBinding("boundIn", "bound-value")),
                List.of(new ExecContextGraftService.OutputMaterialization("graftOut", "out-value".getBytes(StandardCharsets.UTF_8))));

        final Variable in = variableRepository.findByNameAndTaskContextIdAndExecContextId("boundIn", gr.lineCtxId(), ec.id);
        assertNotNull(in, "the bound input is written at the line ctx " + gr.lineCtxId());
        final Variable out = variableRepository.findByNameAndTaskContextIdAndExecContextId("graftOut", gr.lineCtxId(), ec.id);
        assertNotNull(out, "the output is materialized at the line ctx " + gr.lineCtxId());

        final ExecContextSegment seg = segmentsByCtx(ec.id).get(gr.lineCtxId());
        assertNotNull(seg, "the grafted line has its segment");
        final List<ExecContextApiData.VariableState> vs = seg.getExecContextSegmentParams().variableStates;
        assertEquals(1, vs.size(), "one variable-state entry - the head's outputs - lands in the line's segment");
        final ExecContextApiData.VariableState entry = vs.getFirst();
        assertEquals(gr.headTaskId(), entry.taskId);
        assertEquals(ec.id, entry.execContextId);
        assertEquals(gr.lineCtxId(), entry.taskContextId);
        assertEquals("lineHead", entry.process);
        assertNotNull(entry.outputs);
        assertEquals(List.of(out.id), entry.outputs.stream().map(o -> o.id).toList(), "the entry names the materialized output");
        assertEquals(List.of(), joinRepository.findIdsByExecContextId(ec.id), "PLACE_NOW writes no join record");

        invariants.assertAll(ec.id);
    }

    @Test
    public void test_placeNow_outOfBand_indicesFromIds_seedReReadAfterInBandNumbering() {
        final ExecContextImpl ec = producedS1();
        final Long splitterId = idByProcessCode(support.rows(ec.id)).get("splitter");

        final ExecContextGraftService.GraftResult g1 = placeNow(ec.id, splitterId, List.of(), List.of());
        final ExecContextGraftService.GraftResult g2 = placeNow(ec.id, splitterId, List.of(), List.of());
        final Map<String, ExecContextSegment> segs = segmentsByCtx(ec.id);
        final long id1 = Objects.requireNonNull(segs.get(g1.lineCtxId()), g1.lineCtxId()).id;
        final long id2 = Objects.requireNonNull(segs.get(g2.lineCtxId()), g2.lineCtxId()).id;
        assertEquals(BASE + "#" + id1, g1.lineCtxId(), "seed 0 + id of the first graft's segment");
        assertEquals(BASE + "#" + id2, g2.lineCtxId(), "the seed is read once: seed 0 + id of the second graft's segment");
        assertNotEquals(g1.lineCtxId(), g2.lineCtxId());

        // in-band numbering (a splitter's next line) continues above every sibling, read from the segments
        final long highest = Math.max(id1, id2);
        assertEquals(BASE + "#" + (highest + 1), lineCtxService.nextSequentialLineCtx(ec.id, BASE),
                "in-band: base#(highest sibling index + 1), the siblings read from the segment rows");

        // ... and it evicted the cached seed: the next out-of-band graft re-reads it as the highest index
        final ExecContextGraftService.GraftResult g3 = placeNow(ec.id, splitterId, List.of(), List.of());
        final long id3 = Objects.requireNonNull(segmentsByCtx(ec.id).get(g3.lineCtxId()), g3.lineCtxId()).id;
        assertEquals(BASE + "#" + (highest + id3), g3.lineCtxId(),
                "after an in-band allocation the seed is re-read: highest existing index " + highest + " + id " + id3);

        invariants.assertAll(ec.id);
    }
}
