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
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCloneService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraftService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 baseline of 041-EXEC-CONTEXT-SEGMENTS-PLAN (section 8.4): {@link ExecContextCloneService#cloneExecContext},
 * pinned on today's whole-ExecContext storage and required to stay green, unchanged, once every ExecContext is
 * segmented.
 *
 * <ul>
 *   <li>a FINISHED {@link SegmentFixtureShapes#S1} ExecContext carrying an out-of-band PLACE_NOW graft - the shape of
 *       an RG STAGE after a manual insert - so it has a reset point;</li>
 *   <li>a FINISHED {@link SegmentFixtureShapes#S6} ExecContext - three nesting levels of lines.</li>
 * </ul>
 * For each: the multiset of (process code, ctx, exec state) is equal in source and clone; the reset point (when there is
 * one) maps through the clone's task-id map to the same (process code, ctx); and the clone's Tasks plus the Variables
 * (name, ctx, init state) of source AND clone are pinned. Everything is read from {@code MH_TASK} /
 * {@code MH_VARIABLE} records and {@link ExecContextCloneService.CloneResult#taskIdMap()} - never the whole-ExecContext
 * records.
 *
 * <p>⚠️ The plan (section 8.4) expected equal Variable counts. Observed: they are not - the clone carries only the
 * Variables its source's variable state references, so a splitter's per-line output and the graft bindings of its lines
 * are not copied. Both sides are pinned as they are today; a fix flips the text.
 *
 * <p>041 decision 10, option (a) - as in {@code ExecContextBaselineGraftTest}: the out-of-band graft's line index is
 * {@code seed + an allocated id}, so the case with the graft asserts its line ctx as "at base {@code 1,2}, index above
 * every earlier sibling" and writes it as {@value #NEW_LINE} in the pinned text (in the rows before they are sorted,
 * and in the reset point). Every other pinned value is unchanged.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextBaselineCloneTest extends PreparingSourceCode {

    private static final String NEW_LINE = "1,2#<new>";

    @Autowired private PreparingSourceCodeInitService preparingSourceCodeInitService;
    @Autowired private ExecContextBaselineSupport support;
    @Autowired private ExecContextGraftService execContextGraftService;
    @Autowired private ExecContextCloneService execContextCloneService;
    @Autowired private VariableRepository variableRepository;

    private static final String S1_WITH_GRAFT_CLONE = """
            cloneState=FINISHED
            resetPoint=lineHead@1,2#<new>
            --- tasks
            branchA@1,3#0=OK
            branchB@1,4#1=OK
            fanout@1=OK
            lineHead@1,2#1=OK
            lineHead@1,2#2=OK
            lineHead@1,2#3=OK
            lineHead@1,2#<new>=SKIPPED
            lineTail@1,2#1=OK
            lineTail@1,2#2=OK
            lineTail@1,2#3=OK
            lineTail@1,2#<new>=SKIPPED
            mh.finish@1=OK
            post@1=OK
            prepare@1=OK
            splitter@1=OK
            --- source variables
            items@1=SET
            lineVal@1,2#1=SET
            lineVal@1,2#2=SET
            lineVal@1,2#3=SET
            lineVal@1,5#1=SET
            lineVal@1,5#2=SET
            lineVal@1,5#3=SET
            --- clone variables
            items@1=SET""";
    private static final String S6_CLONE = """
            cloneState=FINISHED
            resetPoint=none
            --- tasks
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
            split3@1,5,3|2#2=OK
            --- source variables
            items@1=SET
            v1@1,5#1=SET
            v1@1,5#2=SET
            v1@1,7#1=SET
            v1@1,7#2=SET
            v2@1,5,3|1#1=SET
            v2@1,5,3|1#2=SET
            v2@1,5,3|2#1=SET
            v2@1,5,3|2#2=SET
            v2@1,5,6|1#1=SET
            v2@1,5,6|1#2=SET
            v2@1,5,6|2#1=SET
            v2@1,5,6|2#2=SET
            v3@1,5,3,2|1|1#1=SET
            v3@1,5,3,2|1|1#2=SET
            v3@1,5,3,2|1|2#1=SET
            v3@1,5,3,2|1|2#2=SET
            v3@1,5,3,2|2|1#1=SET
            v3@1,5,3,2|2|1#2=SET
            v3@1,5,3,2|2|2#1=SET
            v3@1,5,3,2|2|2#2=SET
            v3@1,5,3,4|1|1#1=SET
            v3@1,5,3,4|1|1#2=SET
            v3@1,5,3,4|1|2#1=SET
            v3@1,5,3,4|1|2#2=SET
            v3@1,5,3,4|2|1#1=SET
            v3@1,5,3,4|2|1#2=SET
            v3@1,5,3,4|2|2#1=SET
            v3@1,5,3,4|2|2#2=SET
            --- clone variables
            items@1=SET""";

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://segment-baseline-s1", EnumsApi.SourceCodeLang.mhsc, SegmentFixtureShapes.S1.mhsc());
    }

    @Test
    public void test_cloneFinishedWithPlaceNowGraft() {
        final Long sourceId = runToFinished(SegmentFixtureShapes.S1).execContextId();
        final List<ExecContextBaselineSupport.TaskRow> before = support.rows(sourceId);
        final Long splitterId = support.rows(sourceId).stream()
                .filter(r -> r.processCode().equals("splitter"))
                .findFirst().orElseThrow().id();
        final ExecContextGraftService.GraftResult gr = execContextGraftService.attachGroup(
                sourceId, splitterId, new ExecContextGraftService.GroupRef("line"), List.of(), List.of(),
                ExecContextGraftService.Driver.PLACE_NOW, "mh.nop");
        support.settle(sourceId);
        final String lineCtxId = assertOutOfBandLineCtx(gr.lineCtxId(), before);

        assertEquals(S1_WITH_GRAFT_CLONE, cloneAndCompare(sourceId, gr.resetPointTaskId(), lineCtxId),
                "clone of S1 with a PLACE_NOW graft: clone state, reset point, Tasks, Variables");
    }

    @Test
    public void test_cloneFinishedNested() {
        final Long sourceId = runToFinished(SegmentFixtureShapes.S6).execContextId();
        assertEquals(S6_CLONE, cloneAndCompare(sourceId, null, null),
                "clone of S6: clone state, Tasks, Variables");
    }

    /** Clones, asserts source and clone agree, and returns the clone's observed content. */
    private String cloneAndCompare(Long sourceId, @Nullable Long resetPointTaskId, @Nullable String outOfBandLineCtx) {
        final String sourceTasks = ExecContextBaselineSupport.describeRows(normalized(support.rows(sourceId), outOfBandLineCtx));
        final String sourceVariables = describeVariables(sourceId);

        final ExecContextCloneService.CloneResult cr = execContextCloneService.cloneExecContext(sourceId);
        final Long cloneId = cr.clonedExecContextId();
        final List<ExecContextBaselineSupport.TaskRow> cloneRows = normalized(support.rows(cloneId), outOfBandLineCtx);

        assertEquals(sourceTasks, ExecContextBaselineSupport.describeRows(cloneRows),
                "the multiset of (process code, ctx, exec state) must be equal in source and clone");
        final String cloneVariables = describeVariables(cloneId);

        String resetPoint = "none";
        if (resetPointTaskId != null) {
            final String sourceKey = keyOf(normalized(support.rows(sourceId), outOfBandLineCtx), resetPointTaskId);
            final Long cloneResetPointId = cr.taskIdMap().get(resetPointTaskId);
            assertNotNull(cloneResetPointId, "the reset point Task #" + resetPointTaskId + " must be in the clone's task-id map");
            resetPoint = keyOf(cloneRows, cloneResetPointId);
            assertEquals(sourceKey, resetPoint, "the reset point must map to the same (process code, ctx) in the clone");
        }

        return "cloneState=" + support.state(cloneId)
                + "\nresetPoint=" + resetPoint
                + "\n--- tasks\n" + ExecContextBaselineSupport.describeRows(cloneRows)
                + "\n--- source variables\n" + sourceVariables
                + "\n--- clone variables\n" + cloneVariables;
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

    /** {@code rows} with the out-of-band line's ctx written as {@link #NEW_LINE}, re-sorted as {@code support.rows} sorts; unchanged for null. */
    private static List<ExecContextBaselineSupport.TaskRow> normalized(List<ExecContextBaselineSupport.TaskRow> rows, @Nullable String lineCtxId) {
        if (lineCtxId == null) {
            return rows;
        }
        return rows.stream()
                .map(r -> lineCtxId.equals(r.ctx()) ? new ExecContextBaselineSupport.TaskRow(r.id(), r.processCode(), NEW_LINE, r.state()) : r)
                .sorted(Comparator.comparing(ExecContextBaselineSupport.TaskRow::key).thenComparing(ExecContextBaselineSupport.TaskRow::id))
                .toList();
    }

    /** One line per Variable of the ExecContext: {@code name@ctx=SET|NULL|NOT_INITED}, sorted. */
    private String describeVariables(Long execContextId) {
        final List<String> lines = new ArrayList<>();
        for (Long id : variableRepository.findAllByExecContextId(Pageable.unpaged(), execContextId)) {
            final Variable v = variableRepository.findById(id).orElse(null);
            if (v == null) {
                continue;
            }
            lines.add(v.name + "@" + v.taskContextId + "=" + (v.nullified ? "NULL" : v.inited ? "SET" : "NOT_INITED"));
        }
        lines.sort(String::compareTo);
        return String.join("\n", lines);
    }

    private static String keyOf(List<ExecContextBaselineSupport.TaskRow> rows, Long taskId) {
        return rows.stream().filter(r -> Objects.equals(r.id(), taskId)).findFirst()
                .map(ExecContextBaselineSupport.TaskRow::key)
                .orElse("missing #" + taskId);
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
}
