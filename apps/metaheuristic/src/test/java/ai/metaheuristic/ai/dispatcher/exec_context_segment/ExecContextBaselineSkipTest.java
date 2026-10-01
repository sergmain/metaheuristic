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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 1 baseline of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.4): SKIPPED propagation after an ERROR, pinned on
 * today's whole-ExecContext storage and required to stay green, unchanged, once every ExecContext is segmented.
 *
 * <ul>
 *   <li>{@link SegmentFixtureShapes#K1}: an ERROR in one line - exactly which Tasks become SKIPPED, and the join is
 *       still handed out because the other line is live;</li>
 *   <li>{@link SegmentFixtureShapes#K2}: both grafted lines fail while their fork succeeded - observed: the plain
 *       join still runs;</li>
 *   <li>{@link SegmentFixtureShapes#K4}: both branches of a static fork fail while the fork succeeded - observed:
 *       the plain join still runs;</li>
 *   <li>{@link SegmentFixtureShapes#K3}: the join's only parent fails and the join is {@code tag terminal};</li>
 *   <li>{@link SegmentFixtureShapes#K5}: the join's only parent fails and the join is plain - the join, what
 *       follows it, the {@code tag terminal} Task and the leaf {@code mh.finish}.</li>
 * </ul>
 * Each case pins, in one text: the final ExecContext state, the Tasks handed out per scheduler step, and the final
 * (process code, ctx, exec state) of every Task - all from {@code MH_TASK} records and the real internal-task queue.
 *
 * <p>The failing Task writes to an undeclared variable, so it fails the same way on every run; the dispatcher first
 * records ERROR_WITH_RECOVERY and turns it into ERROR (no {@code tries}) when a scan finds nothing else ready.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextBaselineSkipTest extends PreparingSourceCode {

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;

    private static final String K1_OBSERVED = """
            execContext=FINISHED
            --- steps
            0: wrapper@1
            1: boom@1,2#1, good1@1,3#1
            2: good2@1,3#1
            3: join@1
            4: post@1
            5: mh.finish@1
            --- final
            afterBoom@1,2#1=SKIPPED
            boom@1,2#1=ERROR
            good1@1,3#1=OK
            good2@1,3#1=OK
            join@1=OK
            mh.finish@1=OK
            post@1=OK
            wrapper@1=OK""";
    private static final String K2_OBSERVED = """
            execContext=FINISHED
            --- steps
            0: wrapper@1
            1: boom@1,2#1, boom@1,2#2
            2: join@1
            3: afterJoin@1
            4: post@1
            5: mh.finish@1
            --- final
            afterBoom@1,2#1=SKIPPED
            afterBoom@1,2#2=SKIPPED
            afterJoin@1=OK
            boom@1,2#1=ERROR
            boom@1,2#2=ERROR
            join@1=OK
            mh.finish@1=OK
            post@1=OK
            wrapper@1=OK""";
    private static final String K3_OBSERVED = """
            execContext=FINISHED
            --- steps
            0: boom@1
            1: join@1
            2: afterJoin@1
            3: mh.finish@1
            --- final
            afterJoin@1=OK
            boom@1=ERROR
            join@1=OK
            mh.finish@1=OK""";
    private static final String K4_OBSERVED = """
            execContext=FINISHED
            --- steps
            0: fork@1
            1: boomA@1,2#0, boomB@1,3#1
            2: join@1
            3: afterJoin@1
            4: post@1
            5: mh.finish@1
            --- final
            afterJoin@1=OK
            boomA@1,2#0=ERROR
            boomB@1,3#1=ERROR
            fork@1=OK
            join@1=OK
            mh.finish@1=OK
            post@1=OK""";
    private static final String K5_OBSERVED = """
            execContext=FINISHED
            --- steps
            0: boom@1
            1: post@1
            2: mh.finish@1
            --- final
            afterJoin@1=SKIPPED
            boom@1=ERROR
            join@1=SKIPPED
            mh.finish@1=OK
            post@1=OK""";

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-k1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.K1.mhsc());
    }

    @Test
    public void test_K1_errorInOneLine_joinStillHandedOutWhileOtherLineLive() {
        assertEquals(K1_OBSERVED, run(SegmentFixtureShapes.K1), "K1: ExecContext state, hand-out per step, final Task states");
    }

    @Test
    public void test_K2_bothGraftedLinesDead_forkLive_joinRuns() {
        assertEquals(K2_OBSERVED, run(SegmentFixtureShapes.K2), "K2: ExecContext state, hand-out per step, final Task states");
    }

    @Test
    public void test_K3_onlyParentDead_terminalJoin() {
        assertEquals(K3_OBSERVED, run(SegmentFixtureShapes.K3), "K3: ExecContext state, hand-out per step, final Task states");
    }

    @Test
    public void test_K4_bothStaticBranchesDead_plainJoin() {
        assertEquals(K4_OBSERVED, run(SegmentFixtureShapes.K4), "K4: ExecContext state, hand-out per step, final Task states");
    }

    @Test
    public void test_K5_onlyParentDead_plainJoin() {
        assertEquals(K5_OBSERVED, run(SegmentFixtureShapes.K5), "K5: ExecContext state, hand-out per step, final Task states");
    }

    private String run(SegmentFixtureShapes.Shape shape) {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(shape.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextBaselineSupport.StepRun run = support.runStepByStep(() -> {
            final ExecContextImpl ec = support.createAndStart(data, shape.items());
            // the harness's per-test ExecContext pointer follows this run
            setExecContextForTest(ec);
            return ec.id;
        }, 80);
        support.assertEveryRunTaskHandedOutOnce(run.execContextId(), run.steps(), shape.id());
        return "execContext=" + support.state(run.execContextId())
                + "\n--- steps\n" + ExecContextBaselineSupport.describeSteps(run.steps())
                + "\n--- final\n" + ExecContextBaselineSupport.describeRows(support.rows(run.execContextId()));
    }
}
