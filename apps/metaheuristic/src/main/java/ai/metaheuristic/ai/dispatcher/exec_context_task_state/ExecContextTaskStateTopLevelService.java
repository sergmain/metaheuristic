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

package ai.metaheuristic.ai.dispatcher.exec_context_task_state;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.events.TransferStateFromTaskQueueToExecContextEvent;
import ai.metaheuristic.ai.dispatcher.event.events.UpdateTaskExecStatesInGraphEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.threads.ThreadedPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 12/18/2020
 * Time: 6:37 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextTaskStateTopLevelService {

    private final ExecContextTaskStateService execContextTaskStateService;
    private final TaskRepository taskRepository;
    private final ExecContextCache execContextCache;

    private final LinkedHashMap<Long, ThreadedPool<UpdateTaskExecStatesInGraphEvent>> updateTaskExecStatesInGraphEventThreadedPool = new LinkedHashMap<>(100) {
        protected boolean removeEldestEntry(Map.Entry<Long, ThreadedPool<UpdateTaskExecStatesInGraphEvent>> entry) {
            return this.size()>50 && entry.getValue().getQueueSize()==0 && entry.getValue().getActiveCount()==0;
        }
    };

    @Async
    @EventListener
    public void handleEvent(UpdateTaskExecStatesInGraphEvent event) {
        updateTaskExecStatesInGraphEventThreadedPool.computeIfAbsent(event.execContextId,
                (id)-> new ThreadedPool<>(1, 0, false, false, this::updateTaskExecStatesInGraph)).putToQueue(event);
    }

    public void processUpdateTaskExecStatesInGraph() {
        updateTaskExecStatesInGraphEventThreadedPool.entrySet().stream().parallel().forEach(e->e.getValue().processEvent());
    }

    public void updateTaskExecStatesInGraph(UpdateTaskExecStatesInGraphEvent event) {
        try {
            log.debug("call ExecContextTaskStateTopLevelService.updateTaskExecStatesInGraph({}, {})", event.execContextId, event.taskId);
            TaskImpl task = taskRepository.findByIdReadOnly(event.taskId);
            if (task==null) {
                return;
            }
            TaskParamsYaml taskParams = task.getTaskParamsYaml();
            if (!event.execContextId.equals(task.execContextId)) {
                log.error("417.020 (!execContextId.equals(task.execContextId))");
                return;
            }
            ExecContextImpl ec = execContextCache.findById(task.execContextId, true);
            if (ec==null) {
                return;
            }
            ExecContextTaskStateSyncService.getWithSyncNullable(ec.execContextTaskStateId,
                    () -> TaskSyncService.getWithSyncNullable(event.taskId,
                            () -> updateTaskExecStatesInGraph(ec.execContextGraphId, ec.execContextTaskStateId, event.taskId, EnumsApi.TaskExecState.from(task.execState), taskParams.task.taskContextId)));

        } catch (Throwable th) {
            log.error("417.020 Error, need to investigate ", th);
        }
    }

    private OperationStatusRest updateTaskExecStatesInGraph(Long execContextGraphId, Long execContextTaskStateId, Long taskId, EnumsApi.TaskExecState state, String taskContextId) {
        return execContextTaskStateService.updateTaskExecStatesInGraph(execContextGraphId, execContextTaskStateId, taskId, state, taskContextId);
    }

    private final LinkedHashMap<Long, ThreadedPool<TransferStateFromTaskQueueToExecContextEvent>> threadedPoolMap = new LinkedHashMap<>(100) {
        protected boolean removeEldestEntry(Map.Entry<Long, ThreadedPool<TransferStateFromTaskQueueToExecContextEvent>> entry) {
            return this.size()>50 && entry.getValue().getQueueSize()==0 && entry.getValue().getActiveCount()==0;
        }
    };

    @Async
    @EventListener
    public void handleEvent(TransferStateFromTaskQueueToExecContextEvent event) {
        threadedPoolMap.computeIfAbsent(event.execContextId, (id)-> new ThreadedPool<>(2, this::transferStateFromTaskQueueToExecContext)).putToQueue(event);
    }

    public void transferStateFromTaskQueueToExecContext(TransferStateFromTaskQueueToExecContextEvent event) {
        try {
            log.debug("call ExecContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext({})", event.execContextId);
            TaskQueue.TaskGroup taskGroup;
            int i = 1;
            long mills = System.currentTimeMillis();
            while ((taskGroup =
                    ExecContextGraphSyncService.getWithSync(event.execContextGraphId, ()->
                            ExecContextTaskStateSyncService.getWithSync(event.execContextTaskStateId, ()->
                                    transferStateFromTaskQueueToExecContext(
                                            event.execContextId, event.execContextGraphId, event.execContextTaskStateId))))!=null) {
                taskGroup.reset();
                if (System.currentTimeMillis() - mills > 1_000) {
                    break;
                }
                i++;
            }
            log.info("417.060 transferStateFromTaskQueueToExecContext() was completed in {} loops within {} milliseconds", i-1, System.currentTimeMillis() - mills);
        } catch (Throwable th) {
            log.error("Error, need to investigate ", th);
        }
    }

    @Nullable
    public TaskQueue.TaskGroup transferStateFromTaskQueueToExecContext(Long execContextId, Long execContextGraphId, Long execContextTaskStateId) {
        return execContextTaskStateService.transferStateFromTaskQueueToExecContext(execContextId, execContextGraphId, execContextTaskStateId);
    }

}
