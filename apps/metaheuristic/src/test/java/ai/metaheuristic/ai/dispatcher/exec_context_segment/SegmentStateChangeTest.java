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
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraftService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraftTxService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeInitService;
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
 * Phase 9 of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.6): a task-state change writes only the segments of the Tasks it
 * touches and the join records of the lines whose tails moved; every other segment's {@code VERSION} is unchanged.
 *
 * <p>A produced, not run S1 ExecContext. A live line is grafted with the Phase 8 primitive
 * ({@link ExecContextGraftTxService#createGroupTasksTx} with {@code registerTails}): a PRE_INIT line under the splitter,
 * registered with its derived join {@code post} - the state an out-of-band RUN_NOW graft leaves before its reset runs.
 * States are changed through {@link ExecContextSegmentStateTxService#updateTaskExecStates}, the call both task-state
 * entry points make.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SegmentStateChangeTest extends PreparingSourceCode {

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private SegmentInvariantAsserts invariants;
    @Autowired private ExecContextGraftService execContextGraftService;
    @Autowired private ExecContextGraftTxService graftTxService;
    @Autowired private ExecContextSegmentLineCtxService lineCtxService;
    @Autowired private ExecContextSegmentStateTxService segmentStateTxService;
    @Autowired private ExecContextSegmentRepository segmentRepository;
    @Autowired private ExecContextJoinRepository joinRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S1.mhsc());
    }

    private ExecContextImpl producedS1() {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(SegmentFixtureShapes.S1.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextImpl ec = support.createAndStart(data, SegmentFixtureShapes.S1.items());
        setExecContextForTest(ec);
        return ec;
    }

    private Map<Long, String> processById(Long ecId) {
        return support.rows(ecId).stream().collect(Collectors.toMap(ExecContextBaselineSupport.TaskRow::id, ExecContextBaselineSupport.TaskRow::processCode));
    }

    private Long idOf(Long ecId, String processCode) {
        return support.rows(ecId).stream().filter(r -> processCode.equals(r.processCode())).findFirst().orElseThrow().id();
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

    /** The ctxs whose segment VERSION moved between two snapshots. */
    private static Set<String> written(Map<String, Integer> before, Map<String, Integer> after) {
        return after.keySet().stream().filter(ctx -> !Objects.equals(before.get(ctx), after.get(ctx))).collect(Collectors.toCollection(TreeSet::new));
    }

    /** A live line of group {@code line} under the splitter, registered with its derived join. Returns [head, tail] and its ctx. */
    private record LiveLine(String ctx, Long head, Long tail) {}

    private LiveLine liveLine(Long ecId, Long splitterId) {
        final ExecContextGraftService.GraftSetup gs = execContextGraftService.graftSetup(ecId, splitterId, new ExecContextGraftService.GroupRef("line"));
        final ExecContextSegmentLineCtxService.AllocatedLine line = lineCtxService.allocateOutOfBand(ecId, gs.lineBaseCtxId());
        final List<Long> created = new ArrayList<>();
        graftTxService.createGroupTasksTx(gs.sec(), gs.ecd(), splitterId, line.lineCtxId(), gs.rootProcessCode(), List.of(),
                new ArrayList<>(), created, ExecContextSegmentTxService.SegmentStart.own(line.segmentId()), true);
        assertEquals(2, created.size(), "the line is lineHead -> lineTail");
        return new LiveLine(line.lineCtxId(), created.get(0), created.get(1));
    }

    private ExecContextOperationStatusWithTaskList change(Long ecId, Long taskId, String ctx, EnumsApi.TaskExecState state) {
        return segmentStateTxService.updateTaskExecStates(ecId, List.of(new TaskData.TaskWithStateAndTaskContextId(taskId, state, ctx)));
    }

    private ExecContextJoin joinOf(Long ecId, Long joinTaskId) {
        return Objects.requireNonNull(joinRepository.findByExecContextIdAndJoinTaskId(ecId, joinTaskId), "join record of #" + joinTaskId);
    }

    private static EnumsApi.TaskExecState stateIn(ExecContextSegment s, Long taskId) {
        return s.getExecContextSegmentParams().states.getOrDefault(taskId, EnumsApi.TaskExecState.NONE);
    }

    @Test
    public void test_errorInTheRootChain_killsTheRestOfIt_otherSegmentsUnchanged() {
        final ExecContextImpl ec = producedS1();
        final Long splitterId = idOf(ec.id, "splitter");
        final ExecContextGraftService.GraftResult placed = execContextGraftService.attachGroup(ec.id, splitterId,
                new ExecContextGraftService.GroupRef("line"), List.of(), List.of(), ExecContextGraftService.Driver.PLACE_NOW, "mh.nop");
        final Map<String, Integer> before = versions(segmentsByCtx(ec.id));

        final ExecContextOperationStatusWithTaskList status = change(ec.id, idOf(ec.id, "prepare"), "1", EnumsApi.TaskExecState.ERROR);

        final Map<Long, String> process = processById(ec.id);
        assertEquals(Set.of("fanout", "splitter"), status.childrenTasks.stream().map(t -> process.get(t.taskId)).collect(Collectors.toSet()),
                "the rest of the root chain is killed, except post (tag terminal) and mh.finish (the leaf)");
        final Map<String, ExecContextSegment> after = segmentsByCtx(ec.id);
        assertEquals(Set.of("1"), written(before, versions(after)),
                "only the root segment is written; the PLACE_NOW line under the killed splitter was SKIPPED already");
        final ExecContextSegment root = after.get("1");
        assertEquals(EnumsApi.TaskExecState.ERROR, stateIn(root, idOf(ec.id, "prepare")));
        assertEquals(EnumsApi.TaskExecState.SKIPPED, stateIn(root, idOf(ec.id, "fanout")));
        assertEquals(EnumsApi.TaskExecState.SKIPPED, stateIn(root, splitterId));
        assertNotEquals(EnumsApi.TaskExecState.SKIPPED, stateIn(root, idOf(ec.id, "post")));
        assertNotEquals(EnumsApi.TaskExecState.SKIPPED, stateIn(root, idOf(ec.id, "mh.finish")));
        assertEquals(List.of(), joinRepository.findIdsByExecContextId(ec.id), "no line is registered: no join record");
        assertNotNull(placed.lineCtxId());

        invariants.assertAll(ec.id);
    }

    @Test
    public void test_liveLine_headOkWritesOnlyItsSegment_tailOkRaisesItsJoin() {
        final ExecContextImpl ec = producedS1();
        final Long splitterId = idOf(ec.id, "splitter");
        final Long postId = idOf(ec.id, "post");
        final LiveLine line = liveLine(ec.id, splitterId);
        assertEquals(1, joinOf(ec.id, postId).linesRegistered, "the live line is registered with post");

        Map<String, Integer> before = versions(segmentsByCtx(ec.id));
        change(ec.id, line.head(), line.ctx(), EnumsApi.TaskExecState.OK);
        assertEquals(Set.of(line.ctx()), written(before, versions(segmentsByCtx(ec.id))), "a head's change writes only its segment");
        assertEquals(0, joinOf(ec.id, postId).linesFinished, "a head is not a tail: the join does not move");

        before = versions(segmentsByCtx(ec.id));
        change(ec.id, line.tail(), line.ctx(), EnumsApi.TaskExecState.OK);
        assertEquals(Set.of(line.ctx()), written(before, versions(segmentsByCtx(ec.id))), "a tail's change writes only its segment ...");
        final ExecContextJoin post = joinOf(ec.id, postId);
        assertEquals(1, post.linesFinished, "... and its join: one finished line");
        assertEquals(0, post.linesDead);
        assertEquals(EnumsApi.TaskExecState.OK, stateIn(segmentsByCtx(ec.id).get(line.ctx()), line.tail()));

        invariants.assertAll(ec.id);
    }

    @Test
    public void test_liveLine_errorKillsTheRestOfTheLine_joinCountsItDead() {
        final ExecContextImpl ec = producedS1();
        final Long splitterId = idOf(ec.id, "splitter");
        final Long postId = idOf(ec.id, "post");
        final LiveLine line = liveLine(ec.id, splitterId);
        final Map<String, Integer> before = versions(segmentsByCtx(ec.id));

        final ExecContextOperationStatusWithTaskList status = change(ec.id, line.head(), line.ctx(), EnumsApi.TaskExecState.ERROR);

        assertEquals(Set.of(line.tail()), status.childrenTasks.stream().map(t -> t.taskId).collect(Collectors.toSet()),
                "the rest of the line is killed");
        assertEquals(Set.of(line.ctx()), written(before, versions(segmentsByCtx(ec.id))), "only the line's segment is written");
        final ExecContextJoin post = joinOf(ec.id, postId);
        assertEquals(1, post.linesDead, "the join counts the dead line");
        assertEquals(0, post.linesFinished);
        assertNotEquals(EnumsApi.TaskExecState.SKIPPED, stateIn(segmentsByCtx(ec.id).get("1"), postId),
                "post keeps a live parent - the splitter - and is tag terminal");

        invariants.assertAll(ec.id);
    }
}
