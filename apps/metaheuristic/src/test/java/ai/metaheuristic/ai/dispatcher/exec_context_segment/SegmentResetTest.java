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
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraftService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskResetService;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeInitService;
import ai.metaheuristic.ai.yaml.exec_context_segment.ExecContextSegmentParams;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 11 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.6): reset on segments. The Phase 1 reset cases
 * ({@link ExecContextBaselineResetTest}) run unchanged on segments; this class adds what only segments can show - a
 * reset writes only the reset Task's segment, its descendants' segments and the join records on the way up - and the
 * revival of a line born SKIPPED, which registers it with its join.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SegmentResetTest extends PreparingSourceCode {

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private SegmentInvariantAsserts invariants;
    @Autowired private TaskResetService taskResetService;
    @Autowired private ExecContextGraftService execContextGraftService;
    @Autowired private ExecContextSegmentRepository segmentRepository;
    @Autowired private ExecContextJoinRepository joinRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-r1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.R1.mhsc());
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

    private Long taskId(Long ecId, String processCode, String ctx) {
        return support.rows(ecId).stream().filter(r -> r.processCode().equals(processCode) && r.ctx().equals(ctx))
                .findFirst().orElseThrow(() -> new AssertionError("no Task " + processCode + "@" + ctx)).id();
    }

    private ExecContextJoin joinOf(Long ecId, Long joinTaskId) {
        return Objects.requireNonNull(joinRepository.findByExecContextIdAndJoinTaskId(ecId, joinTaskId), "join record of #" + joinTaskId);
    }

    @Test
    public void test_reset_writesOnlyTheResetLinesSegment_theDescendantsSegments_andTheJoinOnTheWay() {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(SegmentFixtureShapes.R1.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextBaselineSupport.StepRun run = support.runStepByStep(() -> {
            final ExecContextImpl ec = support.createAndStart(data, SegmentFixtureShapes.R1.items());
            setExecContextForTest(ec);
            return ec.id;
        }, 80);
        final Long ecId = run.execContextId();
        assertEquals(EnumsApi.ExecContextState.FINISHED, support.state(ecId));
        final Long postId = taskId(ecId, "post", "1");
        final ExecContextJoin postBefore = joinOf(ecId, postId);
        final Map<String, Integer> before = versions(segmentsByCtx(ecId));

        taskResetService.resetTaskAndExecContext(ecId, taskId(ecId, "lineMid", "1,2#2"));

        final Map<String, Integer> after = versions(segmentsByCtx(ecId));
        final Set<String> written = after.keySet().stream().filter(ctx -> !Objects.equals(before.get(ctx), after.get(ctx)))
                .collect(Collectors.toCollection(TreeSet::new));
        assertTrue(written.contains("1,2#2"), "the reset line's segment is written: " + written);
        assertTrue(Set.of("1", "1,2#2").containsAll(written),
                "only the reset line's segment and the root segment (post, mh.finish) may be written, written: " + written);
        assertEquals(before.keySet(), after.keySet(), "no segment is added or removed by a mid-line reset");
        final ExecContextJoin postAfter = joinOf(ecId, postId);
        assertEquals(postBefore.linesRegistered, postAfter.linesRegistered);
        assertEquals(postBefore.linesFinished - 1, postAfter.linesFinished, "the reset line's tail is pending again");
        assertEquals(postBefore.linesDead, postAfter.linesDead);

        invariants.assertAll(ecId);
    }

    @Test
    public void test_resetOfAPlaceNowLine_revivesAndRegistersIt() {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(SegmentFixtureShapes.S1.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextImpl ec = support.createAndStart(data, SegmentFixtureShapes.S1.items());
        setExecContextForTest(ec);
        final Long splitterId = taskId(ec.id, "splitter", "1");
        final Long postId = taskId(ec.id, "post", "1");
        final ExecContextGraftService.GraftResult gr = execContextGraftService.attachGroup(ec.id, splitterId,
                new ExecContextGraftService.GroupRef("line"), List.of(), List.of(), ExecContextGraftService.Driver.PLACE_NOW, "mh.nop");
        assertNull(joinRepository.findByExecContextIdAndJoinTaskId(ec.id, postId), "a PLACE_NOW line is not registered");
        assertFalse(lineOf(ec.id, gr.lineCtxId()).registered);

        taskResetService.resetTaskAndExecContext(ec.id, gr.headTaskId());

        assertTrue(lineOf(ec.id, gr.lineCtxId()).registered, "the revived line registers itself");
        final ExecContextJoin post = joinOf(ec.id, postId);
        assertEquals(1, post.linesRegistered, "post gets a record with the one revived line");
        assertEquals(0, post.linesFinished);
        assertEquals(0, post.linesDead);
        final ExecContextSegmentParams p = segmentsByCtx(ec.id).get(gr.lineCtxId()).getExecContextSegmentParams();
        assertEquals(EnumsApi.TaskExecState.INIT, p.states.get(gr.headTaskId()), "the reset Task is INIT");
        final Long tailId = taskId(ec.id, "lineTail", gr.lineCtxId());
        assertEquals(EnumsApi.TaskExecState.NONE, p.states.get(tailId), "its descendant in the line is NONE");

        invariants.assertAll(ec.id);
    }

    private ExecContextSegmentParams.Line lineOf(Long ecId, String lineCtx) {
        return segmentsByCtx(ecId).get(lineCtx).getExecContextSegmentParams().lines.stream()
                .filter(l -> lineCtx.equals(l.ctx)).findFirst().orElseThrow();
    }
}
