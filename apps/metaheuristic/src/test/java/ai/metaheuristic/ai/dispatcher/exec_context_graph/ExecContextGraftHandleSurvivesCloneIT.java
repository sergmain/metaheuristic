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
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCloneService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
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
 * Phase-3 acceptance IT (MH-generic half): the grafted instance's re-entry HANDLE survives a clone
 * (025-MHSC-DSL-V2-PLAN, Phase 3).
 *
 * <p>The graft's re-run handle is simply the grafted line's HEAD task id ({@code GraftResult.headTaskId()})
 * — a runtime task id. Phase 3 requires that on {@link ExecContextCloneService#cloneExecContext} this handle
 * (a) is remapped into the clone's task-id space through {@code taskIdMap}, and (b) is a fresh runtime id,
 * never carried across — which is precisely why a content hash must exclude it.
 *
 * <p><b>Zero production change.</b> The clone already enumerates EVERY source-EC task row and builds a
 * complete {@code oldTaskId -> newTaskId} map (exposed on {@code CloneResult.taskIdMap()}). The grafted head
 * is an ordinary task row, so it is already covered — matching the plan's "remap only if not already covered
 * by taskIdMap". This IT is the acceptance that documents that coverage.
 *
 * <p><b>Seal split.</b> "content hash unchanged by its presence" is an RG-layer property: MH has no content
 * hash (the digest lives in the RG consumer and already excludes {@code resetTaskId}). The MH-generic
 * guarantee proven here is that the handle resolves in the clone's task-id space AND is a fresh id; the
 * RG-side content-hash exclusion is verified by the RG consumer in Phase 4.
 *
 * @author Sergio Lissner
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@AutoConfigureCache
public class ExecContextGraftHandleSurvivesCloneIT extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private InternalFunctionService internalFunctionService;
    @Autowired private ExecContextGraftService execContextGraftService;
    @Autowired private ExecContextCloneService cloneService;
    @Autowired private TaskRepository taskRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(
                "/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_graftHandle_remapsIntoCloneTaskIdSpace_asFreshId() {
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

        // 2. find a target task whose getSubProcesses resolves a non-empty sequential body.
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

        // 3. GRAFT a flat body under the target, PLACE_NOW; the returned head id IS the re-run handle.
        ExecContextGraftService.GraftResult gr = execContextGraftService.attachGroup(
                ecId, targetTaskId,
                ExecContextGraftService.GroupRef.fromTargetSubProcesses(),
                List.of(),
                List.of(new ExecContextGraftService.OutputMaterialization(
                        "graftOut", "v".getBytes(StandardCharsets.UTF_8))),
                ExecContextGraftService.Driver.PLACE_NOW);
        final Long handle = gr.headTaskId();
        assertNotNull(handle, "graft must return a head task id (the re-run handle)");

        // sanity: the handle is a real task row in the SOURCE EC.
        TaskImpl srcHead = taskRepository.findByIdReadOnly(handle);
        assertNotNull(srcHead, "the captured handle must be a real task row in the source EC");
        assertEquals(ecId, srcHead.execContextId);

        // 4. CLONE the EC (the objection reopen path clones a committed leaf into a STAGE; MH just clones).
        ExecContextCloneService.CloneResult r = cloneService.cloneExecContext(ecId);
        assertNotNull(r);
        assertNotEquals(ecId, r.clonedExecContextId(), "clone must be a distinct EC");

        // 4a. the captured handle is covered by the clone's taskIdMap and RESOLVES in the clone's id space.
        assertTrue(r.taskIdMap().containsKey(handle),
                "the captured re-run handle must be covered by the clone taskIdMap");
        final Long clonedHandle = r.taskIdMap().get(handle);
        assertNotNull(clonedHandle, "the captured handle must remap to a clone-space task id");

        // 4b. it is a FRESH runtime id, never carried across - the reason a content hash must exclude it.
        assertNotEquals(handle, clonedHandle,
                "the remapped handle must be a fresh clone-space id (runtime ids differ across clones)");

        // 4c. the remapped handle points at a real task that belongs to the CLONE EC, not the source.
        TaskImpl clonedHead = taskRepository.findByIdReadOnly(clonedHandle);
        assertNotNull(clonedHead, "the remapped handle must resolve to a real cloned task");
        assertEquals(r.clonedExecContextId(), clonedHead.execContextId,
                "the remapped handle must belong to the clone EC, not the source");

        // 4d. the cloned handle retains the grafted head's process identity (same group body, new instance).
        assertEquals(srcHead.getTaskParamsYaml().task.processCode,
                clonedHead.getTaskParamsYaml().task.processCode,
                "the cloned handle must retain the grafted head's process identity");
    }
}
