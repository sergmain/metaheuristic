/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.events.*;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.*;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.threads.MultiTenantedQueue;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/3/2020
 * Time: 8:32 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextTaskAssigningTopLevelService {

    private final Globals globals;
    private final ExecContextCache execContextCache;
    private final ExecContextFSM execContextFSM;
    private final ExecContextGraphService execContextGraphService;
    private final TaskRepository taskRepository;
    private final TaskCheckCachingService taskCheckCachingService;
    private final TaskFinishingTxService taskFinishingTxService;
    private final ExecContextRepository execContextRepository;
    private final ExecContextTaskResettingTopLevelService execContextTaskResettingTopLevelService;
    private final ApplicationEventPublisher eventPublisher;

    public static class UnassignedTasksStat {
        public int found;
        public int allocated;
        public List<String> notAllocatedReasons = new ArrayList<>();

        public void add(UnassignedTasksStat add) {
            this.found += add.found;
            this.allocated += add.allocated;
            this.notAllocatedReasons.addAll(add.notAllocatedReasons);
        }
    }

    private final MultiTenantedQueue<Long, FindUnassignedTasksAndRegisterInQueueEvent> MULTI_TENANTED_QUEUE =
        new MultiTenantedQueue<>(2, ConstsApi.SECONDS_1, true, null, this::findUnassignedTasksAndRegisterInQueue);

    @PreDestroy
    public void onExit() {
        MULTI_TENANTED_QUEUE.clearQueue();
    }

    public void putToQueue(final FindUnassignedTasksAndRegisterInQueueEvent event) {
        MULTI_TENANTED_QUEUE.putToQueue(event);
    }

    @Async
    @EventListener
    public void handleEvaluateProviderEvent(FindUnassignedTasksAndRegisterInQueueEvent event) {
        if (globals.state.awaitingForProcessor) {
            return;
        }
        putToQueue(event);
    }

    private void findUnassignedTasksAndRegisterInQueue(FindUnassignedTasksAndRegisterInQueueEvent event) {
        List<Long> execContextIds = execContextRepository.findAllStartedIds();
        execContextIds.sort(Comparator.naturalOrder());
        UnassignedTasksStat statTotal = new UnassignedTasksStat();
        for (Long execContextId : execContextIds) {
            UnassignedTasksStat stat = findUnassignedTasksAndRegisterInQueueInternal(execContextId);
            statTotal.add(stat);
        }
        if (log.isInfoEnabled()) {
            log.info("703.030 total found {}, allocated {}", statTotal.found, statTotal.allocated);
            for (String notAllocatedReason : statTotal.notAllocatedReasons) {
                log.info("  " + notAllocatedReason);
            }
        }
        if (statTotal.allocated>0) {
            eventPublisher.publishEvent(new NewWebsocketEvent(Enums.WebsocketEventType.task));
        }
    }

    private long mills = 0L;
    @SuppressWarnings("SizeReplaceableByIsEmpty")
    private UnassignedTasksStat findUnassignedTasksAndRegisterInQueueInternal(Long execContextId) {
        TxUtils.checkTxNotExists();

        UnassignedTasksStat stat = new UnassignedTasksStat();

        log.info("703.100 start searching a new tasks for registering, execContextId: #{}", execContextId);
        final ExecContextImpl execContext = execContextCache.findById(execContextId, true);
        if (execContext == null) {
            return stat;
        }
        // do nothing if execContext is finished or stopped
        var execContextState = EnumsApi.ExecContextState.fromCode(execContext.state);
        if (EnumsApi.ExecContextState.isFinishedState(execContextState) || execContextState== EnumsApi.ExecContextState.STOPPED) {
            return stat;
        }

        if (System.currentTimeMillis() - mills > 10_000) {
            eventPublisher.publishEvent(new TransferStateFromTaskQueueToExecContextEvent(execContextId, execContext.execContextTaskStateId));
            mills = System.currentTimeMillis();
        }

        final List<ExecContextData.TaskVertex> vertices = ExecContextSyncService.getWithSync(execContextId,
                () -> execContextGraphService.findAllForAssigning(execContext.execContextGraphId, execContext.execContextTaskStateId, true));

        stat.found = vertices.size();
        log.debug("703.140 found {} tasks for registering, execContextId: #{}", vertices.size(), execContextId);

        if (vertices.isEmpty()) {
            execContextTaskResettingTopLevelService.handleResetTasksWithErrorEvent(new ResetTasksWithErrorEvent(execContextId));
            return stat;
        }

        List<ExecContextData.TaskVertex> filteredVertices = new ArrayList<>();
        for (ExecContextData.TaskVertex vertex : vertices) {
            final TaskQueue.AllocatedTask allocatedTask = TaskQueueService.alreadyRegisteredAsTaskWithSync(vertex.taskId);
            if (allocatedTask==null) {
                filteredVertices.add(vertex);
            }
            else {
                if (allocatedTask.state == EnumsApi.TaskExecState.CHECK_CACHE) {
                    taskCheckCachingService.putToQueue(new RegisterTaskForCheckCachingEvent(execContextId, allocatedTask.queuedTask.taskId));
                }
            }
        }

        if (stat.found>0 && filteredVertices.isEmpty() && log.isInfoEnabled()) {
            String suffix = vertices.size()>5 ? " and more, total: " + vertices.size() : "";
            stat.notAllocatedReasons.add("all tasks were already registered, ids: " +
                    vertices.stream().limit(5).map(o->o.taskId!=null ? o.taskId.toString() : "null").collect(Collectors.joining(", "))+suffix);
        }

        final ExecContextParamsYaml execContextParamsYaml = execContext.getExecContextParamsYaml();

        int page = 0;
        List<Long> taskIds;
        while ((taskIds = execContextFSM.getAllByProcessorIdIsNullAndExecContextIdAndIdIn(execContextId, filteredVertices, page++)).size()>0) {

            for (Long taskId : taskIds) {
                TaskImpl task = taskRepository.findByIdReadOnly(taskId);
                if (task==null) {
                    if (log.isInfoEnabled()) stat.notAllocatedReasons.add("task #"+ taskId +" wasn't found");
                    continue;
                }
                if (EnumsApi.TaskExecState.isFinishedState(task.execState)) {
                    EnumsApi.TaskExecState state = EnumsApi.TaskExecState.from(task.execState);
                    if (log.isWarnEnabled()) {
                        String es = "703.380 Task #"+task.getId()+" was already processed with status " + state;
                        log.warn(es);
                        if (log.isInfoEnabled()) stat.notAllocatedReasons.add(es);
                    }
                    // this situation will be handled while a reconciliation stage
                    continue;
                }

                if (task.execState == EnumsApi.TaskExecState.INIT.value) {
                    eventPublisher.publishEvent(new InitVariablesEvent(task.id));
                    if (log.isInfoEnabled()) stat.notAllocatedReasons.add("703.410 task #"+task.getId()+" task.execState == EnumsApi.TaskExecState.INIT");
                    continue;
                }

                if (task.execState == EnumsApi.TaskExecState.CHECK_CACHE.value) {
                    // cache will be checked via Schedulers.DispatcherSchedulers.processCheckCaching()
                    taskCheckCachingService.putToQueue(new RegisterTaskForCheckCachingEvent(execContextId, taskId));
                    if (log.isInfoEnabled()) stat.notAllocatedReasons.add("703.440 task #"+task.getId()+" task.execState == EnumsApi.TaskExecState.CHECK_CACHE");
                    continue;
                }

                if (task.execState==EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    // this state is occur when the state in graph is NONE or CHECK_CACHE, and the state in DB is IN_PROGRESS
                    logDebugAboutTask(task);
                    if (log.isInfoEnabled()) stat.notAllocatedReasons.add("703.470 task #"+task.getId()+" task.execState==EnumsApi.TaskExecState.IN_PROGRESS");
                    continue;
                }

                if (task.execState==EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value) {
                    // this state is occur when the state in graph is NONE or CHECK_CACHE, and the state in DB is ERROR_WITH_RECOVERY
                    if (log.isInfoEnabled()) stat.notAllocatedReasons.add("703.500 task #"+task.getId()+" task.execState == EnumsApi.TaskExecState.ERROR_WITH_RECOVERY");
                    continue;
                }

                if (TaskQueueService.alreadyRegisteredWithSync(task.id)) {
                    if (log.isInfoEnabled()) stat.notAllocatedReasons.add("703.530 task #"+task.getId()+" task is already registered");
                    continue;
                }

                if (task.execState == EnumsApi.TaskExecState.NONE.value) {
                    final TaskParamsYaml taskParamYaml;
                    try {
                        taskParamYaml = task.getTaskParamsYaml();
                    }
                    catch (YAMLException e) {
                        log.error("703.560 Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.getMessage(), task.getParams());
                        final String es = S.f("703.590 Task #%s has broken params yaml and will be skipped", task.id);
                        taskFinishingTxService.finishWithErrorWithTx(task.id, es);
                        if (log.isInfoEnabled()) stat.notAllocatedReasons.add(es);
                        continue;
                    }
                    switch(taskParamYaml.task.context) {
                        case external:
                            if (TaskProviderTopLevelService.registerTask(execContextParamsYaml, task, taskParamYaml)) {
                                stat.allocated++;
                            }
                            else {
                                if (log.isInfoEnabled()) stat.notAllocatedReasons.add("task #"+task.getId()+" task is already registered in task queue");
                            }
                            break;
                        case internal:
                            // all tasks with internal function will be processed in a different thread after registering in TaskQueue
                            log.debug("703.620 start processing an internal function {} for task #{}", taskParamYaml.task.function.code, task.id);
                            if (TaskProviderTopLevelService.registerInternalTask(execContextId, taskId, taskParamYaml)) {
                                stat.allocated++;
                            }
                            eventPublisher.publishEvent(new TaskWithInternalContextEvent(execContext.sourceCodeId, execContextId, taskId));
                            break;
                        case long_running:
                            break;
                    }
                    continue;
                }
                throw new IllegalStateException("703.640 not-handled task state: " + EnumsApi.TaskExecState.from(task.execState));
            }
        }
        TaskProviderTopLevelService.lock(execContextId);
        log.debug("703.670 allocated {} of new tasks in execContext #{}", stat.allocated, execContextId);

        return stat;
    }

    private static void logDebugAboutTask(TaskImpl task) {
        if (!log.isDebugEnabled()) {
            return;
        }
        try {
            TaskParamsYaml taskParamYaml = task.getTaskParamsYaml();
            if (taskParamYaml.task.context != EnumsApi.FunctionExecContext.internal) {
                log.warn("703.700 task #{} with IN_PROGRESS is there? Function: {}", task.id, taskParamYaml.task.function.code);
            }
        }
        catch (Throwable th) {
            log.warn("703.720 Error parsing taskParamsYaml, error: " + th.getMessage());
            log.warn("703.740 task #{} with IN_PROGRESS is there?", task.id);
        }
    }
}
