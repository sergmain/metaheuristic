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
import ai.metaheuristic.ai.dispatcher.task.TaskResetService;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 1 baseline of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.4): reset of a FINISHED ExecContext via
 * {@link TaskResetService#resetTaskAndExecContext}, pinned on today's whole-ExecContext storage and required to stay
 * green, unchanged, once every ExecContext is segmented.
 *
 * <ul>
 *   <li>a mid-line Task of {@link SegmentFixtureShapes#R1} ({@code lineMid} of the second line);</li>
 *   <li>a top-level Task upstream of the splitter ({@code prepare}) - the reset reaches the splitter, whose dynamically
 *       created lines the reset removes and the re-run creates again.</li>
 * </ul>
 * Each case pins, in one text: the ExecContext state right after the reset, the Tasks the reset left unfinished (the
 * reopened set, from {@code MH_TASK}), the Tasks handed out per scheduler step of the re-run, the final ExecContext
 * state, and the final (process code, ctx, exec state) of every Task. The reset runs under the suspended
 * internal-task queue, so nothing it reopens runs unobserved.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextBaselineResetTest extends PreparingSourceCode {

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private TaskResetService taskResetService;

    private static final String MID_LINE_OBSERVED = """
            execContextAfterReset=STARTED
            reopened=lineMid@1,2#2, lineTail@1,2#2, mh.finish@1, post@1
            --- steps
            0: lineMid@1,2#2
            1: lineTail@1,2#2
            2: post@1
            3: mh.finish@1
            execContextAtEnd=FINISHED
            --- final
            lineHead@1,2#1=OK
            lineHead@1,2#2=OK
            lineMid@1,2#1=OK
            lineMid@1,2#2=OK
            lineTail@1,2#1=OK
            lineTail@1,2#2=OK
            mh.finish@1=OK
            post@1=OK
            prepare@1=OK
            splitter@1=OK""";
    /**
     * ⚠️ Pins a defect of today's reset: the reset deletes the grafted lines ({@code 1,2#n}) and their variables, but
     * not the splitter's own per-line variable {@code lineVal} at {@code 1,3#n}; the re-run of the splitter then hits the
     * {@code MH_VARIABLE} unique index ({@code 994.300}) and ends ERROR, creating no new lines. A fix flips this text.
     */
    private static final String UPSTREAM_OF_SPLITTER_OBSERVED = """
            execContextAfterReset=STARTED
            reopened=mh.finish@1, post@1, prepare@1, splitter@1
            --- steps
            0: prepare@1
            1: splitter@1
            2: post@1
            3: mh.finish@1
            execContextAtEnd=FINISHED
            --- final
            lineHead@1,2#1=SKIPPED
            lineHead@1,2#2=SKIPPED
            lineMid@1,2#1=SKIPPED
            lineMid@1,2#2=SKIPPED
            lineTail@1,2#1=SKIPPED
            lineTail@1,2#2=SKIPPED
            mh.finish@1=OK
            post@1=OK
            prepare@1=OK
            splitter@1=ERROR""";

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-r1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.R1.mhsc());
    }

    @Test
    public void test_resetMidLineTask() {
        assertEquals(MID_LINE_OBSERVED, resetAndRerun("lineMid", "1,2#2"),
                "reset of lineMid@1,2#2: state after reset, reopened, steps of the re-run, final states");
    }

    @Test
    public void test_resetTaskUpstreamOfSplitter() {
        assertEquals(UPSTREAM_OF_SPLITTER_OBSERVED, resetAndRerun("prepare", "1"),
                "reset of prepare@1: state after reset, reopened, steps of the re-run, final states");
    }

    private String resetAndRerun(String processCode, String ctx) {
        final Long ecId = runToFinished(SegmentFixtureShapes.R1).execContextId();
        final Long taskId = taskId(support.rows(ecId), processCode, ctx);

        final AtomicReference<EnumsApi.ExecContextState> afterReset = new AtomicReference<>();
        final AtomicReference<String> reopened = new AtomicReference<>();
        final ExecContextBaselineSupport.StepRun rerun = support.runStepByStep(() -> {
            taskResetService.resetTaskAndExecContext(ecId, taskId);
            afterReset.set(support.state(ecId));
            reopened.set(support.rows(ecId).stream()
                    .filter(r -> !r.isFinished())
                    .map(ExecContextBaselineSupport.TaskRow::key)
                    .sorted()
                    .collect(Collectors.joining(", ")));
            return ecId;
        }, 80);

        return "execContextAfterReset=" + afterReset.get()
                + "\nreopened=" + reopened.get()
                + "\n--- steps\n" + ExecContextBaselineSupport.describeSteps(rerun.steps())
                + "\nexecContextAtEnd=" + support.state(ecId)
                + "\n--- final\n" + ExecContextBaselineSupport.describeRows(support.rows(ecId));
    }

    private ExecContextBaselineSupport.StepRun runToFinished(SegmentFixtureShapes.Shape shape) {
        final PreparingData.PreparingSourceCodeData data =
                preparingSourceCodeInitService.beforePreparingSourceCode(shape.mhsc(), EnumsApi.SourceCodeLang.mhsc);
        final ExecContextBaselineSupport.StepRun run = support.runStepByStep(() -> {
            final ExecContextImpl ec = support.createAndStart(data, shape.items());
            // the harness's per-test ExecContext pointer follows this run
            setExecContextForTest(ec);
            return ec.id;
        }, 80);
        assertEquals(EnumsApi.ExecContextState.FINISHED, support.state(run.execContextId()), shape.id() + ": the ExecContext must reach FINISHED");
        support.assertEveryRunTaskHandedOutOnce(run.execContextId(), run.steps(), shape.id());
        return run;
    }

    private static Long taskId(List<ExecContextBaselineSupport.TaskRow> rows, String processCode, String ctx) {
        final List<ExecContextBaselineSupport.TaskRow> found = rows.stream()
                .filter(r -> r.processCode().equals(processCode) && r.ctx().equals(ctx))
                .toList();
        assertEquals(1, found.size(), "exactly one Task " + processCode + "@" + ctx + " expected");
        return found.getFirst().id();
    }
}
