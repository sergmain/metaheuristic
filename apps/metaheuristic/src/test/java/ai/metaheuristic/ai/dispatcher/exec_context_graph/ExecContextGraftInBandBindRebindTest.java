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
 * 032 Phase A proof: an AUTHORED in-band {@code graft ... bind(...)} materializes its bound inputs
 * write-once at the grafted line ctx, INCLUDING a REBIND (enclosing name != group formal name) - the
 * capability the per-level depth counter needs (bind {@code nextDepth} onto the child's {@code depth}).
 *
 * <p>Setup (inline .mhsc): {@code seed} produces a single-line variable {@code items}; a dynamic
 * {@code mh.batch-line-splitter} splits it into a per-line {@code lineVal}; its sequential body is a
 * single {@code graft grp bind (lineVal) driver run-now}. The group {@code grp} declares a FORMAL input
 * {@code val} (deliberately NOT named {@code lineVal}) and its head {@code grpHead} is an
 * {@code mh.evaluation} that REQUIRES {@code val} as input and echoes it.
 *
 * <p>Because {@code val} exists nowhere in the enclosing context (only {@code lineVal} does), the head can
 * only resolve {@code val} if the graft's {@code bind(lineVal)} materialized it onto the formal - a
 * name-inheritance walk cannot. Before the wiring (bind dropped), {@code grpHead} would ERROR on the
 * unresolved input and the EC would never reach FINISHED; with it, the EC runs green.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextGraftInBandBindRebindTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TaskRepository taskRepository;

    private static final String MHSC_SOURCE = """
            source "test-inband-bind-1.0" {
                seed := internal mh.evaluation {
                    -> items: ext=".txt"
                    meta expression = "items = 'HELLO'"
                }
                group grp (<- val) reset-point grpHead {
                    grpHead := internal mh.evaluation {
                        <- val
                        -> echoed: ext=".txt"
                        meta expression = "echoed = val"
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
                        graft grp bind (lineVal) driver run-now
                    }
                }
            }
            """;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("inline://test-inband-bind", EnumsApi.SourceCodeLang.mhsc, MHSC_SOURCE);
    }

    @AfterEach
    public void afterTest() {
        if (getExecContextForTest() != null) {
            ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
                    () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
        }
    }

    @Test
    public void test_inBandGraft_bindRebindsEnclosingValueOntoDifferentFormal() {
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

        // The whole point: without bind materialization, grpHead cannot resolve 'val' and the EC never
        // reaches FINISHED. FINISHED here is the rebind proof.
        assertEquals(EnumsApi.ExecContextState.FINISHED.code, getExecContextForTest().getState(),
                "EC with an in-band graft that REBINDS lineVal->val must run to FINISHED");

        final List<TaskImpl> tasks = taskRepository.findByExecContextIdReadOnly(ecId);

        TaskImpl grpHead = tasks.stream()
                .filter(t -> "grpHead".equals(t.getTaskParamsYaml().task.processCode))
                .findFirst().orElse(null);
        assertNotNull(grpHead, "the in-band graft must have laid the group body head 'grpHead'");

        assertNotEquals(EnumsApi.TaskExecState.ERROR,
                preparingSourceCodeService.findTaskState(getExecContextForTest(), grpHead.id),
                "grpHead must not be ERROR - it resolved its formal input 'val' via the rebind");

        assertTrue(tasks.stream().noneMatch(t -> t.getTaskParamsYaml().task.processCode.startsWith("mh.graft.")),
                "the graft node must not produce a task of its own");
    }
}
