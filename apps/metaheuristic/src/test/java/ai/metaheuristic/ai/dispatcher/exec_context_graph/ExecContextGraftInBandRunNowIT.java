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
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
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

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-6 (batch 6.3b) acceptance IT for the AUTHORED IN-BAND graft. Where the Phase-2 IT
 * ({@link ExecContextGraftRunNowNestedIT}) drives the graft via an explicit out-of-band
 * {@code attachGroup} service call, THIS test proves the DISPATCHER-NATIVE path end-to-end: a
 * {@code .mhsc} that declares a {@code group} and an in-band {@code graft} compiles to a graft-tagged
 * Process (batch 6.3b-ii), and when the enclosing {@code mh.nop} runs, the dispatcher's task-production
 * loop ({@code createTasksForSubProcesses}) expands that node via {@link GraftExpander} instead of
 * producing a task - laying the group body live (RUN_NOW) so the pipeline runs it to FINISHED.
 *
 * <p>Source (inline .mhsc): a group {@code grp1} whose body head is a plain {@code mh.nop} ({@code grpHead}),
 * and a {@code wrapper} ({@code mh.nop}) whose sequential body is a single {@code graft grp1 driver run-now}.
 * The group body is moved out of the main pipeline at compile (batch 6.2) and resolved by name at graft time.
 *
 * <p>Asserts: (a) the group body head {@code grpHead} exists as a real task (only possible if the in-band
 * graft expanded); (b) the graft node itself produced NO task (its {@code mh.graft.*} placeholder is never a
 * task); (c) {@code grpHead} ran (not ERROR); (d) {@code grpHead} nests under {@code wrapper}'s context
 * (own nested context via the Phase-0 rebase - no top-level / 020 leak).
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextGraftInBandRunNowIT extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TaskRepository taskRepository;

    private static final String MHSC_SOURCE = """
            source "test-inband-graft-1.0" {
                group grp1 reset-point grpHead {
                    grpHead := internal mh.nop { }
                }
                wrapper := internal mh.nop {
                    sequential {
                        graft grp1 driver run-now
                    }
                }
            }
            """;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://test-inband-graft", EnumsApi.SourceCodeLang.mhsc, MHSC_SOURCE);
    }

    @AfterEach
    public void afterTest() {
        if (getExecContextForTest() != null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @Test
    public void test_inBandGraft_dispatcherExpandsGroupBody_runsToFinished() {
        // NOTE: preparingSourceCodeService.produceTasksForTest asserts the raw params parse as YAML
        // (SourceCodeParamsYaml), which a .mhsc source is not. Drive the SAME lang-aware production path it
        // uses underneath (createExecContext + produceAndStartAllTasks read the SourceCode entity whose stored
        // lang=mhsc; ExecContextCreatorService parses via scspy.lang) - just without that YAML-only assertion.
        var result = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertFalse(result.isErrorMessages(), "EC creation from the .mhsc source must not error");
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(result.execContext.id, true)));
        final Long ecId = getExecContextForTest().id;
        ExecContextSyncService.getWithSyncVoid(ecId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(Objects.requireNonNull(getExecContextForTest().execContextGraphId), () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(Objects.requireNonNull(getExecContextForTest().execContextTaskStateId), () ->
                                txSupportForTestingService.produceAndStartAllTasks(preparingSourceCodeData.getSourceCode(), ecId))));
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));
        execContextStatusService.resetStatus();

        pipelineRunner.runPipelineToCompletion(ecId, 20);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));
        assertEquals(EnumsApi.ExecContextState.FINISHED.code, getExecContextForTest().getState(),
                "the EC with an in-band graft must run to FINISHED");

        final List<TaskImpl> tasks = taskRepository.findByExecContextIdReadOnly(ecId);

        // (a) the in-band graft expanded: the group body head 'grpHead' exists as a real task
        TaskImpl grpHead = tasks.stream()
                .filter(t -> "grpHead".equals(t.getTaskParamsYaml().task.processCode))
                .findFirst().orElse(null);
        assertNotNull(grpHead, "the in-band graft must have laid the group body head 'grpHead'");

        // (b) the graft NODE itself never became a task (its mh.graft.* placeholder is expanded, not run)
        assertTrue(tasks.stream().noneMatch(t -> t.getTaskParamsYaml().task.processCode.startsWith("mh.graft.")),
                "the graft node must not produce a task of its own");

        // (c) the group body ran (an unresolved input / bad wiring would ERROR and never reach FINISHED)
        assertNotEquals(EnumsApi.TaskExecState.ERROR,
                preparingSourceCodeService.findTaskState(getExecContextForTest(), grpHead.id),
                "grpHead must not be ERROR");

        // (d) grpHead NESTS under the target: its taskContextId derives up to the wrapper via
        //     deriveParentTaskContextId (needed for bind()-input resolution + reset-subtree identity),
        //     and it still runs in its own fresh instance-numbered line (isolated - no 020 cross-branch leak).
        TaskImpl wrapper = tasks.stream()
                .filter(t -> "wrapper".equals(t.getTaskParamsYaml().task.processCode))
                .findFirst().orElseThrow(() -> new AssertionError("wrapper task not found"));
        String grpHeadCtx = grpHead.getTaskParamsYaml().task.taskContextId;
        String wrapperCtx = wrapper.getTaskParamsYaml().task.taskContextId;
        assertNotEquals(wrapperCtx, grpHeadCtx, "grpHead must run in its own line context");
        assertTrue(grpHeadCtx.contains("#"), "grpHead at a fresh instance-numbered ctx: " + grpHeadCtx);
        assertTrue(parentChainContains(grpHeadCtx, wrapperCtx),
                "grpHead ctx=" + grpHeadCtx + " must derive up to the wrapper ctx " + wrapperCtx
                        + " (by-name group body rebased under the target)");
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
