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
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.MhInternalTaskPipelineRunner;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.api.EnumsApi;
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
 * Phase-6a follow-on (d): `graft ... at <idRef>` target resolution (Option A: block-local). A graft node in
 * a sequential block names a target sibling via `at`; the graft head must parent on THAT named sibling, not
 * on the v1 default (the chain predecessor = parentTaskIds.get(0)). Scenario: wrapper's body is a sequential
 * block [stepA, stepB, graft grp at stepA]; the chain predecessor at the graft is stepB, but `at stepA`
 * re-targets it, so the two behaviors differ by grpHead's recorded parent.
 *
 * This test asserts ONLY the resolved target (grpHead's parent), not pipeline completion: the v1 chain-tail
 * default orphans the grafted line (the tail's downstream is wired after the sub-block expands, so the graft
 * head has no descendant and breaks getSubProcesses) - a separate in-band-graft wiring issue. `at stepA`
 * targets an earlier sibling that already has a descendant, which is exactly why `at` is needed.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextGraftAtTargetTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TaskRepository taskRepository;

    private static final String MHSC_SOURCE = """
            source "test-graft-at-1.0" {
                group grp reset-point grpHead {
                    grpHead := internal mh.nop { }
                }
                wrapper := internal mh.nop {
                    sequential {
                        stepA := internal mh.nop { }
                        stepB := internal mh.nop { }
                        graft grp driver run-now at stepA
                    }
                }
            }
            """;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://test-graft-at", EnumsApi.SourceCodeLang.mhsc, MHSC_SOURCE);
    }

    @AfterEach
    public void afterTest() {
        if (getExecContextForTest() != null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @Test
    public void test_graftAt_targetsNamedSibling_notChainPredecessor() {
        var result = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertFalse(result.isErrorMessages(), "EC creation from the `at` .mhsc must not error");
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(result.execContext.id, true)));
        final Long ecId = getExecContextForTest().id;
        ExecContextSyncService.getWithSyncVoid(ecId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(Objects.requireNonNull(getExecContextForTest().execContextGraphId), () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(Objects.requireNonNull(getExecContextForTest().execContextTaskStateId), () ->
                                txSupportForTestingService.produceAndStartAllTasks(preparingSourceCodeData.getSourceCode(), ecId))));
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));
        execContextStatusService.resetStatus();

        // Run the pipeline so `wrapper` executes and expands the in-band graft; grpHead is created with its
        // parent recorded regardless of whether the pipeline then completes (the chain-tail default orphans
        // it - see the class javadoc). We observe the RESOLVED TARGET, not completion.
        try {
            pipelineRunner.runPipelineToCompletion(ecId, 8);
        } catch (Throwable ignore) {
            // v1 chain-tail default breaks the graph; the graft target is still recorded on grpHead.
        }
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));

        final List<TaskImpl> tasks = taskRepository.findByExecContextIdReadOnly(ecId);
        TaskImpl stepA = task(tasks, "stepA");
        TaskImpl stepB = task(tasks, "stepB");
        TaskImpl grpHead = task(tasks, "grpHead");
        assertNotNull(stepA, "stepA task must exist");
        assertNotNull(stepB, "stepB task must exist");
        assertNotNull(grpHead, "grpHead task must exist (graft expanded when wrapper ran)");

        final List<Long> grpHeadParents = grpHead.getTaskParamsYaml().task.init.parentTaskIds;
        // RED->GREEN-3 (desired behavior): `at stepA` re-targets the graft to the NAMED sibling stepA,
        // not the chain predecessor stepB.
        assertEquals(List.of(stepA.id), grpHeadParents,
                "graft must honor `at stepA`: grpHead parents on the named sibling stepA, not chain-tail stepB");
    }

    private static TaskImpl task(List<TaskImpl> tasks, String processCode) {
        return tasks.stream().filter(t -> processCode.equals(t.getTaskParamsYaml().task.processCode)).findFirst().orElse(null);
    }
}
