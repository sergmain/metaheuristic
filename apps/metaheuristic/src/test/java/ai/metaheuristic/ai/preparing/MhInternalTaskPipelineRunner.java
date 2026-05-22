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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextEventService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.ExecContextState.FINISHED;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Drives a full MH pipeline execution loop in integration tests for source-codes
 * that use ONLY internal functions (mh.inline-as-variable, mh.permute-*, mh.nop,
 * mh.finish, ...).
 * <p>
 * Modeled on {@code ai.metaheuristic.legal.mhsc.RgPipelineTestExecutionService}, but
 * pared down — no external-function simulation branch here, because the source codes
 * under test do not use external functions. If a future caller's YAML mixes externals
 * in, this runner will fail loudly with a clear "unexpected external function" message
 * rather than silently stalling.
 * <p>
 * Why this exists: {@code PreparingSourceCodeService.findTaskForRegisteringInQueue}
 * only awaits the task-allocator registration pass (i.e. that the {@code
 * FindUnassignedTasksAndRegisterInQueueEvent} is drained). It does NOT await the
 * downstream {@code @Async @EventListener} chain that lands {@code
 * TaskWithInternalContextEvent}s on the internal-context MTQ and runs them. For tests
 * that need to observe intermediate or final task counts deterministically, that gap is
 * a race. This runner closes the race by directly invoking
 * {@code taskWithInternalContextEventService.putToQueue(...)} for each ready internal
 * task and awaiting its terminal state before moving to the next iteration — exactly
 * the pattern the RG harness uses.
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

    /**
     * Run the pipeline until the exec-context reaches FINISHED, or fail.
     *
     * @param execContextId  the exec-context to drive
     * @param maxIterations  upper bound on outer-loop iterations (each iteration scans
     *                       for ready tasks once); a deep YAML may need more, but for
     *                       the permute-* YAMLs in this package, ~20 is generous.
     */
    public void runPipelineToCompletion(Long execContextId, int maxIterations) {
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

            // Find tasks that are ready to execute (NONE-state, internal-context only).
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
                if (tpy.task.context != EnumsApi.FunctionExecContext.internal) {
                    fail("MhInternalTaskPipelineRunner is internal-functions-only, but task #" + taskId +
                            " uses function '" + tpy.task.function.code + "' with context=" + tpy.task.context +
                            ". Use the RG-side runner for source codes mixing externals.");
                }

                log.info("Enqueuing internal function: {} (task #{})", tpy.task.function.code, taskId);
                enqueueInternalFunction(ec, task);
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
     * This is the production path — handles condition evaluation, subprocess creation, SKIP
     * logic — bypassing only the {@code @Async @EventListener} hop that races the test thread.
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
