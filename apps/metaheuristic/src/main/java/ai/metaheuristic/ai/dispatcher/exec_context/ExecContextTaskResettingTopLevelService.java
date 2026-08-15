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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.events.ResetTaskEvent;
import ai.metaheuristic.ai.dispatcher.event.events.ResetTaskShortEvent;
import ai.metaheuristic.ai.dispatcher.event.events.ResetTasksWithErrorEvent;
import ai.metaheuristic.ai.dispatcher.execution_gate.ExecutionGateService;
import ai.metaheuristic.ai.dispatcher.execution_gate.ExecutionGateUtils;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorCoreRepository;
import ai.metaheuristic.ai.yaml.execution_gate.ExecutionGateParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.commons.utils.FunctionAnalyzerUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import org.jspecify.annotations.Nullable;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.threads.MultiTenantedQueue;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 11/24/2021
 * Time: 5:49 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextTaskResettingTopLevelService {

    private final TaskRepository taskRepository;
    private final ExecContextTaskResettingService execContextTaskResettingService;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextCache execContextCache;
    private final ExecutionGateService executionGateService;
    private final ProcessorCoreRepository processorCoreRepository;

    private final MultiTenantedQueue<Long, ResetTasksWithErrorEvent> resetTasksWithErrorEventThreadedPool =
            new MultiTenantedQueue<>(100, ConstsApi.SECONDS_10, true, "ExecContextTaskResetting-", this::resetTasksWithErrorForRecovery);

    /**
     * Exposed through an accessor rather than as a public field: this bean carries {@code @Async}
     * methods and is therefore proxied, and a field read on the proxy sees the subclass's own
     * uninitialized field instead of the target's. A method call is delegated to the target.
     */
    public MultiTenantedQueue<Long, ResetTasksWithErrorEvent> getResetTasksWithErrorEventThreadedPool() {
        return resetTasksWithErrorEventThreadedPool;
    }

    @PreDestroy
    public void onExit() {
        resetTasksWithErrorEventThreadedPool.shutdown();
    }

    @Async
    @EventListener
    public void handleResetTasksWithErrorEvent(ResetTasksWithErrorEvent event) {
        resetTasksWithErrorEventThreadedPool.putToQueue(event);
    }

    @Async
    @EventListener
    public void resetTask(ResetTaskEvent event) {
        ExecContextSyncService.getWithSyncVoid(event.execContextId, () -> execContextTaskResettingService.resetTaskWithTx(event.execContextId, event.taskId));
    }

    @Async
    @EventListener
    public void resetTaskShort(ResetTaskShortEvent event) {
        TaskImpl task = taskRepository.findByIdReadOnly(event.taskId);
        if (task==null || EnumsApi.TaskExecState.isFinishedState(task.execState)) {
            return;
        }
        if (task.assignedOn==null || (System.currentTimeMillis() - task.assignedOn<30_000)) {
            return;
        }
        ExecContextSyncService.getWithSyncVoid(task.execContextId, () -> execContextTaskResettingService.resetTaskWithTx(task.execContextId, event.taskId));
    }

    /**
     * Opens a durable block because a Function's own analyzer matched what it printed.
     *
     * <p>Idempotent by construction: re-blocking a key already blocked for at least as long does not
     * open a transaction, so a Function failing across many sibling Tasks costs one write, not one per
     * Task.
     */
    private void openGateFromAnalyzer(ExecContextImpl ec, TaskImpl task, TaskParamsYaml tpy, FunctionConfigYaml.Analyzer hit) {
        final Long processorId = processorIdOf(task);
        final String refKey = ExecutionGateUtils.refKeyFor(hit.scope, ec.companyId, processorId, tpy);
        if (refKey==null) {
            log.warn("156.050 analyzer '{}' of function {} declares scope {}, but no key could be built for task #{}",
                    hit.name, tpy.task.function.code, hit.scope, task.id);
            return;
        }
        final ExecutionGateParamsYaml params = new ExecutionGateParamsYaml();
        params.triggeredByTaskId = task.id;
        params.functionCode = tpy.task.function.code;
        params.processorId = processorId;
        params.matchedPattern = String.join(" | ", hit.regex);
        params.incrementTries = hit.incrementTries;

        final long blockedUntil = System.currentTimeMillis() + FunctionAnalyzerUtils.parseTimeout(hit.timeout).toMillis();
        log.warn("156.060 analyzer '{}' of function {} matched output of task #{}, blocking {} '{}' until {}",
                hit.name, tpy.task.function.code, task.id, hit.scope, refKey, blockedUntil);
        executionGateService.quarantine(hit.scope, refKey, blockedUntil, hit.name, params);
    }

    /** A Task records the core it ran on; a block covers the whole Processor that core belongs to. */
    @Nullable
    private Long processorIdOf(TaskImpl task) {
        return task.coreId==null ? null : processorCoreRepository.findProcessorIdByCoreId(task.coreId);
    }

    public void resetTasksWithErrorForRecovery(ResetTasksWithErrorEvent event) {
        TxUtils.checkTxNotExists();

        List<Long> taskIds = taskRepository.findTaskForErrorWithRecoveryState(event.execContextId);
        if (taskIds.isEmpty()) {
            return;
        }

        ExecContextImpl ec = execContextCache.findById(event.execContextId, true);
        if (ec==null) {
            return;
        }

        ExecContextTaskState execContextTaskState = execContextTaskStateRepository.findById(ec.execContextTaskStateId).orElse(null);
        if (execContextTaskState==null) {
            log.error("156.030 ExecContextTaskState wasn't found for execContext #{}", event.execContextId);
            return;
        }
        ExecContextTaskStateParamsYaml ectspy = execContextTaskState.getExecContextTaskStateParamsYaml();

        final List<TaskData.TaskWithRecoveryStatus> statuses = new ArrayList<>(taskIds.size()+1);
        for (Long taskId : taskIds) {
            TaskImpl task = taskRepository.findByIdReadOnly(taskId);
            if (task==null) {
                continue;
            }
            TaskParamsYaml tpy = task.getTaskParamsYaml();
            Integer ai = ectspy.triesWasMade.get(taskId);
            int triesWasMade = ai == null ? 0 : ai;
            int maxTries = tpy.task.triesAfterError == null ? 0 : tpy.task.triesAfterError;
            // after a try of recovering, we don't need to use CACHE. so it'll be NONE
            final EnumsApi.TaskExecState targetState = maxTries > triesWasMade ? EnumsApi.TaskExecState.NONE : EnumsApi.TaskExecState.ERROR;

            // ask the Function's own rules what its output meant. The Function author is the one who
            // knows what their tool prints when its key is exhausted or its host is broken; nothing
            // here interprets the text itself.
            final FunctionConfigYaml.Analyzer hit = FunctionAnalyzerUtils.firstHitInExecResults(
                    executionGateService.analyzersOf(tpy.task.function.code),
                    FunctionExecUtils.to(task.functionExecResults));
            if (hit!=null) {
                openGateFromAnalyzer(ec, task, tpy, hit);
            }
            // incrementTries=false means the failure was never this Task's fault, so the attempt is not
            // charged against it - that is the whole difference between a free retry and a normal one
            final int tries = (hit!=null && !hit.incrementTries) ? triesWasMade : triesWasMade+1;

            log.warn("999.010 resetTasksWithErrorForRecovery: task #{}, currentState: {}, targetState: {}, triesWasMade: {}, maxTries: {}, execContextId: {}", taskId, EnumsApi.TaskExecState.from(task.execState), targetState, triesWasMade, maxTries, event.execContextId);
            statuses.add(new TaskData.TaskWithRecoveryStatus(taskId, tries, targetState));
        }
        ExecContextSyncService.getWithSyncVoid(event.execContextId, ()->
                ExecContextTaskStateSyncService.getWithSyncVoid(ec.execContextTaskStateId,
                        () -> execContextTaskResettingService.resetTasksWithErrorForRecovery(event.execContextId, statuses)));
        int i=0;
    }

}
