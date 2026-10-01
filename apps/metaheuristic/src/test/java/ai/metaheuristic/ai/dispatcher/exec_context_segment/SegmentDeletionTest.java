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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTxService;
import ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextChunkedDeletionTxService.Kind;
import ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextChunkedDeletionTxService.Step;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextJoinRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextSegmentRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 041-EXEC-CONTEXT-SEGMENTS-PLAN Phase 14 (section 8.6, decision 3): the records of a deleted ExecContext go in bounded
 * steps - segments, join records, Tasks, Variables - each step one transaction deleting at most its bound, and a deletion
 * stopped after one step completes when resumed.
 *
 * <p>Section 8.6 names S7 (one fork with 1,000 lines); S7 is a DOT-only fixture, so the ExecContext is S1 run with 1,000
 * items - one splitter fork with 1,000 grafted lines of two Tasks each.
 *
 * <p>041 Phase 21: deleting an ExecContext through {@code ExecContextTxService.deleteExecContext} publishes the event whose
 * listener ({@code ExecContextCleanerService}) deletes its segment and join records right away in bounded steps. The
 * service case below therefore removes the ExecContext record directly (no event), so its steps are the only deletion;
 * the listener has its own case.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SegmentDeletionTest extends PreparingSourceCode {

    private static final int LINES = 1000;
    private static final int BOUND = 50;

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private SegmentInvariantAsserts invariants;
    @Autowired private ExecContextChunkedDeletionTxService deletionTxService;
    @Autowired private ExecContextTxService execContextTxService;
    @Autowired private ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository execContextRepository;
    @Autowired private ExecContextSegmentRepository segmentRepository;
    @Autowired private ExecContextJoinRepository joinRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private VariableRepository variableRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S1.mhsc());
    }

    private Long runToFinished(SegmentFixtureShapes.Shape shape) {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(shape.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextBaselineSupport.StepRun run = support.runStepByStep(() -> {
            final ExecContextImpl ec = support.createAndStart(data, shape.items());
            setExecContextForTest(ec);
            return ec.id;
        }, 80);
        assertEquals(EnumsApi.ExecContextState.FINISHED, support.state(run.execContextId()), shape.id() + ": the ExecContext must reach FINISHED");
        return run.execContextId();
    }

    private Map<Kind, Long> counts(Long ecId) {
        final Map<Kind, Long> m = new EnumMap<>(Kind.class);
        m.put(Kind.SEGMENT, segmentRepository.countByExecContextId(ecId));
        m.put(Kind.JOIN, joinRepository.countByExecContextId(ecId));
        m.put(Kind.TASK, (long) taskRepository.findAllByExecContextId(Pageable.unpaged(), ecId).size());
        m.put(Kind.VARIABLE, (long) variableRepository.findAllByExecContextId(Pageable.unpaged(), ecId).size());
        return m;
    }

    @Test
    public void test_S1x1000_boundedSteps_stoppedThenResumed_leaveNothing() {
        final String items = IntStream.rangeClosed(1, LINES).mapToObj(i -> "L" + i).collect(Collectors.joining("\n"));
        final Long ecId = runToFinished(new SegmentFixtureShapes.Shape("S1x" + LINES, SegmentFixtureShapes.S1.mhsc(), items));
        invariants.assertAll(ecId);

        final Map<Kind, Long> before = counts(ecId);
        assertEquals(1L + LINES, before.get(Kind.SEGMENT), "the root segment and one per grafted line");
        assertEquals(7L + 2L * LINES, before.get(Kind.TASK), "S1's seven Tasks and two per line");
        assertTrue(before.get(Kind.JOIN) > 0, "the lines' joins have records");
        assertTrue(before.get(Kind.VARIABLE) > 0, "the run made Variables");

        // guards: a positive bound, and nothing of a live ExecContext
        final IllegalArgumentException badBound = assertThrows(IllegalArgumentException.class, () -> deletionTxService.deleteStep(ecId, 0, ExecContextChunkedDeletionTxService.ALL));
        assertTrue(badBound.getMessage().startsWith("01.922.010"), badBound.getMessage());
        final IllegalStateException live = assertThrows(IllegalStateException.class, () -> deletionTxService.deleteStep(ecId, BOUND, ExecContextChunkedDeletionTxService.ALL));
        assertTrue(live.getMessage().startsWith("01.922.020"), live.getMessage());
        assertEquals(before, counts(ecId), "nothing of a live ExecContext was deleted");

        // the ExecContext record only - no deletion event, so no listener competes with the steps below
        execContextRepository.deleteById(ecId);

        // one step, then stop: exactly one bound of segments, nothing else
        final Step first = deletionTxService.deleteStep(ecId, BOUND, ExecContextChunkedDeletionTxService.ALL);
        assertEquals(new Step(Kind.SEGMENT, BOUND), first, "the first step deletes one bound of segments");
        final Map<Kind, Long> afterFirst = counts(ecId);
        assertEquals(before.get(Kind.SEGMENT) - BOUND, afterFirst.get(Kind.SEGMENT));
        assertEquals(before.get(Kind.TASK), afterFirst.get(Kind.TASK), "no Task deleted while segments remain");

        // resume until nothing is left: every step within its bound, kinds in order, totals equal what there was
        final Map<Kind, Long> deleted = new EnumMap<>(Kind.class);
        deleted.put(Kind.SEGMENT, (long) BOUND);
        Kind last = Kind.SEGMENT;
        int steps = 1;
        Step step;
        while ((step = deletionTxService.deleteStep(ecId, BOUND, ExecContextChunkedDeletionTxService.ALL)) != null) {
            steps++;
            assertTrue(step.deleted() > 0 && step.deleted() <= BOUND, "step " + steps + " deleted " + step.deleted() + ", bound " + BOUND);
            assertTrue(step.kind().ordinal() >= last.ordinal(), "step " + steps + ": " + step.kind() + " after " + last);
            last = step.kind();
            deleted.merge(step.kind(), (long) step.deleted(), Long::sum);
            assertTrue(steps < 10_000, "the deletion must end");
        }
        assertEquals(before, deleted, "every record of every kind deleted exactly once");
        assertEquals(Map.of(Kind.SEGMENT, 0L, Kind.JOIN, 0L, Kind.TASK, 0L, Kind.VARIABLE, 0L), counts(ecId), "nothing left");
        assertNull(deletionTxService.deleteStep(ecId, BOUND, ExecContextChunkedDeletionTxService.ALL), "a finished deletion stays finished");
    }

    /** Phase 21: the deletion listener takes the segment and join records of a deleted ExecContext; Tasks stay for the periodic cleaner. */
    @Test
    public void test_S1x200_deleteExecContext_listenerDeletesSegmentsAndJoins() {
        final int lines = 200;
        final String items = IntStream.rangeClosed(1, lines).mapToObj(i -> "L" + i).collect(Collectors.joining("\n"));
        final Long ecId = runToFinished(new SegmentFixtureShapes.Shape("S1x" + lines, SegmentFixtureShapes.S1.mhsc(), items));
        final Map<Kind, Long> before = counts(ecId);
        assertEquals(1L + lines, before.get(Kind.SEGMENT), "the root segment and one per grafted line");
        assertTrue(before.get(Kind.JOIN) > 0, "the lines' joins have records");

        execContextTxService.deleteExecContext(ecId);

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(60)).pollInterval(java.time.Duration.ofMillis(300))
                .until(() -> segmentRepository.countByExecContextId(ecId) == 0 && joinRepository.countByExecContextId(ecId) == 0);
        assertEquals(before.get(Kind.TASK), counts(ecId).get(Kind.TASK), "the listener leaves the Tasks to the periodic cleaner");
    }
}
