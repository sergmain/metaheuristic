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
import ai.metaheuristic.ai.SharedItEnv;
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
    private final List<String> blockedApiKeys = new ArrayList<>();

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
        for (String key : blockedApiKeys) {
            executionGateService.release(EnumsApi.GateScope.api, key);
            final ExecutionGate leftover = executionGateRepository.findByScopeAndRefKey(EnumsApi.GateScope.api.name(), key);
            if (leftover != null) {
                executionGateRepository.delete(leftover);
            }
        }
        blockedApiKeys.clear();
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
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().id, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().id, () -> {
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

    @Test
    public void test_aFailureRecognisedOnlyByMhShippedRulesBlocksTheApiKey() {
        // ---- nothing but mh-api-analyzers.yaml can recognise this failure ------------------------
        // no operator rule, and a Function code the dispatcher has never seen - so it has no descriptor
        // and no rules of its own. The only rules left are the ones read from the classpath at start.
        globals.dispatcher.executionGate.analyzers.clear();
        final String functionCode = "e2e-fn-mh-" + System.nanoTime() + ":1.0";
        final String keyCode = SharedItEnv.uniqueCode("E2E_ANTHROPIC_KEY");

        final DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        final ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        final Long execContextId = getExecContextForTest().id;
        final String apiKey = getExecContextForTest().companyId + ":" + keyCode;
        blockedApiKeys.add(apiKey);

        final TaskParamsYaml tpy = taskParams(execContextId, functionCode);
        tpy.task.function.api = new TaskParamsYaml.Api(keyCode);
        final TaskImpl task = txTestingService.create(execContextId, TaskParamsYamlUtils.UTILS.toString(tpy));

        ExecContextSyncService.getWithSyncVoid(execContextId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().id, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().id, () -> {
                            final OperationStatusRest osr = txSupportForTestingService.addTasksToGraphWithTx(
                                    execContextId, List.of(),
                                    List.of(new TaskApiData.TaskWithContext(task.id, CommonConsts.TOP_LEVEL_CONTEXT_ID)));
                            assertEquals(EnumsApi.OperationStatus.OK, osr.status);
                        })));

        assertNull(executionGateService.blockedUntil(EnumsApi.GateScope.api, apiKey),
                "setup failed: nothing may be blocked before the Task fails");

        // ---- the Task fails with what AnthropicApiExecutor prints on a 429 -------------------------
        TaskSyncService.getWithSyncVoid(task.id, () -> taskFinishingTxService.finishWithErrorWithTx(
                task.id, "API response status: 429\n" +
                        "777.100 Anthropic API call failed with status 429; body={\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\"}}"));

        final TaskImpl failed = taskRepository.findByIdReadOnly(task.id);
        assertNotNull(failed);
        assertEquals(EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value, failed.execState,
                "setup failed: the Task must reach ERROR_WITH_RECOVERY for the recovery pass to see it");

        // ---- the real recovery pass runs ---------------------------------------------------------
        execContextTaskResettingTopLevelService.resetTasksWithErrorForRecovery(new ResetTasksWithErrorEvent(execContextId));

        // ---- EFFECT 1: the KEY is blocked, by the rule MH ships ----------------------------------
        final ExecutionGate row = executionGateRepository.findByScopeAndRefKey(EnumsApi.GateScope.api.name(), apiKey);
        assertNotNull(row, "the recovery pass must have opened a block on the API key - only mh-api-analyzers.yaml declares a rule for this console");
        assertEquals("api-quota-exhausted", row.reasonCode, "the shipped rule's name becomes the recorded reason");
        assertTrue(row.blockedUntil > System.currentTimeMillis(), "the block must still be live");
        assertEquals(task.id, row.getExecutionGateParamsYaml().triggeredByTaskId,
                "the row must say which Task's failure opened it");

        // ---- EFFECT 2: the block reached memory, where admission reads it ------------------------
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> executionGateService.blockedUntil(EnumsApi.GateScope.api, apiKey) != null);

        // ---- and nothing was blocked on the Function: the rule's scope is the key ----------------
        assertNull(executionGateRepository.findByScopeAndRefKey(EnumsApi.GateScope.function.name(), functionCode),
                "api-quota-exhausted is scope api - the Function itself must stay unblocked");
    }

    @Test
    public void test_aFreeRetryHitOnATaskWithNoTriesLeft_doesNotEndInError() {
        // a throttle: the provider said "not now", the Task did nothing wrong. With incrementTries=false the
        // recovery pass must retry it free of charge after the block - even when the Task has no tries of its
        // own left (triesAfterError: 0)
        globals.dispatcher.executionGate.analyzers.clear();
        final String functionCode = "e2e-fn-throttle-" + System.nanoTime() + ":1.0";
        blockedFunctionCodes.add(functionCode);
        globals.dispatcher.executionGate.analyzers.add(new FunctionConfigYaml.Analyzer(
                "throttled", new ArrayList<>(List.of("failed with status 429")), "30s", false,
                EnumsApi.GateScope.function));

        final DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        final ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        final Long execContextId = getExecContextForTest().id;

        final TaskParamsYaml tpy = taskParams(execContextId, functionCode);
        tpy.task.triesAfterError = 0;
        final TaskImpl task = txTestingService.create(execContextId, TaskParamsYamlUtils.UTILS.toString(tpy));

        ExecContextSyncService.getWithSyncVoid(execContextId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().id, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().id, () -> {
                            final OperationStatusRest osr = txSupportForTestingService.addTasksToGraphWithTx(
                                    execContextId, List.of(),
                                    List.of(new TaskApiData.TaskWithContext(task.id, CommonConsts.TOP_LEVEL_CONTEXT_ID)));
                            assertEquals(EnumsApi.OperationStatus.OK, osr.status);
                        })));

        TaskSyncService.getWithSyncVoid(task.id, () -> taskFinishingTxService.finishWithErrorWithTx(
                task.id, "API response status: 429\nsome-api call failed with status 429; body={\"error\":\"too many requests\"}"));
        assertEquals(EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value, Objects.requireNonNull(taskRepository.findByIdReadOnly(task.id)).execState,
                "setup failed: the Task must reach ERROR_WITH_RECOVERY for the recovery pass to see it");

        execContextTaskResettingTopLevelService.resetTasksWithErrorForRecovery(new ResetTasksWithErrorEvent(execContextId));

        // the rule matched: the Function is blocked for the throttle window
        final ExecutionGate row = executionGateRepository.findByScopeAndRefKey(EnumsApi.GateScope.function.name(), functionCode);
        assertNotNull(row, "setup failed: the throttle rule must have matched");
        assertEquals("throttled", row.reasonCode);

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(100))
                .until(() -> Objects.requireNonNull(taskRepository.findByIdReadOnly(task.id)).execState != EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value);
        final TaskImpl after = Objects.requireNonNull(taskRepository.findByIdReadOnly(task.id));

        // a free retry goes back into the queue; the block above is what delays it
        assertEquals(EnumsApi.TaskExecState.NONE.value, after.execState,
                "a free-retry hit must send the Task back to NONE - never ERROR - even with no tries left");
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
