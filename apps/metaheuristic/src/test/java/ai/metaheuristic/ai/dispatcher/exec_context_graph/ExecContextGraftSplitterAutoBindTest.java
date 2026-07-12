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
 * Splitter-nested graft auto-bind: a dynamic {@code mh.batch-line-splitter} produces a per-line variable
 * {@code lineVal}; its sequential body is a single {@code graft grp driver run-now} with NO bind(). The
 * group {@code grp} declares NO formal inputs; its head {@code grpHead} consumes {@code lineVal} DIRECTLY
 * by name (the mhdg-rg req-rung-0 / store-req shape). Because the graft lays the head at a fresh ISOLATED
 * line ctx (line isolation), the enclosing splitter's per-line {@code lineVal} is not resolvable up the
 * ancestry from that sibling ctx, so the head cannot obtain its input.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextGraftSplitterAutoBindTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TaskRepository taskRepository;

    private static final String MHSC_SOURCE = """
            source "test-splitter-autobind-1.0" {
                seed := internal mh.evaluation {
                    -> items: ext=".txt"
                    meta expression = "items = 'HELLO'"
                }
                group grp reset-point grpHead {
                    grpHead := internal mh.evaluation {
                        <- lineVal
                        -> echoed: ext=".txt"
                        meta expression = "echoed = lineVal"
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
        return new SourceCodeUriAndLang("inline://test-splitter-autobind", EnumsApi.SourceCodeLang.mhsc, MHSC_SOURCE);
    }

    @AfterEach
    public void afterTest() {
        if (getExecContextForTest() != null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @Test
    public void test_splitterNestedGraft_autoBindsPerLineOutput() {
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

        // while the bug is present the EC cannot complete (grpHead can't resolve lineVal), so the
        // runner times out; swallow it and inspect the resulting state directly.
        try {
            pipelineRunner.runPipelineToCompletion(ecId, 20);
        }
        catch (Throwable th) {
            // expected while the bug is present
        }
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));

        final List<TaskImpl> tasks = taskRepository.findByExecContextIdReadOnly(ecId);

        TaskImpl grpHead = tasks.stream()
                .filter(t -> "grpHead".equals(t.getTaskParamsYaml().task.processCode))
                .findFirst().orElse(null);
        assertNotNull(grpHead, "the in-band graft must have laid the group body head 'grpHead'");

        // DESIRED behavior: the splitter's per-line lineVal is auto-bound into the grafted line ctx, so
        // grpHead resolves its input and the EC runs to FINISHED.
        assertEquals(EnumsApi.ExecContextState.FINISHED.code, getExecContextForTest().getState(),
                "EC must reach FINISHED once the splitter's per-line lineVal is auto-bound into the grafted line ctx");
        assertNotEquals(EnumsApi.TaskExecState.ERROR,
                preparingSourceCodeService.findTaskState(getExecContextForTest(), grpHead.id),
                "grpHead must not be ERROR - it resolved lineVal via the splitter auto-bind");
    }
}
