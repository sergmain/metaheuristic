/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.event.*;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateCache;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Serge
 * Date: 11/24/2021
 * Time: 5:49 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextTaskResettingTopLevelService {

    private final TaskRepository taskRepository;
    private final ExecContextTaskResettingService execContextTaskResettingService;
    private final ExecContextTaskStateCache execContextTaskStateCache;
    private final ExecContextCache execContextCache;

    private static final LinkedList<ResetTasksWithErrorEvent> QUEUE = new LinkedList<>();
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);

    public static void putToQueue(final ResetTasksWithErrorEvent event) {
        synchronized (QUEUE) {
            if (QUEUE.contains(event)) {
                return;
            }
            QUEUE.add(event);
        }
    }

    @Nullable
    private static ResetTasksWithErrorEvent pullFromQueue() {
        synchronized (QUEUE) {
            return QUEUE.pollFirst();
        }
    }

    public void resetTasksWithErrorForRecovery() {
        final int activeCount = executor.getActiveCount();
        if (log.isDebugEnabled()) {
            final long completedTaskCount = executor.getCompletedTaskCount();
            final long taskCount = executor.getTaskCount();
            log.debug("resetTasksInQueue, active task in executor: {}, awaiting tasks: {}", activeCount, taskCount - completedTaskCount);
        }

        if (activeCount>0) {
            return;
        }
        executor.submit(() -> {
            ResetTasksWithErrorEvent event;
            while ((event = pullFromQueue())!=null) {
                resetTasksWithErrorForRecovery(event.execContextId);
            }
        });
    }

    @Async
    @EventListener
    public void resetTask(ResetTaskEvent event) {
        ExecContextSyncService.getWithSyncVoid(event.execContextId, () -> execContextTaskResettingService.resetTaskWithTx(event.execContextId, event.taskId));
    }

    @Async
    @EventListener
    public void resetTaskShort(ResetTaskShortEvent event) {
        TaskImpl task = taskRepository.findById(event.taskId).orElse(null);
        if (task==null || EnumsApi.TaskExecState.isFinishedState(task.execState)) {
            return;
        }
        if (task.assignedOn==null || (System.currentTimeMillis() - task.assignedOn<30_000)) {
            return;
        }
        ExecContextSyncService.getWithSyncVoid(task.execContextId, () -> execContextTaskResettingService.resetTaskWithTx(task.execContextId, event.taskId));
    }

    public void resetTasksWithErrorForRecovery(Long execContextId) {
        TxUtils.checkTxNotExists();

        List<Long> taskIds = taskRepository.findTaskForErrorWithRecoveryState(execContextId);
        if (taskIds.isEmpty()) {
            return;
        }

        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            return;
        }

        ExecContextTaskState execContextTaskState = execContextTaskStateCache.findById(ec.execContextTaskStateId);
        if (execContextTaskState==null) {
            log.error("#155.030 ExecContextTaskState wasn't found for execContext #{}", execContextId);
            return;
        }
        ExecContextTaskStateParamsYaml ectspy = execContextTaskState.getExecContextTaskStateParamsYaml();

        final List<TaskData.TaskWithRecoveryStatus> statuses = new ArrayList<>(taskIds.size()+1);
        for (Long taskId : taskIds) {
            TaskImpl task = taskRepository.findById(taskId).orElse(null);
            if (task==null) {
                continue;
            }
            TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            Integer ai = ectspy.triesWasMade.get(taskId);
            int triesWasMade = ai == null ? 0 : ai;
            int maxTries = tpy.task.triesAfterError == null ? 0 : tpy.task.triesAfterError;
            // after a try of recovering, we don't need to use CACHE. so it'll be NONE
            final EnumsApi.TaskExecState targetState = maxTries > triesWasMade ? EnumsApi.TaskExecState.NONE : EnumsApi.TaskExecState.ERROR;
            statuses.add(new TaskData.TaskWithRecoveryStatus(taskId, triesWasMade+1, targetState));
        }
        ExecContextSyncService.getWithSyncVoid(execContextId, ()->
                ExecContextTaskStateSyncService.getWithSyncVoid(ec.execContextTaskStateId,
                        () -> execContextTaskResettingService.resetTasksWithErrorForRecovery(execContextId, statuses)));
        int i=0;
    }

}
