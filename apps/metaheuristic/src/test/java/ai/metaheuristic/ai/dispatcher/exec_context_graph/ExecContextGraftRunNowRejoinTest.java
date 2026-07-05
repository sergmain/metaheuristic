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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-6a finding F1: an in-band RUN-NOW graft whose target is the sequential-block chain TAIL orphans the
 * grafted line - the tail's `-> mh.finish` edge is wired AFTER the sub-block expands, so at graft time the
 * target has no descendants and the grafted line's tail wires into nothing -> the head has no descendant and
 * fails getSubProcesses (994.240) when it runs. Fix: a run-now graft that cannot terminate at graft time
 * rejoins the enclosing block's downstream (its tail is propagated into the block's lastIds).
 * Scenario: `wrapper := mh.nop { sequential { stepA; stepB; graft grp driver run-now } }` (no `at` -> targets
 * the chain tail stepB). Assertion is structural (grpHead's descendant edge), deterministic regardless of the
 * pipeline hanging in the current (buggy) behavior.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextGraftRunNowRejoinTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ExecContextGraphService execContextGraphService;

    private static final String MHSC_SOURCE = """
            source "test-graft-runnow-rejoin-1.0" {
                group grp reset-point grpHead {
                    grpHead := internal mh.nop { }
                }
                wrapper := internal mh.nop {
                    sequential {
                        stepA := internal mh.nop { }
                        stepB := internal mh.nop { }
                        graft grp driver run-now
                    }
                }
            }
            """;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://test-graft-runnow-rejoin", EnumsApi.SourceCodeLang.mhsc, MHSC_SOURCE);
    }

    @AfterEach
    public void afterTest() {
        if (getExecContextForTest() != null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @Test
    public void test_runNowGraftUnderChainTail_rejoinsBlockDownstream_notOrphaned() {
        var result = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertFalse(result.isErrorMessages(), "EC creation from the run-now .mhsc must not error");
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(result.execContext.id, true)));
        final Long ecId = getExecContextForTest().id;
        ExecContextSyncService.getWithSyncVoid(ecId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(Objects.requireNonNull(getExecContextForTest().execContextGraphId), () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(Objects.requireNonNull(getExecContextForTest().execContextTaskStateId), () ->
                                txSupportForTestingService.produceAndStartAllTasks(preparingSourceCodeData.getSourceCode(), ecId))));
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));
        execContextStatusService.resetStatus();

        // Run so `wrapper` executes and the in-band graft expands. In the current (buggy) behavior the
        // orphaned grpHead breaks the graph and the pipeline hangs; the grafted line's edges are set at
        // graft time regardless, so the structural assertion below is deterministic.
        try {
            pipelineRunner.runPipelineToCompletion(ecId, 10);
        } catch (Throwable ignore) {
            // current: orphaned graft head -> broken graph -> no completion. Fixed: completes normally.
        }
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));

        final List<TaskImpl> tasks = taskRepository.findByExecContextIdReadOnly(ecId);
        TaskImpl grpHead = task(tasks, "grpHead");
        assertNotNull(grpHead, "grpHead task must exist (graft expanded when wrapper ran)");

        final Long graphId = Objects.requireNonNull(getExecContextForTest().execContextGraphId);
        final Set<ExecContextData.TaskVertex> grpHeadDescendants =
                execContextGraphService.findDirectDescendants(graphId, grpHead.id);
        // RED->GREEN-3 (desired): a run-now graft that cannot terminate at graft time rejoins the block
        // downstream, so grpHead has a real downstream terminal (not orphaned).
        assertFalse(grpHeadDescendants.isEmpty(),
                "run-now graft under the chain tail must rejoin the block downstream: grpHead has a terminal");
    }

    private static TaskImpl task(List<TaskImpl> tasks, String processCode) {
        return tasks.stream().filter(t -> processCode.equals(t.getTaskParamsYaml().task.processCode)).findFirst().orElse(null);
    }
}
