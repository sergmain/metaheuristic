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

package ai.metaheuristic.ai.preparing;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.events.RegisterTaskForCheckCachingEvent;
import ai.metaheuristic.ai.dispatcher.event.events.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextEventService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingService;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskVariableTopLevelService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.api.EnumsApi.ExecContextState.FINISHED;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Drives a full MH pipeline execution loop in integration tests.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li>{@link #runPipelineToCompletion(Long, int)} — internal-functions-only.
 *       Use when the source code under test contains no external functions.
 *       Will fail loudly if it encounters an external task.</li>
 *   <li>{@link #runPipelineToCompletion(Long, SyntheticDataProvider, int)} —
 *       mixed internal + external. External tasks are simulated: the runner
 *       manually transitions the task through NONE → IN_PROGRESS → OK using the
 *       same low-level path as a real Processor's result-report, with output
 *       variables populated from the provided {@link SyntheticDataProvider}.</li>
 * </ul>
 *
 * <h2>Why this exists (and why it avoids findTask)</h2>
 * Production assigns external tasks via {@code TaskProviderTopLevelService.findTask},
 * which on assignment publishes {@code InputVariablesInitedEvent} consumed by an
 * {@code @Async @EventListener}. That listener and the test thread race for the
 * same {@code TaskImpl} row, and the {@code @Version}-guarded commit of the test's
 * subsequent {@code storeExecResultWithTx} call loses to the listener — surfacing
 * as {@code ObjectOptimisticLockingFailureException}. The runner sidesteps that
 * entirely by transitioning {@code task.execState} to {@code IN_PROGRESS} directly
 * and storing the result via {@link ExecContextFSM#storeExecResultWithTx} on the
 * test thread. Net behavioral equivalence is preserved — production correctness
 * isn't being papered over, only the test-side concurrency hazard.
 *
 * Internal-function execution flows through {@link TaskWithInternalContextEventService}
 * directly (bypassing the {@code @Async} hop), mirroring the RG harness pattern.
 *
 * @author Sergio Lissner
 * Date: 5/21/2026
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class MhInternalTaskPipelineRunner {

    private final TaskRepository taskRepository;
    private final TaskRepositoryForTest taskRepositoryForTest;
    private final ExecContextCache execContextCache;
    private final ExecContextSchedulerService execContextSchedulerService;
    private final TaskWithInternalContextEventService taskWithInternalContextEventService;
    private final ExecContextTaskStateService execContextTaskStateService;
    private final ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;
    private final TaskCheckCachingService taskCheckCachingService;
    private final TaskFinishingTopLevelService taskFinishingTopLevelService;
    private final TaskVariableTopLevelService taskVariableTopLevelService;
    private final TxSupportForTestingService txSupportForTestingService;
    private final ExecContextFSM execContextFSM;

    /**
     * Callback to provide synthetic data for an external function task.
     * Map keys are output-variable names; map values are the data to store.
     */
    @FunctionalInterface
    public interface SyntheticDataProvider {
        Map<String, String> provide(String functionCode, TaskParamsYaml tpy);
    }

    /**
     * Provider used by the internal-only overload: fails loudly on any external task.
     */
    private static final SyntheticDataProvider INTERNAL_ONLY = (functionCode, tpy) -> {
        throw new IllegalStateException(
                "MhInternalTaskPipelineRunner.runPipelineToCompletion(execContextId, maxIterations) is " +
                        "internal-functions-only, but task uses external function '" + functionCode +
                        "'. Use the 3-arg overload with a SyntheticDataProvider for mixed pipelines.");
    };

    /**
     * Run the pipeline until FINISHED. Internal-functions-only convenience.
     */
    public void runPipelineToCompletion(Long execContextId, int maxIterations) {
        runPipelineToCompletion(execContextId, INTERNAL_ONLY, maxIterations);
    }

    /**
     * Run the pipeline until FINISHED. External tasks are simulated via the provider.
     */
    public void runPipelineToCompletion(Long execContextId, SyntheticDataProvider syntheticDataProvider, int maxIterations) {
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            processScheduledTasks();

            ExecContextImpl ec = execContextCache.findById(execContextId, true);
            assertNotNull(ec);

            int ecState = ec.getState();
            if (ecState == FINISHED.code) {
                log.info("Pipeline finished after {} iterations", iteration);
                return;
            }
            if (ecState == EnumsApi.ExecContextState.ERROR.code) {
                fail("ExecContext entered ERROR state at iteration " + iteration);
            }

            enqueueCheckCacheTasks(execContextId);
            taskCheckCachingService.checkCaching();

            // Find tasks that are ready to execute (NONE-state).
            List<Object[]> taskRows = taskRepositoryForTest.findAllExecStateAndParamsByExecContextId(execContextId);
            boolean progressed = false;

            for (Object[] row : taskRows) {
                Long taskId = (Long) row[0];
                TaskImpl task = taskRepository.findById(taskId).orElse(null);
                if (task == null) {
                    continue;
                }
                if (task.execState != EnumsApi.TaskExecState.NONE.value) {
                    continue;
                }

                TaskParamsYaml tpy = task.getTaskParamsYaml();
                if (tpy.task.context == EnumsApi.FunctionExecContext.internal) {
                    log.info("Enqueuing internal function: {} (task #{})", tpy.task.function.code, taskId);
                    enqueueInternalFunction(ec, task);
                }
                else {
                    log.info("Simulating external function: {} (task #{})", tpy.task.function.code, taskId);
                    simulateExternalFunction(ec, task, syntheticDataProvider);
                }
                progressed = true;
            }

            if (!progressed) {
                // No NONE-state tasks; wait briefly for async transitions to surface
                // any newly-ready tasks (e.g. dynamic subprocesses created by a permute
                // function that just finished). If nothing surfaces, the next loop
                // iteration will hit the FINISHED check above.
                await().atMost(Duration.ofSeconds(10))
                        .pollInterval(Duration.ofMillis(200))
                        .until(() -> {
                            processScheduledTasks();
                            enqueueCheckCacheTasks(execContextId);
                            taskCheckCachingService.checkCaching();

                            ExecContextImpl ecCheck = execContextCache.findById(execContextId, true);
                            if (ecCheck == null) {
                                return true;
                            }
                            if (ecCheck.getState() == FINISHED.code ||
                                    ecCheck.getState() == EnumsApi.ExecContextState.ERROR.code) {
                                return true;
                            }
                            List<Object[]> rows = taskRepositoryForTest.findAllExecStateAndParamsByExecContextId(execContextId);
                            for (Object[] r : rows) {
                                Long tid = (Long) r[0];
                                TaskImpl t = taskRepository.findById(tid).orElse(null);
                                if (t != null && t.execState == EnumsApi.TaskExecState.NONE.value) {
                                    return true;
                                }
                            }
                            return false;
                        });
            }
        }
        fail("Pipeline did not finish within " + maxIterations + " iterations");
    }

    /**
     * Enqueue an internal-function task directly via {@link TaskWithInternalContextEventService}.
     * Production path — handles condition evaluation, subprocess creation, SKIP logic —
     * bypassing only the {@code @Async @EventListener} hop that races the test thread.
     * Then awaits the task leaving NONE state (terminal: OK / SKIPPED / ERROR).
     */
    private void enqueueInternalFunction(ExecContextImpl ec, TaskImpl task) {
        TaskParamsYaml tpy = task.getTaskParamsYaml();

        taskWithInternalContextEventService.putToQueue(
                new TaskWithInternalContextEvent(ec.sourceCodeId, ec.id, task.id));

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    processScheduledTasks();
                    TaskImpl reloaded = taskRepository.findById(task.id).orElse(null);
                    if (reloaded == null) {
                        return true;
                    }
                    ExecContextImpl execContext = execContextCache.findById(ec.id, true);
                    assertNotNull(execContext);
                    if (execContext.state == FINISHED.code) {
                        return true;
                    }
                    int state = reloaded.execState;
                    return state == EnumsApi.TaskExecState.OK.value
                            || state == EnumsApi.TaskExecState.SKIPPED.value
                            || state == EnumsApi.TaskExecState.ERROR.value;
                });

        TaskImpl result = taskRepository.findById(task.id).orElse(null);
        if (result != null) {
            EnumsApi.TaskExecState finalState = EnumsApi.TaskExecState.from(result.execState);
            log.info("  Internal function {} (task #{}) finished with state: {}",
                    tpy.task.function.code, task.id, finalState);
        }
    }

    /**
     * Simulate execution of an external function on the test thread. Does NOT go through
     * {@code TaskProviderTopLevelService.findTask} — that path publishes async events
     * that race the subsequent storeExecResultWithTx commit and surface as
     * ObjectOptimisticLockingFailureException. Instead, transition the task directly
     * to IN_PROGRESS, populate its output variables from the provider, then run the
     * exact same storeExecResultWithTx + variable-status-update + checkTaskCanBeFinished
     * sequence the production processor-reporting path takes.
     */
    private void simulateExternalFunction(ExecContextImpl ec, TaskImpl task, SyntheticDataProvider syntheticDataProvider) {
        TaskParamsYaml tpy = task.getTaskParamsYaml();
        String functionCode = tpy.task.function.code;

        // Wait for task to be in NONE state (after CHECK_CACHE resolves)
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    processScheduledTasks();
                    enqueueCheckCacheTasks(ec.id);
                    taskCheckCachingService.checkCaching();
                    TaskImpl reloaded = taskRepository.findById(task.id).orElse(null);
                    return reloaded != null && reloaded.execState == EnumsApi.TaskExecState.NONE.value;
                });

        // Transition task to IN_PROGRESS (simulates Processor picking it up).
        TaskSyncService.getWithSyncVoid(task.id, () -> {
            TaskImpl t = taskRepository.findById(task.id).orElse(null);
            assertNotNull(t);
            t.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
            t.setAssignedOn(System.currentTimeMillis());
            taskRepository.save(t);
        });

        // Get synthetic data for output variables.
        Map<String, String> outputData = syntheticDataProvider.provide(functionCode, tpy);

        for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
            String data = outputData.get(output.name);
            if (data != null) {
                txSupportForTestingService.storeOutputVariableWithTaskContextId(
                        ec.id, output.name, data, tpy.task.taskContextId, task.id);
            }
            else if (output.getNullable()) {
                log.info("  Nullable output '{}' has no synthetic data, skipping", output.name);
            }
            else {
                fail("No synthetic data for required output variable '" + output.name
                        + "' of function " + functionCode + " (task #" + task.id + ")");
            }
        }

        // Report OK execution result (same shape as a real Processor's report).
        FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec(
                new FunctionApiData.SystemExecResult("output-of-a-function", true, 0, "Synthetic OK"),
                null, null, null);
        String execResult = FunctionExecUtils.toString(functionExec);

        ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r =
                new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
        r.setTaskId(task.id);
        r.setResult(execResult);

        TaskSyncService.getWithSyncVoid(task.id, () ->
                ExecContextSyncService.getWithSyncVoid(ec.id, () ->
                        execContextFSM.storeExecResultWithTx(r)));

        processScheduledTasks();

        // Mark output variables uploaded — the production sequence after a Processor
        // reports a result.
        TaskImpl updatedTask = taskRepository.findById(task.id).orElse(null);
        assertNotNull(updatedTask);
        TaskParamsYaml updatedTpy = updatedTask.getTaskParamsYaml();
        for (TaskParamsYaml.OutputVariable output : updatedTpy.task.outputs) {
            Enums.UploadVariableStatus status = TaskSyncService.getWithSyncNullable(task.id,
                    () -> taskVariableTopLevelService.updateStatusOfVariable(task.id, output.id).status);
            assertEquals(Enums.UploadVariableStatus.OK, status,
                    "Upload status for output '" + output.name + "' should be OK");
        }

        taskFinishingTopLevelService.checkTaskCanBeFinished(task.id);
        processScheduledTasks();
    }

    private void enqueueCheckCacheTasks(Long execContextId) {
        List<Object[]> allTaskRows = taskRepositoryForTest.findAllExecStateAndParamsByExecContextId(execContextId);
        for (Object[] row : allTaskRows) {
            Long taskId = (Long) row[0];
            TaskImpl t = taskRepository.findById(taskId).orElse(null);
            if (t != null && t.execState == EnumsApi.TaskExecState.CHECK_CACHE.value) {
                taskCheckCachingService.putToQueue(new RegisterTaskForCheckCachingEvent(execContextId, taskId));
            }
        }
    }

    private void processScheduledTasks() {
        execContextTaskStateService.processUpdateTaskExecStatesInGraph();
        execContextVariableStateTopLevelService.processFlushing();
        execContextSchedulerService.initTaskVariables();
        execContextSchedulerService.updateExecContextStatuses();
    }
}
