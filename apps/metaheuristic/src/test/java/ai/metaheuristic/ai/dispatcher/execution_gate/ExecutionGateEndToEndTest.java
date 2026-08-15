/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.execution_gate;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecutionGate;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.events.ResetTasksWithErrorEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskResettingTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecutionGateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.*;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ❗ The one test that crosses the whole feature: a Task fails printing something an analyzer
 * recognises, a durable block appears, and the next allocation attempt is refused because of it.
 *
 * <p>It exists because its absence hid five separate defects. Every other test for this feature asks
 * "does this method do what it says?", and each passed, because each method did — while
 * {@code recordRejection}, {@code checkScopeAllowedInDescriptor}, the expiry sweep, {@code merge} and
 * the composed {@code admit} were all reachable from nothing but their own tests. A unit test IS a
 * caller, so grepping for any of them found hits and they looked wired.
 *
 * <p>Only a test that enters where production enters can tell the difference between "this works" and
 * "this runs". Every assertion below is on an EFFECT — a row in a table, a rejection reason from the
 * allocator — never on a return value from the method under test.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecutionGateEndToEndTest extends PreparingSourceCode {

    private static final Long PROCESSOR_ID = 778_001L;
    private static final Long CORE_ID = 778_101L;

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TxTestingService txTestingService;
    @Autowired private ExecContextTaskResettingTopLevelService execContextTaskResettingTopLevelService;
    @Autowired private ExecutionGateService executionGateService;
    @Autowired private ExecutionGateRepository executionGateRepository;
    @Autowired private TaskProviderUnassignedTaskService taskProviderUnassignedTaskService;
    @Autowired private TaskFinishingTxService taskFinishingTxService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private Globals globals;

    private final List<String> blockedFunctionCodes = new ArrayList<>();

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @AfterEach
    public void clearGlobalsAndBlocks() {
        // the context and the DB are shared for the whole run
        globals.dispatcher.executionGate.analyzers.clear();
        for (String code : blockedFunctionCodes) {
            executionGateService.release(EnumsApi.GateScope.function, code);
            final ExecutionGate leftover = executionGateRepository.findByScopeAndRefKey(EnumsApi.GateScope.function.name(), code);
            if (leftover != null) {
                executionGateRepository.delete(leftover);
            }
        }
        blockedFunctionCodes.clear();
        TaskQueueService.resetQueue();
    }

    @Test
    public void test_aFailureMatchingAnAnalyzerBlocksTheFunctionAndTheNextAllocationIsRefused() {
        // a code no other test has seen, so the analyzer cache cannot answer for it from an earlier run
        final String functionCode = "e2e-fn-" + System.nanoTime() + ":1.0";
        blockedFunctionCodes.add(functionCode);

        // ---- an operator-declared rule, which is a Function-descriptor-free way to get one -------
        globals.dispatcher.executionGate.analyzers.add(new FunctionConfigYaml.Analyzer(
                "quota-exhausted", new ArrayList<>(List.of("quota exhausted")), "20min", false,
                EnumsApi.GateScope.function));

        // ---- an ExecContext holding one Task that is about to fail ------------------------------
        final DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        final ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        final Long execContextId = getExecContextForTest().id;

        final TaskParamsYaml tpy = taskParams(execContextId, functionCode);
        final TaskImpl task = txTestingService.create(execContextId, TaskParamsYamlUtils.UTILS.toString(tpy));

        ExecContextSyncService.getWithSyncVoid(execContextId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId, () -> {
                            final OperationStatusRest osr = txSupportForTestingService.addTasksToGraphWithTx(
                                    execContextId, List.of(),
                                    List.of(new TaskApiData.TaskWithContext(task.id, CommonConsts.TOP_LEVEL_CONTEXT_ID)));
                            assertEquals(EnumsApi.OperationStatus.OK, osr.status);
                        })));

        assertNull(executionGateService.blockedUntil(EnumsApi.GateScope.function, functionCode),
                "setup failed: nothing may be blocked before the Task fails");

        // ---- the Task fails, printing what the rule recognises -----------------------------------
        TaskSyncService.getWithSyncVoid(task.id, () -> taskFinishingTxService.finishWithErrorWithTx(
                task.id, "provider returned 429: quota exhausted for this key, retry later"));

        final TaskImpl failed = taskRepository.findByIdReadOnly(task.id);
        assertNotNull(failed);
        assertEquals(EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value, failed.execState,
                "setup failed: the Task must reach ERROR_WITH_RECOVERY for the recovery pass to see it");

        // ---- the real recovery pass runs ---------------------------------------------------------
        execContextTaskResettingTopLevelService.resetTasksWithErrorForRecovery(new ResetTasksWithErrorEvent(execContextId));

        // ---- EFFECT 1: a durable row exists, written by nothing the test called directly ---------
        final ExecutionGate row = executionGateRepository.findByScopeAndRefKey(EnumsApi.GateScope.function.name(), functionCode);
        assertNotNull(row, "the recovery pass must have opened a block for the Function the analyzer matched");
        assertEquals("quota-exhausted", row.reasonCode, "the analyzer's name becomes the recorded reason");
        assertTrue(row.blockedUntil > System.currentTimeMillis(), "the block must still be live");
        assertEquals(task.id, row.getExecutionGateParamsYaml().triggeredByTaskId,
                "the row must say which Task's failure opened it");

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> executionGateService.blockedUntil(EnumsApi.GateScope.function, functionCode) != null);

        // ---- EFFECT 2: the allocator now refuses a Task using that Function ----------------------
        ExecContextSyncService.getWithSyncVoid(execContextId, () -> txSupportForTestingService.toStarted(execContextId));
        execContextStatusService.resetStatus();

        final Long queuedTaskId = 778_201L;
        final TaskImpl queued = new TaskImpl();
        queued.id = queuedTaskId;
        queued.execContextId = execContextId;
        queued.execState = EnumsApi.TaskExecState.NONE.value;
        queued.setParams(TaskParamsYamlUtils.UTILS.toString(tpy));

        TaskQueueService.resetQueue();
        TaskQueueSyncStaticService.getWithSyncVoid(() -> TaskQueueService.addNewTask(new TaskQueue.QueuedTask(
                EnumsApi.FunctionExecContext.external, execContextId, queuedTaskId, queued, tpy, null, 1)));
        TaskProviderTopLevelService.lock(execContextId);

        final TaskData.TaskSearching searching = taskProviderUnassignedTaskService.findUnassignedTaskAndAssign(
                processorAndCore(), false, new DispatcherData.TaskQuotas(0));

        assertEquals(Enums.TaskRejectingStatus.function_is_quarantined, searching.rejected.get(queuedTaskId),
                "the allocator must refuse a Task whose Function is blocked - this is the half that was unwired");

        // ---- EFFECT 3: releasing the block lets work flow again ---------------------------------
        executionGateService.release(EnumsApi.GateScope.function, functionCode);
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> executionGateService.blockedUntil(EnumsApi.GateScope.function, functionCode) == null);

        final TaskData.TaskSearching afterRelease = taskProviderUnassignedTaskService.findUnassignedTaskAndAssign(
                processorAndCore(), false, new DispatcherData.TaskQuotas(0));

        assertNotEquals(Enums.TaskRejectingStatus.function_is_quarantined, afterRelease.rejected.get(queuedTaskId),
                "a released block must stop being a reason - otherwise a block could never be lifted");
    }

    private static ProcessorData.ProcessorAndCoreParams processorAndCore() {
        final ProcessorStatusYaml psy = new ProcessorStatusYaml();
        psy.env = new ProcessorStatusYaml.Env();
        psy.env.envs.put("java-25", "java");
        psy.gitStatusInfo = new GtiUtils.GitStatusInfo(EnumsApi.GitStatus.installed);
        psy.taskParamsVersion = 3;
        psy.os = null;

        final CoreStatusYaml csy = new CoreStatusYaml();
        csy.tags = null;

        return new ProcessorData.ProcessorAndCoreParams(PROCESSOR_ID, CORE_ID, psy, csy);
    }

    private static TaskParamsYaml taskParams(Long execContextId, String functionCode) {
        final TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.execContextId = execContextId;
        tpy.task.taskContextId = CommonConsts.TOP_LEVEL_CONTEXT_ID;
        tpy.task.processCode = "assembly-raw-file";
        tpy.task.context = EnumsApi.FunctionExecContext.external;
        tpy.task.function = new TaskParamsYaml.FunctionConfig();
        tpy.task.function.code = functionCode;
        tpy.task.function.env = "java-25";
        tpy.task.triesAfterError = 1;
        return tpy;
    }
}
