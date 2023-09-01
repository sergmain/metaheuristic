/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.threads.ThreadedPool;
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

    private final ThreadedPool<ResetTasksWithErrorEvent> resetTasksWithErrorEventThreadedPool =
            new ThreadedPool<>("ExecContextTaskResettingTopLevelService-", 1, 0, false, false, this::resetTasksWithErrorForRecovery);

    @PreDestroy
    public void onExit() {
        resetTasksWithErrorEventThreadedPool.shutdown();
    }

    @Async
    @EventListener
    public void handleEvaluateProviderEvent(ResetTasksWithErrorEvent event) {
        resetTasksWithErrorEventThreadedPool.putToQueue(event);
    }

    public void resetTasksWithErrorForRecovery() {
/*
        final int activeCount = executor.getActiveCount();
        if (log.isDebugEnabled()) {
            final long completedTaskCount = executor.getCompletedTaskCount();
            final long taskCount = executor.getTaskCount();
            log.debug("resetTasksInQueue, active task in executor: {}, awaiting tasks: {}", activeCount, taskCount - completedTaskCount);
        }
*/
        resetTasksWithErrorEventThreadedPool.processEvent();
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
            log.error("#155.030 ExecContextTaskState wasn't found for execContext #{}", event.execContextId);
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
            statuses.add(new TaskData.TaskWithRecoveryStatus(taskId, triesWasMade+1, targetState));
        }
        ExecContextSyncService.getWithSyncVoid(event.execContextId, ()->
                ExecContextTaskStateSyncService.getWithSyncVoid(ec.execContextTaskStateId,
                        () -> execContextTaskResettingService.resetTasksWithErrorForRecovery(event.execContextId, statuses)));
        int i=0;
    }

}
