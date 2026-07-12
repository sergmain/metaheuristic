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
 * Problem-2 regression guard: in a {@code run-now} grafted FLAT line, when the head task FAILS
 * (here {@code aHead} fails on an absent required input {@code ghostVar}, unrelated to the splitter's
 * per-line binding), its downstream sibling {@code bDownstream} (which consumes {@code aHead}'s output
 * {@code aOut}) must NOT be dispatched onto the broken upstream - it must be held (NONE) or SKIPPED,
 * never executed-and-left-ERROR. The head's failure is correctly a recoverable ERROR_WITH_RECOVERY (or
 * terminal ERROR once retries are exhausted); either way the downstream must not run on a missing input.
 * Terminal-ERROR -> descendants SKIPPED is separately covered by the skip-propagation logic/tests.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextGraftRunNowDownstreamSkipTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TaskRepository taskRepository;

    private static final String MHSC_SOURCE = """
            source "test-runnow-downstream-skip-1.0" {
                seed := internal mh.evaluation {
                    -> items: ext=".txt"
                    meta expression = "items = 'HELLO'"
                }
                group grp reset-point aHead {
                    aHead := internal mh.evaluation {
                        <- ghostVar
                        -> aOut: ext=".txt"
                        meta expression = "aOut = ghostVar"
                    }
                    bDownstream := internal mh.evaluation {
                        <- aOut
                        -> bOut: ext=".txt"
                        meta expression = "bOut = aOut"
                    }
                }
                splitter := internal mh.batch-line-splitter {
                    <- items
                    meta number-of-lines-per-task = "1",
                         variable-for-splitting = "items",
                         output-is-dynamic = "true",
                         output-variable = "lineVal",
                         is-array = "false"
                    sequential {
                        graft grp driver run-now
                    }
                }
            }
            """;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://test-runnow-downstream-skip", EnumsApi.SourceCodeLang.mhsc, MHSC_SOURCE);
    }

    @AfterEach
    public void afterTest() {
        if (getExecContextForTest() != null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @Test
    public void test_runNowGraft_headFailure_skipsDownstream() {
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

        // the head fails, so the EC enters ERROR; the runner fail()s / times out - swallow and inspect states
        try {
            pipelineRunner.runPipelineToCompletion(ecId, 20);
        }
        catch (Throwable th) {
            // expected: the head ERRORs
        }
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));

        final List<TaskImpl> tasks = taskRepository.findByExecContextIdReadOnly(ecId);
        TaskImpl aHead = tasks.stream()
                .filter(t -> "aHead".equals(t.getTaskParamsYaml().task.processCode))
                .findFirst().orElse(null);
        TaskImpl bDownstream = tasks.stream()
                .filter(t -> "bDownstream".equals(t.getTaskParamsYaml().task.processCode))
                .findFirst().orElse(null);
        assertNotNull(aHead, "the graft must have laid the head 'aHead'");
        assertNotNull(bDownstream, "the graft must have laid the downstream 'bDownstream'");

        final EnumsApi.TaskExecState aHeadState = preparingSourceCodeService.findTaskState(getExecContextForTest(), aHead.id);
        final EnumsApi.TaskExecState bState = preparingSourceCodeService.findTaskState(getExecContextForTest(), bDownstream.id);

        assertTrue(aHeadState == EnumsApi.TaskExecState.ERROR || aHeadState == EnumsApi.TaskExecState.ERROR_WITH_RECOVERY,
                "aHead must be in a failed state on the absent input ghostVar, but was: " + aHeadState);

        // problem 2: a downstream task of a FAILED/recovering upstream in a run-now flat line must NOT
        // be dispatched - it must be held (NONE) or SKIPPED, never executed (neither OK nor ERROR).
        assertNotEquals(EnumsApi.TaskExecState.ERROR, bState,
                "bDownstream must NOT run-and-error on a broken upstream (aHead=" + aHeadState + ") - but was: " + bState);
        assertNotEquals(EnumsApi.TaskExecState.OK, bState,
                "bDownstream must NOT run-and-succeed on a broken upstream (aHead=" + aHeadState + ") - but was: " + bState);
    }
}
