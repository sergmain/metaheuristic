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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.events.TransferStateFromTaskQueueToExecContextEvent;
import ai.metaheuristic.ai.dispatcher.event.events.UpdateTaskExecStatesInExecContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.exceptions.CommonRollbackException;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.threads.ThreadedPool;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService.importExecContextGraph;

/**
 * @author Serge
 * Date: 12/18/2020
 * Time: 6:37 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextTaskStateService {

    private final ExecContextTaskStateTxService execContextTaskStateService;
    private final TaskRepository taskRepository;
    private final ExecContextCache execContextCache;
    private final ExecContextGraphService execContextGraphService;


    private final ThreadedPool<Long, TransferStateFromTaskQueueToExecContextEvent> threadedPoolMap =
        new ThreadedPool<>("TransferStateFromTaskQueueToExecContext-", 2, true, false, this::transferStateFromTaskQueueToExecContext, ConstsApi.DURATION_NONE );

    private final ThreadedPool<Long, UpdateTaskExecStatesInExecContextEvent> updateTaskExecStatesInGraphEventThreadedPool =
        new ThreadedPool<>("UpdateTaskExecStatesInGraph-", 100, false, false, this::updateTaskExecStatesExecContext, ConstsApi.SECONDS_5);

    @PreDestroy
    public void onExit() {
        threadedPoolMap.shutdown();
        updateTaskExecStatesInGraphEventThreadedPool.shutdown();
    }

    @Async
    @EventListener
    public void handleEvent(TransferStateFromTaskQueueToExecContextEvent event) {
        threadedPoolMap.putToQueue(event);
    }

    @Async
    @EventListener
    public void handleEvent(UpdateTaskExecStatesInExecContextEvent event) {
        updateTaskExecStatesInGraphEventThreadedPool.putToQueue(event);
    }

    public void processUpdateTaskExecStatesInGraph() {
//        updateTaskExecStatesInGraphEventThreadedPool.entrySet().stream().parallel().forEach(e->e.getValue().processEvent());
    }

    public void updateTaskExecStatesExecContext(UpdateTaskExecStatesInExecContextEvent event) {
        try {
            log.debug("call ExecContextTaskStateTopLevelService.updateTaskExecStatesExecContext({}, {})", event.execContextId, event.taskIds);
            TaskImpl task = taskRepository.findByIdReadOnly(event.taskIds);
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
                    () -> TaskSyncService.getWithSyncNullable(event.taskIds,
                            () -> updateTaskExecStatesExecContext(ec.execContextTaskStateId, event.taskIds, EnumsApi.TaskExecState.from(task.execState), taskParams.task.taskContextId)));

        } catch (Throwable th) {
            log.error("417.020 Error, need to investigate ", th);
        }
    }

    private OperationStatusRest updateTaskExecStatesExecContext(Long execContextTaskStateId, Long taskId, EnumsApi.TaskExecState state, String taskContextId) {
        return execContextTaskStateService.updateTaskExecStatesInGraph(execContextTaskStateId, taskId, state, taskContextId);
    }

    public void transferStateFromTaskQueueToExecContext(TransferStateFromTaskQueueToExecContextEvent event) {
        try {
            log.debug("call ExecContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext({})", event.execContextId);
            int i = 1;
            long mills = System.currentTimeMillis();
            do {
                TaskQueue.TaskGroups taskGroup = ExecContextTaskStateSyncService.getWithSync(event.execContextTaskStateId,
                    ()-> transferStateFromTaskQueueToExecContext(event.execContextId, event.execContextTaskStateId));
                if (taskGroup.groups.isEmpty()) {
                    break;
                }
                if (System.currentTimeMillis() - mills > 1_000) {
                    break;
                }
                i++;
            } while (true);
            log.info("417.060 transferStateFromTaskQueueToExecContext() was completed in {} loops within {} milliseconds", i-1, System.currentTimeMillis() - mills);
        } catch (Throwable th) {
            log.error("Error, need to investigate ", th);
        }
    }

    public TaskQueue.TaskGroups transferStateFromTaskQueueToExecContext(Long execContextId, Long execContextTaskStateId) {
        try {
            ExecContextImpl ec = execContextCache.findById(execContextId, true);
            if (ec==null) {
                return TaskQueue.EMPTY;
            }
            ExecContextGraph execContextGraph = execContextGraphService.prepareExecContextGraph(ec.execContextGraphId);
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = importExecContextGraph(execContextGraph.getExecContextGraphParamsYaml());
            ExecContextData.ExecContextDAC execContextDAC = new ExecContextData.ExecContextDAC(execContextId, graph);

            final TaskQueue.TaskGroups taskGroups = execContextTaskStateService.transferStateFromTaskQueueToExecContext(execContextDAC, execContextId, execContextTaskStateId);
            taskGroups.reset();
            return taskGroups;
        } catch (CommonRollbackException e) {
            return TaskQueue.EMPTY;
        }
    }

}
