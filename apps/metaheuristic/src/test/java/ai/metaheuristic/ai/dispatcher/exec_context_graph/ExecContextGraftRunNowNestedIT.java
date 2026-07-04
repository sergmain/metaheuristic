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

package ai.metaheuristic.ai.dispatcher.exec_context_graph;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.MhInternalTaskPipelineRunner;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.ContextUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-2 RUN_NOW acceptance IT for the runtime graft primitive (025-MHSC-DSL-V2-PLAN, Phase 2).
 *
 * <p>Where the Phase-1 IT ({@link ExecContextGraftAttachGroupIT}) proves PLACE_NOW on a STOPPED EC,
 * this one proves the RUN_NOW driver end-to-end on a genuinely FINISHED EC: it grafts a body whose
 * root is an INNER dynamic subprocess (a splitter) and asserts the splitter RE-EXPANDS when driven,
 * with its children born at their own resolvable contexts.
 *
 * <p><b>Why a FINISHED EC.</b> RUN_NOW delegates to {@code TaskResetService.resetTaskAndExecContext},
 * which only transitions {@code FINISHED -> STARTED}; a STOPPED EC (the PLACE_NOW target) is the wrong
 * shape. So the test first drives the pipeline all the way to FINISHED via
 * {@link MhInternalTaskPipelineRunner} before grafting.
 *
 * <p><b>Why this source.</b> {@code graft-run-now-nested.yaml} nests the proven internal-only permute
 * mechanism (mh.permute-variables -> mh.nop leaf, cf. {@code variables-as-not-present.yaml}) inside a
 * plain {@code mh.nop} wrapper ({@code outerWrapper}, cf. the {@code nopObjectivesWrapper} shape).
 * The graft target is {@code outerWrapper}; its borrowed body root is therefore the INNER splitter
 * {@code mh.permute-variables} — targeting the splitter directly would borrow the flat leaf and prove
 * nothing about nested re-expansion.
 *
 * <p><b>Flow.</b> produce+start -> run to FINISHED -> graft RUN_NOW under outerWrapper (no outputs;
 * no input bindings — var1/var2 resolve by walking parent contexts to root, the Phase-0 rebase
 * property) -> the EC reopens to STARTED -> drive again to FINISHED -> assert (a) the grafted head is
 * the inner splitter, (b) it re-expanded (head + >=1 dynamically-created child among the newly-created
 * tasks), (c) each child derives its parent chain up to the grafted line ctx (own context, no 020
 * cross-branch leak), and (d) no child is ERROR (its inputs resolved).
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextGraftRunNowNestedIT extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private ExecContextGraftService execContextGraftService;
    @Autowired private TaskRepository taskRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(
                "/source_code/yaml/variables/graft-run-now-nested.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @AfterEach
    public void afterTest() {
        if (getExecContextForTest() != null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @Test
    public void test_attachGroup_runNow_innerSplitterReExpands_childrenResolvableOwnContexts() {
        // ===== Phase 1: build a genuinely FINISHED EC (resetTaskAndExecContext only does FINISHED->STARTED). =====
        preparingSourceCodeService.produceTasksForTest(resolveSourceCode(getSourceCodeAndLang()), preparingSourceCodeData);
        execContextStatusService.resetStatus();
        final Long ecId = getExecContextForTest().id;

        pipelineRunner.runPipelineToCompletion(ecId, 20);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));
        assertEquals(EnumsApi.ExecContextState.FINISHED.code, getExecContextForTest().getState(),
                "first run must reach FINISHED before the RUN_NOW graft");

        // Snapshot the pre-graft task-id universe: everything the RUN_NOW re-drive creates is 'new'.
        Set<Long> beforeGraft = taskRepository.findByExecContextIdReadOnly(ecId).stream()
                .map(t -> t.id).collect(Collectors.toSet());

        // Target = the plain 'outerWrapper' (mh.nop) whose borrowed body root is the INNER splitter
        // (mh.permute-variables). Targeting the inner splitter directly would borrow the flat leaf.
        Long targetTaskId = null;
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            if ("outerWrapper".equals(t.getTaskParamsYaml().task.processCode)) {
                targetTaskId = t.id;
                break;
            }
        }
        assertNotNull(targetTaskId, "outerWrapper target task not found in the FINISHED EC");

        // ===== Phase 2: graft the borrowed body (the inner splitter) under the target, RUN_NOW. =====
        // No input bindings: the grafted mh.permute-variables resolves var1/var2 by walking parent
        // contexts up to root (the Phase-0 rebase property). No outputs: RUN_NOW tasks produce their own.
        ExecContextGraftService.GraftResult gr = execContextGraftService.attachGroup(
                ecId, targetTaskId,
                ExecContextGraftService.GroupRef.fromTargetSubProcesses(),
                List.of(),
                List.of(),
                ExecContextGraftService.Driver.RUN_NOW);
        assertNotNull(gr.headTaskId(), "graft must return a head task id");
        assertNotNull(gr.lineCtxId(), "graft must return a fresh line ctx");

        // The borrowed body root IS the inner splitter.
        TaskImpl head = taskRepository.findByIdReadOnly(gr.headTaskId());
        assertNotNull(head);
        assertEquals("mh.permute-variables", head.getTaskParamsYaml().task.processCode,
                "the grafted head must be the inner dynamic subprocess (splitter)");

        // RUN_NOW reopened the EC (FINISHED->STARTED) via resetTaskAndExecContext.
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));
        assertEquals(EnumsApi.ExecContextState.STARTED.code, getExecContextForTest().getState(),
                "RUN_NOW must reopen the EC to STARTED");

        // ===== Phase 3: drive the reopened EC; the grafted inner splitter must re-expand end-to-end. =====
        pipelineRunner.runPipelineToCompletion(ecId, 20);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));
        assertEquals(EnumsApi.ExecContextState.FINISHED.code, getExecContextForTest().getState(),
                "the reopened EC must re-reach FINISHED after the grafted line runs");

        // ===== Assertions: inner splitter re-expanded; children resolvable + own contexts. =====
        Set<Long> afterDrive = taskRepository.findByExecContextIdReadOnly(ecId).stream()
                .map(t -> t.id).collect(Collectors.toSet());
        Set<Long> newIds = new HashSet<>(afterDrive);
        newIds.removeAll(beforeGraft);

        assertTrue(newIds.contains(gr.headTaskId()), "grafted head must be among the newly-created tasks");
        // head + >=1 dynamically-created child => the inner splitter re-expanded.
        assertTrue(newIds.size() >= 2,
                "the inner splitter must re-expand into children: expected head + >=1 child, got " + newIds.size());

        final String lineCtx = gr.lineCtxId();
        int childrenChecked = 0;
        for (Long id : newIds) {
            if (id.equals(gr.headTaskId())) {
                continue;
            }
            TaskImpl child = taskRepository.findByIdReadOnly(id);
            assertNotNull(child);
            String childCtx = child.getTaskParamsYaml().task.taskContextId;

            // OWN CONTEXTS: the child's parent chain reaches the grafted line ctx (no cross-branch leak
            // into the original outerWrapper subtree — that would derive to a different ctx).
            assertTrue(parentChainContains(childCtx, lineCtx),
                    "grafted child #" + id + " ctx=" + childCtx + " must derive up to the grafted line ctx "
                            + lineCtx + " (own context, no 020 leak)");

            // RESOLVABLE INPUTS: an unresolved input would ERROR (171.520) and the EC would never have
            // reached FINISHED; assert non-ERROR explicitly too.
            EnumsApi.TaskExecState st = preparingSourceCodeService.findTaskState(getExecContextForTest(), id);
            assertNotEquals(EnumsApi.TaskExecState.ERROR, st,
                    "grafted child #" + id + " must not be ERROR (its inputs must resolve)");
            childrenChecked++;
        }
        assertTrue(childrenChecked >= 1, "at least one re-expanded child must be verified");
    }

    /** Walk childCtx up via deriveParentTaskContextId (the variable-resolution walk); true iff ancestorCtx appears. */
    private static boolean parentChainContains(String childCtx, String ancestorCtx) {
        String c = childCtx;
        int guard = 0;
        while (c != null && guard++ < 64) {
            if (ancestorCtx.equals(c)) {
                return true;
            }
            c = ContextUtils.deriveParentTaskContextId(c);
        }
        return false;
    }
}
