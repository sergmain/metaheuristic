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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.event.events.ExecContextReadinessEvent;
import ai.metaheuristic.ai.dispatcher.event.events.StartProcessReadinessEvent;
import ai.metaheuristic.ai.dispatcher.event.events.StartTaskProcessingEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskTxService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.commons.utils.threads.MultiTenantedQueue;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 9/27/2021
 * Time: 11:52 AM
 *
 * order is 1000 to be sure that all other classes were already prepared and instantiated
 */
@Service
@Profile("dispatcher")
@Slf4j
@Order(1000)
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextReadinessService {

    private final ExecContextCache execContextCache;
    private final TaskProviderTopLevelService taskProviderTopLevelService;
    private final ExecContextReconciliationTopLevelService execContextReconciliationTopLevelService;
    private final ExecContextGraphService execContextGraphService;
    private final DispatcherParamsService dispatcherParamsService;
    private final TaskTxService taskTxService;
    private final ExecContextRepository execContextRepository;
    private final ExecContextReadinessStateService execContextReadinessStateService;

    private MultiTenantedQueue<Long, ExecContextReadinessEvent> startProcessReadinessEventThreadedPool;

    @PostConstruct
    public void init() {
        startProcessReadinessEventThreadedPool = new MultiTenantedQueue<>(100, Duration.ZERO, false, "ExecContextReadinessService-", (event) -> {
            prepare(event.getId());
            execContextReadinessStateService.remove(event.getId());
        });
    }

    @PreDestroy
    public void onExit() {
        startProcessReadinessEventThreadedPool.shutdown();
    }

    // this method will be invoked only one time at startup
    @SuppressWarnings("unused")
    @Async
    @EventListener
    public void processSessionEvent(StartProcessReadinessEvent event) {
        try {
            final List<Long> ids = execContextRepository.findIdsByExecState(EnumsApi.ExecContextState.STARTED.code);
            log.info("Started execContextIds: " + ids);
            execContextReadinessStateService.addAll(ids);
            for (Long notReadyExecContextId : ids) {
                startProcessReadinessEventThreadedPool.putToQueue(new ExecContextReadinessEvent(notReadyExecContextId));
            }
        } catch (Throwable th) {
            log.error("Error in async", th);
        }
    }

    private void prepare(Long execContextId) {
        final ExecContextImpl execContext = execContextCache.findById(execContextId, true);
        if (execContext == null) {
            return;
        }
        ExecContextData.SimpleExecContext simpleExecContext = execContext.asSimple();

        Map<Long, TaskApiData.TaskState> states = taskTxService.getExecStateOfTasks(execContextId);

        final List<ExecContextData.TaskVertex> vertices = execContextGraphService.findAllForAssigning(execContext.execContextGraphId, execContext.execContextTaskStateId, true);

        List<Long> taskIds = vertices.stream().map(v -> v.taskId).toList();

        for (Map.Entry<Long, TaskApiData.TaskState> entry : states.entrySet()) {
            final Long taskId = entry.getKey();
            if (!taskIds.contains(taskId)) {
                continue;
            }
            if (!EnumsApi.TaskExecState.isFinishedState(entry.getValue().execState)) {
                if (dispatcherParamsService.isLongRunning(taskId)) {
                    if (entry.getValue().execState != EnumsApi.TaskExecState.IN_PROGRESS.value) {
                        taskProviderTopLevelService.registerTask(simpleExecContext, taskId);
                    }
                }
                else {
                    taskProviderTopLevelService.registerTask(simpleExecContext, taskId);
                    if (entry.getValue().execState == EnumsApi.TaskExecState.IN_PROGRESS.value) {
                        taskProviderTopLevelService.processStartTaskProcessing(new StartTaskProcessingEvent(execContextId, taskId));
                    }
                }
            }
        }
        execContextReconciliationTopLevelService.reconcileStates(execContext.id, execContext.execContextGraphId, execContext.execContextTaskStateId);
    }

}
