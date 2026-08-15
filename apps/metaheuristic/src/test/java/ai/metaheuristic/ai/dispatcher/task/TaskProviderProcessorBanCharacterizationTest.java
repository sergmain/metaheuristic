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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.data.DispatcherData;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A Task-scoped misconfiguration rejects that Task and nothing else.
 *
 * <p>A Task whose Function declares an {@code env} code the Processor does not have is rejected with
 * {@code interpreter_is_undefined} — which is correct — and the same line ALSO stamps
 * {@code System.currentTimeMillis()} into {@code bannedSince}. That map is keyed by processorId, so
 * one badly configured Task takes the whole Processor, every core of it, out of service for 30
 * minutes for EVERY other Task as well. Two multi-day debugging incidents trace back to this: the
 * cluster goes quiet and nothing in the logs repeats, because the rejection is recorded once per ban
 * window rather than once per poll.
 *
 * <p>This test began as a characterization of that behaviour, so that removing it would provably be a
 * change and nothing else would move with it. Its assertions are now flipped: they describe what the
 * code does after the fix, and they fail against the version that wrote the ban.
 *
 * <p>❗ The second assertion is the load-bearing one. Asserting only that the rejecting core is
 * subsequently banned would be satisfied by a per-core ban too, and would therefore document a
 * narrower fault than the one that exists. Asking a DIFFERENT core of the SAME Processor is what
 * pins the real blast radius.
 *
 * <p>⚠️ The task queue this test seeds is global and the harness shares one Spring context across the
 * whole run, so it is reset in {@code @AfterEach}. There is no longer any ban state to clear.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
@Slf4j
public class TaskProviderProcessorBanCharacterizationTest extends PreparingSourceCode {

    private static final String ABSENT_ENV_CODE = "env-that-no-processor-declares";

    private static final Long PROCESSOR_ID = 777_001L;
    private static final Long CORE_A_ID = 777_101L;
    private static final Long CORE_B_ID = 777_102L;

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TaskProviderUnassignedTaskService taskProviderUnassignedTaskService;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ai.metaheuristic.ai.dispatcher.execution_gate.ExecutionGateService executionGateService;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @AfterEach
    public void clearQueue() {
        TaskQueueService.resetQueue();
    }

    @Test
    public void test_interpreterIsUndefined_rejectsOnlyThatTask_andNeverBansTheProcessor() {
        // ---- a started ExecContext, so the admission loop gets past its ExecContext checks -------
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        final Long execContextId = getExecContextForTest().id;

        ExecContextSyncService.getWithSyncVoid(execContextId, () -> txSupportForTestingService.toStarted(execContextId));
        // the started state is read from a cache that is only rebuilt on demand
        execContextStatusService.resetStatus();
        assertTrue(execContextStatusService.isStarted(execContextId),
                "setup failed: the ExecContext must be STARTED before the admission loop runs");

        // ---- one queued Task whose Function needs an env the Processor does not have -------------
        TaskQueueService.resetQueue();
        final Long taskId = 777_201L;
        final TaskParamsYaml tpy = taskParams(execContextId);
        final TaskImpl task = new TaskImpl();
        task.id = taskId;
        task.execContextId = execContextId;
        task.execState = EnumsApi.TaskExecState.NONE.value;
        task.setParams(TaskParamsYamlUtils.UTILS.toString(tpy));

        TaskQueueSyncStaticService.getWithSyncVoid(() -> TaskQueueService.addNewTask(new TaskQueue.QueuedTask(
                EnumsApi.FunctionExecContext.external, execContextId, taskId, task, tpy, null, 1)));

        // ❗ A freshly created TaskGroup is unlocked, and isQueueEmpty() only counts LOCKED groups
        // (TaskGroup.isNewTask() returns false while locked==false). The production assigning pass
        // locks the group as its last step, so a test that seeds the queue directly must do the same
        // or the admission loop will report queue_is_empty and never evaluate anything.
        TaskProviderTopLevelService.lock(execContextId);

        assertFalse(TaskProviderTopLevelService.isQueueEmpty(),
                "setup failed: the queue must hold the Task the admission loop is meant to evaluate");

        final ProcessorData.ProcessorAndCoreParams coreA = processorAndCore(CORE_A_ID);
        final ProcessorData.ProcessorAndCoreParams coreB = processorAndCore(CORE_B_ID);
        final DispatcherData.TaskQuotas quotas = new DispatcherData.TaskQuotas(0);

        // ---- 1st call on core A: the Task is rejected, and a durable ban is written as a side effect
        final TaskData.TaskSearching first = taskProviderUnassignedTaskService.findUnassignedTaskAndAssign(coreA, false, quotas);

        assertEquals(Enums.TaskRejectingStatus.interpreter_is_undefined, first.rejected.get(taskId),
                "the Task must be rejected because the Processor has no interpreter for the Function's env");
        assertEquals(Enums.TaskSearchingStatus.task_not_found, first.status,
                "no Task was assignable, so the search as a whole ends with task_not_found");

        // no durable block may exist for this Processor: nothing about a misconfigured Task is a
        // statement about the Processor's health
        assertNull(executionGateService.blockedUntil(EnumsApi.GateScope.processor, String.valueOf(PROCESSOR_ID)),
                "a Task-scoped misconfiguration must not withhold work from the Processor");

        // ---- 2nd call on the SAME core: the queue is walked again, normally ----------------------
        final TaskData.TaskSearching second = taskProviderUnassignedTaskService.findUnassignedTaskAndAssign(coreA, false, quotas);

        assertEquals(Enums.TaskSearchingStatus.task_not_found, second.status,
                "the second call must evaluate Tasks normally rather than short-circuiting");
        assertEquals(Enums.TaskRejectingStatus.interpreter_is_undefined, second.rejected.get(taskId),
                "the Task is rejected again, and the reason is reported again - once per poll, not once per 30 minutes");

        // ---- 3rd call on a DIFFERENT core of the SAME Processor ---------------------------------
        // ❗ The assertion that pins the fix. A core that never saw the misconfigured Task was
        // previously refused service because the ban was keyed by processorId.
        final TaskData.TaskSearching third = taskProviderUnassignedTaskService.findUnassignedTaskAndAssign(coreB, false, quotas);

        assertEquals(Enums.TaskSearchingStatus.task_not_found, third.status,
                "a different core of the same Processor keeps working - the Processor was never at fault");
        assertEquals(Enums.TaskRejectingStatus.interpreter_is_undefined, third.rejected.get(taskId),
                "core B evaluates the Task too, and reports the same reason");
    }

    private static ProcessorData.ProcessorAndCoreParams processorAndCore(Long coreId) {
        final ProcessorStatusYaml psy = new ProcessorStatusYaml();
        psy.env = new ProcessorStatusYaml.Env();
        // deliberately populated, and deliberately WITHOUT the code the Task's Function asks for:
        // an empty env would be rejected earlier, with environment_is_empty
        psy.env.envs.put("python-3", "/usr/bin/python3");
        psy.gitStatusInfo = new GtiUtils.GitStatusInfo(EnumsApi.GitStatus.installed);
        psy.taskParamsVersion = 1;
        psy.os = null;

        final CoreStatusYaml csy = new CoreStatusYaml();
        csy.tags = null;

        return new ProcessorData.ProcessorAndCoreParams(PROCESSOR_ID, coreId, psy, csy);
    }

    private static TaskParamsYaml taskParams(Long execContextId) {
        TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.execContextId = execContextId;
        tpy.task.taskContextId = CommonConsts.TOP_LEVEL_CONTEXT_ID;
        tpy.task.processCode = "assembly-raw-file";
        tpy.task.context = EnumsApi.FunctionExecContext.external;
        tpy.task.function = new TaskParamsYaml.FunctionConfig();
        tpy.task.function.code = "function-01:1.1";
        tpy.task.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        // a dispatcher-sourced external Function must declare where its payload lives -
        // TaskParamsYaml.checkIntegrity() rejects the document otherwise
        final TaskParamsYaml.Target target = new TaskParamsYaml.Target();
        target.file = "function-01.jar";
        tpy.task.function.targets.put(CommonConsts.MH_DEFAULT_OS_KEY, target);
        // the whole point: no Processor in this test declares this env code
        tpy.task.function.env = ABSENT_ENV_CODE;
        return tpy;
    }
}
