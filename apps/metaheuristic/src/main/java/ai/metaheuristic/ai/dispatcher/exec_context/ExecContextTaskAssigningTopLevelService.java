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
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.RegisterTaskForCheckCachingEvent;
import ai.metaheuristic.ai.dispatcher.event.ResetTasksWithErrorEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.event.TransferStateFromTaskQueueToExecContextEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

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
@RequiredArgsConstructor
public class ExecContextTaskAssigningTopLevelService {

    private final ExecContextCache execContextCache;
    private final ExecContextFSM execContextFSM;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final TaskRepository taskRepository;
    private final TaskCheckCachingTopLevelService taskCheckCachingTopLevelService;
    private final TaskFinishingService taskFinishingService;
    private final ExecContextRepository execContextRepository;
    private final ApplicationEventPublisher eventPublisher;

    public static class UnassignedTasksStat {
        public int found;
        public int allocated;

        public void add(UnassignedTasksStat add) {
            this.found += add.found;
            this.allocated += add.allocated;
        }
    }

    private long mills = 0L;

    public void findUnassignedTasksAndRegisterInQueue() {
        // TODO P3 2022-03-11 this commented code is for optimized queries. maybe it should be deleted as not actual
//        log.info("Invoking execContextTopLevelService.findUnassignedTasksAndRegisterInQueue()");
//        boolean allocatedTaskMoreThan = TaskQueueService.allocatedTaskMoreThan(100);
//        log.warn("#703.010 found {} tasks for registering, execCOntextId: #{}", vertices.size(), execContextId);

        List<Long> execContextIds = execContextRepository.findAllStartedIds();
        execContextIds.sort((Comparator.naturalOrder()));
        UnassignedTasksStat statTotal = new UnassignedTasksStat();
        for (Long execContextId : execContextIds) {
            UnassignedTasksStat stat = findUnassignedTasksAndRegisterInQueue(execContextId);
            statTotal.add(stat);
        }
        log.warn("#703.030 total found {}, allocated {}", statTotal.found, statTotal.allocated);
    }

    public UnassignedTasksStat findUnassignedTasksAndRegisterInQueue(Long execContextId) {

        UnassignedTasksStat stat = new UnassignedTasksStat();

        log.warn("#703.100 start searching a new tasks for registering, execContextId: #{}", execContextId);
        final ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return stat;
        }

        if (System.currentTimeMillis() - mills > 10_000) {
            eventPublisher.publishEvent(new TransferStateFromTaskQueueToExecContextEvent(
                    execContextId, execContext.execContextGraphId, execContext.execContextTaskStateId));
            mills = System.currentTimeMillis();
        }

        final List<ExecContextData.TaskVertex> vertices = ExecContextSyncService.getWithSync(execContextId,
                () -> execContextGraphTopLevelService.findAllForAssigning(
                        execContext.execContextGraphId, execContext.execContextTaskStateId, true));

        stat.found = vertices.size();
        log.debug("#703.140 found {} tasks for registering, execContextId: #{}", vertices.size(), execContextId);

        if (vertices.isEmpty()) {
            ExecContextTaskResettingTopLevelService.putToQueue(new ResetTasksWithErrorEvent(execContextId));
            return stat;
        }

        List<ExecContextData.TaskVertex> filteredVertices = vertices.stream()
                .filter(o->!TaskQueueService.alreadyRegisteredWithSync(o.taskId))
                .collect(Collectors.toList());

        int page = 0;
        List<Long> taskIds;
        boolean isEmpty = true;
        while ((taskIds = execContextFSM.getAllByProcessorIdIsNullAndExecContextIdAndIdIn(execContextId, filteredVertices, page++)).size()>0) {
            isEmpty = false;

            for (Long taskId : taskIds) {
                TaskImpl task = taskRepository.findById(taskId).orElse(null);
                if (task==null) {
                    continue;
                }
                if (task.execState == EnumsApi.TaskExecState.CHECK_CACHE.value) {
                    taskCheckCachingTopLevelService.putToQueue(new RegisterTaskForCheckCachingEvent(execContextId, taskId));
                    // cache will be checked via Schedulers.DispatcherSchedulers.processCheckCaching()
                    continue;
                }

                if (task.execState==EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    // this state is occur when the state in graph is NONE or CHECK_CACHE, and the state in DB is IN_PROGRESS
                    logDebugAboutTask(task);
                    continue;
                }

                if (TaskQueueService.alreadyRegisteredWithSync(task.id)) {
                    continue;
                }

                final TaskParamsYaml taskParamYaml;
                try {
                    taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                }
                catch (YAMLException e) {
                    log.error("#703.260 Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.getMessage(), task.getParams());
                    taskFinishingService.finishWithErrorWithTx(task.id, S.f("#703.260 Task #%s has broken params yaml and will be skipped", task.id));
                    continue;
                }
                if (task.execState == EnumsApi.TaskExecState.NONE.value) {
                    switch(taskParamYaml.task.context) {
                        case external:
                            if (TaskProviderTopLevelService.registerTask(execContext, task, taskParamYaml)) {
                                stat.allocated++;
                            }
                            break;
                        case internal:
                            // all tasks with internal function will be processed in a different thread after registering in TaskQueue
                            log.debug("#703.300 start processing an internal function {} for task #{}", taskParamYaml.task.function.code, task.id);
                            if (TaskProviderTopLevelService.registerInternalTask(execContextId, taskId, taskParamYaml)) {
                                stat.allocated++;
                            }
                            eventPublisher.publishEvent(new TaskWithInternalContextEvent(execContext.sourceCodeId, execContextId, taskId));
                            break;
                        case long_running:
                            break;
                    }
                }
                else {
                    EnumsApi.TaskExecState state = EnumsApi.TaskExecState.from(task.execState);
                    log.warn("#703.380 Task #{} with function '{}' was already processed with status {}",
                            task.getId(), taskParamYaml.task.function.code, state);
                    // this situation will be handled while a reconciliation stage
                }
            }
        }
        TaskProviderTopLevelService.lock(execContextId);
        log.debug("#703.500 allocated {} of new taks in execContext #{}", stat.allocated, execContextId);

        return stat;
    }

    private static void logDebugAboutTask(TaskImpl task) {
        if (!log.isDebugEnabled()) {
            return;
        }
        try {
            TaskParamsYaml taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            if (taskParamYaml.task.context != EnumsApi.FunctionExecContext.internal) {
                log.warn("#703.520 task #{} with IN_PROGRESS is there? Function: {}", task.id, taskParamYaml.task.function.code);
            }
        }
        catch (Throwable th) {
            log.warn("#703.540 Error parsing taskParamsYaml, error: " + th.getMessage());
            log.warn("#703.560 task #{} with IN_PROGRESS is there?", task.id);
        }
    }
}
