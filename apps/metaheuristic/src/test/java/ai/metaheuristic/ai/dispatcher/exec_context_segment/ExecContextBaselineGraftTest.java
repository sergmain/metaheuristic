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
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraftService;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeInitService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.ContextUtils;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 baseline of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.4): grafting, pinned on today's whole-ExecContext
 * storage and required to stay green, unchanged, once every ExecContext is segmented.
 *
 * <ul>
 *   <li>PLACE_NOW out of band ({@link ExecContextGraftService#attachGroup}) into a FINISHED S1 ExecContext, under
 *       its splitter - the shape of an RG manual insert: the new Tasks, their states, the line ctx, the reset
 *       point, and that no Task which existed before changed state (the join {@code post} included);</li>
 *   <li>RUN_NOW out of band into a FINISHED S1 ExecContext: the reopened set, the ExecContext STARTED, the Tasks
 *       handed out per step, the final states, the ExecContext FINISHED again;</li>
 *   <li>in-band PLACE_NOW ({@link SegmentFixtureShapes#S1_PLACE_NOW});</li>
 *   <li>F1 ({@link SegmentFixtureShapes#S5}): a RUN_NOW graft under a chain tail runs and its enclosing join
 *       completes.</li>
 * </ul>
 * In-band RUN_NOW is the graft every production shape uses, so {@link ExecContextBaselineProductionTest} pins it.
 *
 * <p>Observations come from {@code MH_TASK} records, {@link ExecContextGraftService.GraftResult} values, the
 * ExecContext state and the real internal-task queue only.
 *
 * <p>041 decision 10, option (a): an out-of-band graft's line index is {@code seed + an allocated id}, so the two
 * out-of-band cases assert the line ctx as "at base {@code 1,2}, index above every earlier sibling" and then write it
 * as {@value #NEW_LINE} in every pinned text (in the rows before they are sorted, and in the step keys). Every other
 * pinned value - and the in-band cases, whose lines keep sequential numbers - is unchanged.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextBaselineGraftTest extends PreparingSourceCode {

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private ExecContextGraftService execContextGraftService;

    /** The out-of-band grafted line's ctx as written in the pinned texts (decision 10, option a). */
    private static final String NEW_LINE = "1,2#<new>";

    private static final String PLACE_NOW_OUT_OF_BAND = """
            lineCtxId=1,2#<new>
            head=lineHead@1,2#<new>
            resetPoint=lineHead@1,2#<new>
            unwiredTails=0
            execContext=FINISHED
            new:
            lineHead@1,2#<new>=SKIPPED
            lineTail@1,2#<new>=SKIPPED""";

    private static final String RUN_NOW_OUT_OF_BAND = """
            lineCtxId=1,2#<new>
            head=lineHead@1,2#<new>
            resetPoint=lineHead@1,2#<new>
            unwiredTails=0
            execContextAfterGraft=STARTED
            reopened=lineHead@1,2#<new>, lineTail@1,2#<new>, mh.finish@1, post@1
            execContextAtEnd=FINISHED""";
    private static final String RUN_NOW_OUT_OF_BAND_STEPS = """
            0: lineHead@1,2#<new>
            1: lineTail@1,2#<new>
            2: post@1
            3: mh.finish@1""";
    private static final String RUN_NOW_OUT_OF_BAND_FINAL = """
            branchA@1,3#0=OK
            branchB@1,4#1=OK
            fanout@1=OK
            lineHead@1,2#1=OK
            lineHead@1,2#2=OK
            lineHead@1,2#3=OK
            lineHead@1,2#<new>=OK
            lineTail@1,2#1=OK
            lineTail@1,2#2=OK
            lineTail@1,2#3=OK
            lineTail@1,2#<new>=OK
            mh.finish@1=OK
            post@1=OK
            prepare@1=OK
            splitter@1=OK""";

    private static final String IN_BAND_PLACE_NOW_STEPS = """
            0: prepare@1
            1: fanout@1
            2: branchA@1,3#0, branchB@1,4#1
            3: splitter@1
            4: post@1
            5: mh.finish@1""";
    private static final String IN_BAND_PLACE_NOW_FINAL = """
            branchA@1,3#0=OK
            branchB@1,4#1=OK
            fanout@1=OK
            lineHead@1,2#1=SKIPPED
            lineHead@1,2#2=SKIPPED
            lineHead@1,2#3=SKIPPED
            lineTail@1,2#1=SKIPPED
            lineTail@1,2#2=SKIPPED
            lineTail@1,2#3=SKIPPED
            mh.finish@1=OK
            post@1=OK
            prepare@1=OK
            splitter@1=OK""";

    private static final String F1_STEPS = """
            0: wrapper@1
            1: a@1,3#0
            2: gHead@1,3,2|0#1
            3: gTail@1,3,2|0#1
            4: post@1
            5: mh.finish@1""";
    private static final String F1_FINAL = """
            a@1,3#0=OK
            gHead@1,3,2|0#1=OK
            gTail@1,3,2|0#1=OK
            mh.finish@1=OK
            post@1=OK
            wrapper@1=OK""";

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S1.mhsc());
    }

    @Test
    public void test_placeNow_outOfBand_intoFinished() {
        final Long ecId = runToFinished(SegmentFixtureShapes.S1).execContextId();
        final List<ExecContextBaselineSupport.TaskRow> before = support.rows(ecId);
        final Long splitterId = taskId(before, "splitter");

        final ExecContextGraftService.GraftResult gr = execContextGraftService.attachGroup(
                ecId, splitterId, new ExecContextGraftService.GroupRef("line"), List.of(), List.of(),
                ExecContextGraftService.Driver.PLACE_NOW, "mh.nop");
        support.settle(ecId);
        final String lineCtxId = assertOutOfBandLineCtx(gr.lineCtxId(), before);

        final List<ExecContextBaselineSupport.TaskRow> after = support.rows(ecId);
        assertEquals(ExecContextBaselineSupport.describeRows(before), ExecContextBaselineSupport.describeRows(existing(after, before)),
                "PLACE_NOW: no Task that existed before the graft may change state - the join 'post' included");

        final List<ExecContextBaselineSupport.TaskRow> afterN = normalized(after, lineCtxId);
        final String observed = "lineCtxId=" + NEW_LINE
                + "\nhead=" + keyOf(afterN, gr.headTaskId())
                + "\nresetPoint=" + keyOf(afterN, gr.resetPointTaskId())
                + "\nunwiredTails=" + gr.unwiredTails().size()
                + "\nexecContext=" + support.state(ecId)
                + "\nnew:\n" + ExecContextBaselineSupport.describeRows(added(afterN, before));
        assertEquals(PLACE_NOW_OUT_OF_BAND, observed, "PLACE_NOW out of band: graft result and the new Tasks");
    }

    @Test
    public void test_runNow_outOfBand_intoFinished() {
        final Long ecId = runToFinished(SegmentFixtureShapes.S1).execContextId();
        final List<ExecContextBaselineSupport.TaskRow> before = support.rows(ecId);
        final Long splitterId = taskId(before, "splitter");

        final AtomicReference<ExecContextGraftService.GraftResult> grRef = new AtomicReference<>();
        final AtomicReference<String> reopened = new AtomicReference<>();
        final AtomicReference<EnumsApi.ExecContextState> stateAfterGraft = new AtomicReference<>();
        final ExecContextBaselineSupport.StepRun second = support.runStepByStep(() -> {
            grRef.set(execContextGraftService.attachGroup(
                    ecId, splitterId, new ExecContextGraftService.GroupRef("line"), List.of(), List.of(),
                    ExecContextGraftService.Driver.RUN_NOW, "mh.nop"));
            stateAfterGraft.set(support.state(ecId));
            reopened.set(normalized(support.rows(ecId), grRef.get().lineCtxId()).stream()
                    .filter(r -> !r.isFinished())
                    .map(ExecContextBaselineSupport.TaskRow::key)
                    .sorted()
                    .collect(Collectors.joining(", ")));
            return ecId;
        }, 80);

        final ExecContextGraftService.GraftResult gr = grRef.get();
        final String lineCtxId = assertOutOfBandLineCtx(gr.lineCtxId(), before);
        final List<ExecContextBaselineSupport.TaskRow> after = normalized(support.rows(ecId), lineCtxId);
        final String observed = "lineCtxId=" + NEW_LINE
                + "\nhead=" + keyOf(after, gr.headTaskId())
                + "\nresetPoint=" + keyOf(after, gr.resetPointTaskId())
                + "\nunwiredTails=" + gr.unwiredTails().size()
                + "\nexecContextAfterGraft=" + stateAfterGraft.get()
                + "\nreopened=" + reopened.get()
                + "\nexecContextAtEnd=" + support.state(ecId);
        assertEquals(RUN_NOW_OUT_OF_BAND, observed, "RUN_NOW out of band: graft result, reopened Tasks, ExecContext states");
        assertEquals(RUN_NOW_OUT_OF_BAND_STEPS, ExecContextBaselineSupport.describeSteps(normalizedSteps(second.steps(), lineCtxId)),
                "RUN_NOW out of band: the Tasks handed out at each scheduler step after the graft");
        assertEquals(RUN_NOW_OUT_OF_BAND_FINAL, ExecContextBaselineSupport.describeRows(after),
                "RUN_NOW out of band: final (process code @ ctx = exec state) of every Task");
    }

    @Test
    public void test_placeNow_inBand() {
        final ExecContextBaselineSupport.StepRun run = runToFinished(SegmentFixtureShapes.S1_PLACE_NOW);
        assertEquals(IN_BAND_PLACE_NOW_STEPS, ExecContextBaselineSupport.describeSteps(run.steps()),
                "in-band PLACE_NOW: the Tasks handed out at each scheduler step");
        assertEquals(IN_BAND_PLACE_NOW_FINAL, ExecContextBaselineSupport.describeRows(support.rows(run.execContextId())),
                "in-band PLACE_NOW: final (process code @ ctx = exec state) of every Task");
    }

    @Test
    public void test_F1_runNowGraftUnderChainTail() {
        final ExecContextBaselineSupport.StepRun run = runToFinished(SegmentFixtureShapes.S5);
        assertEquals(F1_STEPS, ExecContextBaselineSupport.describeSteps(run.steps()),
                "F1: the Tasks handed out at each scheduler step");
        assertEquals(F1_FINAL, ExecContextBaselineSupport.describeRows(support.rows(run.execContextId())),
                "F1: final (process code @ ctx = exec state) of every Task");
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

    private static Long taskId(List<ExecContextBaselineSupport.TaskRow> rows, String processCode) {
        final List<ExecContextBaselineSupport.TaskRow> found = rows.stream().filter(r -> r.processCode().equals(processCode)).toList();
        assertEquals(1, found.size(), "exactly one Task with process code '" + processCode + "' expected");
        return found.getFirst().id();
    }

    /**
     * Decision 10, option (a): the out-of-band line lies at base {@code 1,2}, its index above every sibling that existed
     * before the graft. Returns the line ctx.
     */
    private static String assertOutOfBandLineCtx(String lineCtxId, List<ExecContextBaselineSupport.TaskRow> before) {
        assertEquals("1,2", ContextUtils.getLevel(lineCtxId), "the out-of-band line is at base 1,2: " + lineCtxId);
        final long index = Long.parseLong(Objects.requireNonNull(ContextUtils.getPath(lineCtxId), lineCtxId));
        final long highest = before.stream()
                .filter(r -> "1,2".equals(ContextUtils.getLevel(r.ctx())))
                .map(r -> ContextUtils.getPath(r.ctx()))
                .filter(Objects::nonNull)
                .mapToLong(Long::parseLong)
                .max().orElse(0);
        assertTrue(index > highest, "the out-of-band line index " + index + " must lie above every earlier sibling (highest " + highest + ")");
        return lineCtxId;
    }

    /** {@code rows} with the out-of-band line's ctx written as {@link #NEW_LINE}, re-sorted as {@code support.rows} sorts. */
    private static List<ExecContextBaselineSupport.TaskRow> normalized(List<ExecContextBaselineSupport.TaskRow> rows, String lineCtxId) {
        return rows.stream()
                .map(r -> lineCtxId.equals(r.ctx()) ? new ExecContextBaselineSupport.TaskRow(r.id(), r.processCode(), NEW_LINE, r.state()) : r)
                .sorted(Comparator.comparing(ExecContextBaselineSupport.TaskRow::key).thenComparing(ExecContextBaselineSupport.TaskRow::id))
                .toList();
    }

    /** Step keys with the out-of-band line's ctx written as {@link #NEW_LINE}, each step re-sorted. */
    private static List<List<String>> normalizedSteps(List<List<String>> steps, String lineCtxId) {
        final String suffix = "@" + lineCtxId;
        return steps.stream()
                .map(step -> step.stream()
                        .map(k -> k.endsWith(suffix) ? k.substring(0, k.length() - lineCtxId.length()) + NEW_LINE : k)
                        .sorted()
                        .toList())
                .toList();
    }

    private static String keyOf(List<ExecContextBaselineSupport.TaskRow> rows, @Nullable Long taskId) {
        if (taskId == null) {
            return "null";
        }
        return rows.stream().filter(r -> r.id().equals(taskId)).findFirst()
                .map(ExecContextBaselineSupport.TaskRow::key)
                .orElse("missing #" + taskId);
    }

    private static List<ExecContextBaselineSupport.TaskRow> existing(List<ExecContextBaselineSupport.TaskRow> after, List<ExecContextBaselineSupport.TaskRow> before) {
        final Set<Long> ids = before.stream().map(ExecContextBaselineSupport.TaskRow::id).collect(Collectors.toSet());
        return after.stream().filter(r -> ids.contains(r.id())).toList();
    }

    private static List<ExecContextBaselineSupport.TaskRow> added(List<ExecContextBaselineSupport.TaskRow> after, List<ExecContextBaselineSupport.TaskRow> before) {
        final Set<Long> ids = before.stream().map(ExecContextBaselineSupport.TaskRow::id).collect(Collectors.toSet());
        return after.stream().filter(r -> !ids.contains(r.id())).toList();
    }
}
