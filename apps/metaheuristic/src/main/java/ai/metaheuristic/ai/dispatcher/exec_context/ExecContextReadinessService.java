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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.event.StartProcessReadinessEvent;
import ai.metaheuristic.ai.dispatcher.event.StartTaskProcessingEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskApiData;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

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
@RequiredArgsConstructor
@Order(1000)
public class ExecContextReadinessService {

    private final ExecContextService execContextService;
    private final ExecContextCache execContextCache;
    private final TaskProviderTopLevelService taskProviderTopLevelService;
    private final ExecContextReconciliationTopLevelService execContextReconciliationTopLevelService;
    private final ExecContextRepository execContextRepository;
    private final ExecContextReadinessStateService execContextReadinessStateService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final DispatcherParamsService dispatcherParamsService;

    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    private final LinkedList<Long> queue = new LinkedList<>();

    @PostConstruct
    public void postConstruct() {
        final List<Long> ids = execContextRepository.findIdsByExecState(EnumsApi.ExecContextState.STARTED.code);
        log.info("Started execContextIds: " + ids);
        execContextReadinessStateService.addAll(ids);
        for (Long notReadyExecContextId : ids) {
            putToQueue(notReadyExecContextId);
        }
        log.info("postConstruct(), queue: {}", getIdsFromQueue());
    }

    private void putToQueue(final Long execContextId) {
        synchronized (queue) {
            if (queue.contains(execContextId)) {
                return;
            }
            queue.add(execContextId);
        }
    }

    @Nullable
    private Long pullFromQueue() {
        synchronized (queue) {
            return queue.pollFirst();
        }
    }

    private List<Long> getIdsFromQueue() {
        synchronized (queue) {
            return new ArrayList<>(queue);
        }
    }

    // this method will be invoked only one time at startup
    @Async
    @EventListener
    @SneakyThrows
    public void checkReadiness(StartProcessReadinessEvent event) {
//        TimeUnit.SECONDS.sleep(5);
        log.info("checkReadiness() here, queue: {}", getIdsFromQueue());
        executor.submit(() -> {
            Long execContextId;
            while ((execContextId = pullFromQueue()) != null) {
                prepare(execContextId);
                execContextReadinessStateService.remove(execContextId);
            }
        });
    }

    private void prepare(Long execContextId) {
        final ExecContextImpl execContext = execContextCache.findById(execContextId, true);
        if (execContext == null) {
            return;
        }
        ExecContextData.SimpleExecContext simpleExecContext = execContext.asSimple();

        Map<Long, TaskApiData.TaskState> states = execContextService.getExecStateOfTasks(execContextId);

        final List<ExecContextData.TaskVertex> vertices = execContextGraphTopLevelService.findAllForAssigning(
                execContext.execContextGraphId, execContext.execContextTaskStateId, true);

        List<Long> taskIds = vertices.stream().map(v -> v.taskId).collect(Collectors.toList());

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
