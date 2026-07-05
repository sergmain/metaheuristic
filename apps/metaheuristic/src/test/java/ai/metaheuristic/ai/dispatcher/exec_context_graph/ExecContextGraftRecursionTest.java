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
 * Phase-6a recursion IT: a graft NESTED inside a group body expands. outer's body is an mh.nop wrapper
 * whose sequential block grafts a second group inner; wrapper grafts outer. Chain: wrapper -> graft(outer)
 * -> outerHead(nop) runs -> graft(inner) -> innerHead(nop). Proves a graft-in-a-group-body works via RUNTIME
 * expansion (outerHead is laid, and when it runs the wired SubProcessesTxService re-enters createTasksForSubProcesses
 * with the live expander and grafts inner). Termination is structural (inner has no graft = base case).
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextGraftRecursionTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TaskRepository taskRepository;

    private static final String MHSC_SOURCE = """
            source "test-graft-recursion-1.0" {
                group inner reset-point innerHead {
                    innerHead := internal mh.nop { }
                }
                group outer reset-point outerHead {
                    outerHead := internal mh.nop {
                        sequential {
                            graft inner driver run-now
                        }
                    }
                }
                wrapper := internal mh.nop {
                    sequential {
                        graft outer driver run-now
                    }
                }
            }
            """;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://test-graft-recursion", EnumsApi.SourceCodeLang.mhsc, MHSC_SOURCE);
    }

    @AfterEach
    public void afterTest() {
        if (getExecContextForTest() != null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @Test
    public void test_graftInsideGroupBody_recurses_terminatesAndRunsToFinished() {
        var result = preparingSourceCodeService.createExecContextForTest(preparingSourceCodeData);
        assertFalse(result.isErrorMessages(), "EC creation from the recursion .mhsc must not error");
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(result.execContext.id, true)));
        final Long ecId = getExecContextForTest().id;
        ExecContextSyncService.getWithSyncVoid(ecId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(Objects.requireNonNull(getExecContextForTest().execContextGraphId), () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(Objects.requireNonNull(getExecContextForTest().execContextTaskStateId), () ->
                                txSupportForTestingService.produceAndStartAllTasks(preparingSourceCodeData.getSourceCode(), ecId))));
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));
        execContextStatusService.resetStatus();

        pipelineRunner.runPipelineToCompletion(ecId, 30);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));
        assertEquals(EnumsApi.ExecContextState.FINISHED.code, getExecContextForTest().getState(),
                "the recursive graft chain must terminate and run to FINISHED");

        final List<TaskImpl> tasks = taskRepository.findByExecContextIdReadOnly(ecId);
        TaskImpl outerHead = task(tasks, "outerHead");
        TaskImpl innerHead = task(tasks, "innerHead");
        assertNotNull(outerHead, "outer's body head must be grafted under the wrapper");
        assertNotNull(innerHead, "inner's body head must be grafted under outerHead (recursion one level deep)");
        assertTrue(tasks.stream().noneMatch(t -> t.getTaskParamsYaml().task.processCode.startsWith("mh.graft.")),
                "no graft node may produce a task of its own");
        assertNotEquals(EnumsApi.TaskExecState.ERROR, preparingSourceCodeService.findTaskState(getExecContextForTest(), outerHead.id));
        assertNotEquals(EnumsApi.TaskExecState.ERROR, preparingSourceCodeService.findTaskState(getExecContextForTest(), innerHead.id));
        String innerCtx = innerHead.getTaskParamsYaml().task.taskContextId;
        String outerCtx = outerHead.getTaskParamsYaml().task.taskContextId;
        assertTrue(parentChainContains(innerCtx, outerCtx),
                "innerHead ctx=" + innerCtx + " must derive up to outerHead ctx " + outerCtx + " (recursion nesting)");
    }

    private static TaskImpl task(List<TaskImpl> tasks, String processCode) {
        return tasks.stream().filter(t -> processCode.equals(t.getTaskParamsYaml().task.processCode)).findFirst().orElse(null);
    }

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
