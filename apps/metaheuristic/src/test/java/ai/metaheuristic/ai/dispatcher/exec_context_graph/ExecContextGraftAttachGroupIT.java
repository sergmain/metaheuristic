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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase-1 BEHAVIORAL acceptance IT for the flat / PLACE_NOW graft (025-MHSC-DSL-V2-PLAN, Phase 1).
 *
 * <p>V3 harness (DESCRIPTION-TEST-PIPELINE-V2-V3): extends PreparingSourceCode -> PreparingCore ->
 * MhSharedItTest, so it shares the single Spring context + H2 DB. No @DirtiesContext, no per-class
 * @DynamicPropertySource (the shared one is inherited from MhSharedItTest); @AfterEach stop/cleanup
 * is inherited too.
 *
 * <p>Builds a real EC from a source with a sub-process-bearing target, produces tasks WITHOUT starting
 * (EC left STOPPED - the E4->E5 graft target state), grafts a flat body under a target task via
 * {@link ExecContextGraftService#attachGroup} with driver PLACE_NOW, and asserts the three behavioral
 * guarantees the Spring-less structural suite cannot:
 * <ol>
 *   <li>the grafted line (head + descendants) is SKIPPED terminal;</li>
 *   <li>the declared output is materialized EXACTLY ONCE at the fresh line ctx (write-once, fresh key);</li>
 *   <li>the graft is event-free - the grafted (SKIPPED) head is NOT assignable for dispatch.</li>
 * </ol>
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@AutoConfigureCache
public class ExecContextGraftAttachGroupIT extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private InternalFunctionService internalFunctionService;
    @Autowired private ExecContextGraftService execContextGraftService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ExecContextGraphService execContextGraphService;
    @Autowired private VariableRepository variableRepository;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(
                "/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_attachGroup_flatPlaceNow_skipsLine_materializesOutputOnce_noDispatch() {
        // 1. create EC + produce real tasks WITHOUT starting (STOPPED - the graft target state).
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        final Long ecId = getExecContextForTest().id;

        ExecContextSyncService.getWithSyncVoid(ecId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId, () ->
                                txSupportForTestingService.produceTasksWithoutStarting(getSourceCode(), ecId))));
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));

        // 2. find a target task whose getSubProcesses resolves a non-empty body.
        final ExecContextImpl ec = getExecContextForTest();
        final ExecContextApiData.SimpleExecContext sec = ec.asSimple();
        Long targetTaskId = null;
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            InternalFunctionData.ExecutionContextData ecd =
                    internalFunctionService.getSubProcesses(sec, t.getTaskParamsYaml(), t.id);
            if (ecd.internalFunctionProcessingResult.processing == Enums.InternalFunctionProcessing.ok
                    && !ecd.subProcesses.isEmpty()
                    && ecd.process.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                targetTaskId = t.id;
                break;
            }
        }
        assertNotNull(targetTaskId, "no task with a graftable sub-process body found in the produced EC");

        // 3. GRAFT a flat body under the target, PLACE_NOW, with one write-once output.
        final byte[] outVal = "grafted-value".getBytes(StandardCharsets.UTF_8);
        ExecContextGraftService.GraftResult gr = execContextGraftService.attachGroup(
                ecId, targetTaskId,
                ExecContextGraftService.GroupRef.fromTargetSubProcesses(),
                List.of(),
                List.of(new ExecContextGraftService.OutputMaterialization("graftOut", outVal)),
                ExecContextGraftService.Driver.PLACE_NOW);
        assertNotNull(gr.headTaskId(), "graft must return a head task id");
        assertNotNull(gr.lineCtxId(), "graft must return a fresh line ctx");

        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(ecId, true)));

        // 4a. the grafted line (head + every task at the line ctx) is SKIPPED terminal.
        assertEquals(EnumsApi.TaskExecState.SKIPPED,
                preparingSourceCodeService.findTaskState(getExecContextForTest(), gr.headTaskId()),
                "grafted head must be SKIPPED (terminal)");
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(ecId)) {
            if (gr.lineCtxId().equals(t.getTaskParamsYaml().task.taskContextId)) {
                assertEquals(EnumsApi.TaskExecState.SKIPPED,
                        preparingSourceCodeService.findTaskState(getExecContextForTest(), t.id),
                        "every task on the grafted line must be SKIPPED; task #" + t.id);
            }
        }

        // 4b. the declared output is materialized EXACTLY ONCE at the fresh line ctx (write-once, fresh key).
        Variable v = variableRepository.findByNameAndTaskContextIdAndExecContextId("graftOut", gr.lineCtxId(), ecId);
        assertNotNull(v, "output 'graftOut' must be materialized at the grafted line ctx " + gr.lineCtxId());
        assertEquals(1, variableRepository.findByExecContextIdAndNames(ecId, List.of("graftOut")).size(),
                "output 'graftOut' must be written exactly once (write-once, no duplicate key)");

        // 4c. event-free graft: the grafted (SKIPPED) head is NOT assignable for dispatch.
        final Long headId = gr.headTaskId();
        List<ExecContextData.TaskVertex> assignable = execContextGraphService.findAllForAssigning(
                ec.execContextGraphId, ec.execContextTaskStateId, false);
        assertTrue(assignable.stream().noneMatch(x -> x.taskId.equals(headId)),
                "grafted SKIPPED head must NOT be assignable for dispatch (event-free graft)");
    }
}
