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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 1 baseline of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.4): task production and scheduling, pinned on
 * today's whole-ExecContext storage and required to stay green, unchanged, once every ExecContext is segmented.
 *
 * <p>For each shape of {@link SegmentFixtureShapes} the ExecContext runs to FINISHED one scheduler step at a time
 * ({@link ExecContextBaselineSupport#runStepByStep}), and two things are pinned:
 * <ul>
 *   <li>the final set of (process code, {@code taskContextId}, exec state) of every Task;</li>
 *   <li>the Tasks the scheduler handed out at each step, read from the suspended internal-task queue.</li>
 * </ul>
 * Both come from {@code MH_TASK} records and the real queue only - never the graph DOT or the task-state record.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextBaselineProductionTest extends PreparingSourceCode {

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;

    private static final String S1_FINAL = """
            branchA@1,3#0=OK
            branchB@1,4#1=OK
            fanout@1=OK
            lineHead@1,2#1=OK
            lineHead@1,2#2=OK
            lineHead@1,2#3=OK
            lineTail@1,2#1=OK
            lineTail@1,2#2=OK
            lineTail@1,2#3=OK
            mh.finish@1=OK
            post@1=OK
            prepare@1=OK
            splitter@1=OK""";
    private static final String S1_STEPS = """
            0: prepare@1
            1: fanout@1
            2: branchA@1,3#0, branchB@1,4#1
            3: splitter@1
            4: lineHead@1,2#1, lineHead@1,2#2, lineHead@1,2#3
            5: lineTail@1,2#1, lineTail@1,2#2, lineTail@1,2#3
            6: post@1
            7: mh.finish@1""";
    private static final String S2_FINAL = """
            inner@1,3#1=OK
            inner@1,3#2=OK
            inner@1,3#3=OK
            leafHead@1,3,2|1#1=OK
            leafHead@1,3,2|1#2=OK
            leafHead@1,3,2|1#3=OK
            leafHead@1,3,2|2#1=OK
            leafHead@1,3,2|2#2=OK
            leafHead@1,3,2|2#3=OK
            leafHead@1,3,2|3#1=OK
            leafHead@1,3,2|3#2=OK
            leafHead@1,3,2|3#3=OK
            lineHead@1,3#1=OK
            lineHead@1,3#2=OK
            lineHead@1,3#3=OK
            mh.finish@1=OK
            outer@1=OK
            post@1=OK""";
    private static final String S2_STEPS = """
            0: outer@1
            1: lineHead@1,3#1, lineHead@1,3#2, lineHead@1,3#3
            2: inner@1,3#1, inner@1,3#2, inner@1,3#3
            3: leafHead@1,3,2|1#1, leafHead@1,3,2|1#2, leafHead@1,3,2|1#3, leafHead@1,3,2|2#1, leafHead@1,3,2|2#2, leafHead@1,3,2|2#3, leafHead@1,3,2|3#1, leafHead@1,3,2|3#2, leafHead@1,3,2|3#3
            4: post@1
            5: mh.finish@1""";
    private static final String S6_FINAL = """
            l1Head@1,5#1=OK
            l1Head@1,5#2=OK
            l2Head@1,5,3|1#1=OK
            l2Head@1,5,3|1#2=OK
            l2Head@1,5,3|2#1=OK
            l2Head@1,5,3|2#2=OK
            l3Head@1,5,3,2|1|1#1=OK
            l3Head@1,5,3,2|1|1#2=OK
            l3Head@1,5,3,2|1|2#1=OK
            l3Head@1,5,3,2|1|2#2=OK
            l3Head@1,5,3,2|2|1#1=OK
            l3Head@1,5,3,2|2|1#2=OK
            l3Head@1,5,3,2|2|2#1=OK
            l3Head@1,5,3,2|2|2#2=OK
            mh.finish@1=OK
            post@1=OK
            split1@1=OK
            split2@1,5#1=OK
            split2@1,5#2=OK
            split3@1,5,3|1#1=OK
            split3@1,5,3|1#2=OK
            split3@1,5,3|2#1=OK
            split3@1,5,3|2#2=OK""";
    private static final String S6_STEPS = """
            0: split1@1
            1: l1Head@1,5#1, l1Head@1,5#2
            2: split2@1,5#1, split2@1,5#2
            3: l2Head@1,5,3|1#1, l2Head@1,5,3|1#2, l2Head@1,5,3|2#1, l2Head@1,5,3|2#2
            4: split3@1,5,3|1#1, split3@1,5,3|1#2, split3@1,5,3|2#1, split3@1,5,3|2#2
            5: l3Head@1,5,3,2|1|1#1, l3Head@1,5,3,2|1|1#2, l3Head@1,5,3,2|1|2#1, l3Head@1,5,3,2|1|2#2, l3Head@1,5,3,2|2|1#1, l3Head@1,5,3,2|2|1#2, l3Head@1,5,3,2|2|2#1, l3Head@1,5,3,2|2|2#2
            6: post@1
            7: mh.finish@1""";

    private record Observed(String finalStates, String steps) {
    }

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S1.mhsc());
    }

    @Test
    public void test_S1_finalStatesAndHandOutOrder() {
        final Observed o = run(SegmentFixtureShapes.S1);
        assertEquals(S1_FINAL, o.finalStates(), "S1: final (process code @ ctx = exec state) of every Task");
        assertEquals(S1_STEPS, o.steps(), "S1: the Tasks handed out at each scheduler step");
    }

    @Test
    public void test_S2_finalStatesAndHandOutOrder() {
        final Observed o = run(SegmentFixtureShapes.S2);
        assertEquals(S2_FINAL, o.finalStates(), "S2: final (process code @ ctx = exec state) of every Task");
        assertEquals(S2_STEPS, o.steps(), "S2: the Tasks handed out at each scheduler step");
    }

    @Test
    public void test_S6_finalStatesAndHandOutOrder() {
        final Observed o = run(SegmentFixtureShapes.S6);
        assertEquals(S6_FINAL, o.finalStates(), "S6: final (process code @ ctx = exec state) of every Task");
        assertEquals(S6_STEPS, o.steps(), "S6: the Tasks handed out at each scheduler step");
    }

    private Observed run(SegmentFixtureShapes.Shape shape) {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(shape.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextBaselineSupport.StepRun run = support.runStepByStep(() -> {
            final ExecContextImpl ec = support.createAndStart(data, shape.items());
            // the harness's per-test ExecContext pointer follows this run
            setExecContextForTest(ec);
            return ec.id;
        }, 80);
        final Long ecId = run.execContextId();
        final List<List<String>> steps = run.steps();

        assertEquals(EnumsApi.ExecContextState.FINISHED, support.state(ecId), shape.id() + ": the ExecContext must reach FINISHED");
        support.assertEveryRunTaskHandedOutOnce(ecId, steps, shape.id());
        return new Observed(
                ExecContextBaselineSupport.describeRows(support.rows(ecId)),
                ExecContextBaselineSupport.describeSteps(steps));
    }
}
