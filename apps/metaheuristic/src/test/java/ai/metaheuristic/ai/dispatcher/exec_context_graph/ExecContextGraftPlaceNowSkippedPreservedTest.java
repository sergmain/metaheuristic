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
 * Phase-6a finding F2: a PLACE-NOW in-band graft head is marked SKIPPED (a dormant, objection-reopenable
 * line). But when its target task completes, changeTaskStateToInitForChildren resets the SKIPPED child to
 * INIT (it does not preserve SKIPPED), so the dormant line is un-skipped and RUNS. Scenario: a lone place-now
 * graft under `wrapper` (whose `-> mh.finish` edge is pre-wired, so there is no F1 orphan); the only question
 * is whether grpHead survives `wrapper` completing as SKIPPED.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextGraftPlaceNowSkippedPreservedTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TaskRepository taskRepository;

    private static final String MHSC_SOURCE = """
            source "test-graft-placenow-skip-1.0" {
                group grp reset-point grpHead {
                    grpHead := internal mh.nop { }
                }
                wrapper := internal mh.nop {
                    sequential {
                        graft grp driver place-now
                    }
                }
            }
            """;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://test-graft-placenow-skip", EnumsApi.SourceCodeLang.mhsc, MHSC_SOURCE);
    }

    @AfterEach
    public void afterTest() {
        if (getExecContextForTest() != null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @Test
    public void test_placeNowGraftHead_staysSkipped_afterTargetCompletes() {
        var result = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertFalse(result.isErrorMessages(), "EC creation from the place-now .mhsc must not error");
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
                "the place-now graft pipeline must run to FINISHED");

        final List<TaskImpl> tasks = taskRepository.findByExecContextIdReadOnly(ecId);
        TaskImpl grpHead = task(tasks, "grpHead");
        assertNotNull(grpHead, "grpHead task must exist (graft expanded when wrapper ran)");

        final EnumsApi.TaskExecState grpHeadState = preparingSourceCodeService.findTaskState(getExecContextForTest(), grpHead.id);
        // RED->GREEN-3 (desired): a place-now graft head is a dormant, objection-reopenable line;
        // a completing parent must NOT un-skip it. It stays SKIPPED.
        assertEquals(EnumsApi.TaskExecState.SKIPPED, grpHeadState,
                "place-now SKIPPED graft head must stay SKIPPED when its parent (wrapper) completes");
    }

    private static TaskImpl task(List<TaskImpl> tasks, String processCode) {
        return tasks.stream().filter(t -> processCode.equals(t.getTaskParamsYaml().task.processCode)).findFirst().orElse(null);
    }
}
