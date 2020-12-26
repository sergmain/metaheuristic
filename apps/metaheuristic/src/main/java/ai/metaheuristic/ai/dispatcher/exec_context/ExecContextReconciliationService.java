/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.event.UpdateTaskExecStatesInGraphEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.dispatcher.task.TaskStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Serge
 * Date: 10/12/2020
 * Time: 1:38 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextReconciliationService {

    private final ExecContextService execContextService;
    private final ExecContextGraphService execContextGraphService;
    private final TaskRepository taskRepository;
    private final TaskStateService taskStateService;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextTaskResettingService execContextTaskResettingService;
    private final TaskSyncService taskSyncService;
    private final TaskProviderTopLevelService taskProviderTopLevelService;
    private final ApplicationEventPublisher eventPublisher;

    @SuppressWarnings("StatementWithEmptyBody")
    public ExecContextData.ReconciliationStatus reconcileStates(ExecContextImpl execContext) {
        TxUtils.checkTxNotExists();

        ExecContextData.ReconciliationStatus status = new ExecContextData.ReconciliationStatus(execContext.id);

        // Reconcile states in db and in graph
        List<ExecContextData.TaskVertex> rootVertices = execContextGraphService.findAllRootVertices(execContext);
        if (rootVertices.size()>1) {
            log.error("#307.020 Too many root vertices, ill be used only first vertex, actual number: " + rootVertices.size());
        }

        if (rootVertices.isEmpty()) {
            return status;
        }
        Set<ExecContextData.TaskVertex> vertices = execContextGraphService.findDescendants(execContext, rootVertices.get(0).taskId);

        final Map<Long, TaskApiData.TaskState> states = execContextService.getExecStateOfTasks(execContext.id);

        for (ExecContextData.TaskVertex tv : vertices) {

            TaskApiData.TaskState taskState = states.get(tv.taskId);
            if (taskState==null) {
                status.isNullState.set(true);
            }
            else if (System.currentTimeMillis()-taskState.updatedOn>5_000 && tv.execState.value!=taskState.execState) {
                TaskQueue.AllocatedTask allocatedTask = taskProviderTopLevelService.getTaskExecState(execContext.id, tv.taskId);
                if (allocatedTask==null) {
                    if (taskState.execState==EnumsApi.TaskExecState.IN_PROGRESS.value && tv.execState== EnumsApi.TaskExecState.NONE) {
                        log.warn("#307.040 Found different states for task #{}, db: {}, graph: {}, allocatedTask wasn't found, task will be reset",
                                tv.taskId, EnumsApi.TaskExecState.from(taskState.execState), tv.execState);
                        status.taskForResettingIds.add(tv.taskId);
                    }
                    else {
                        log.warn("#307.060 Found different states for task #{}, db: {}, graph: {}, allocatedTask wasn't found, trying to update a state of task in execContext",
                                tv.taskId, EnumsApi.TaskExecState.from(taskState.execState), tv.execState);
                        eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContext.id, tv.taskId));
                    }
                }
                else if (!allocatedTask.assigned) {
                    log.warn("#307.080 Found different states for task #{}, db: {}, graph: {}, assigned: false, state in queue: {}",
                            tv.taskId, EnumsApi.TaskExecState.from(taskState.execState), tv.execState, allocatedTask.state);
                }
                else if ((taskState.execState==EnumsApi.TaskExecState.OK.value || taskState.execState==EnumsApi.TaskExecState.ERROR.value || taskState.execState==EnumsApi.TaskExecState.SKIPPED.value)
                    && taskState.execState==allocatedTask.state.value) {
                    log.warn("#307.100 Found different states for task #{}, db: {}, graph: {}, assigned: false, state in queue: {}",
                            tv.taskId, EnumsApi.TaskExecState.from(taskState.execState), tv.execState, allocatedTask.state);
                    log.warn("#307.120 task #{} will be removed from queue and state of task will be changed in execContext",
                            tv.taskId);

                    taskProviderTopLevelService.deregisterTask(execContext.id, tv.taskId);
                    eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContext.id, tv.taskId));
                }
                else if (taskState.execState==EnumsApi.TaskExecState.IN_PROGRESS.value &&  tv.execState==EnumsApi.TaskExecState.CHECK_CACHE && allocatedTask.state== EnumsApi.TaskExecState.IN_PROGRESS) {
                    // Found different states for task #37131, db: IN_PROGRESS, graph: CHECK_CACHE, assigned: false, state in queue: IN_PROGRESS
                    // normal situation
                }
                else {
                    log.warn("#307.140 Found different states for task #{}, db: {}, graph: {}, assigned: false, state in queue: {}",
                            tv.taskId, EnumsApi.TaskExecState.from(taskState.execState), tv.execState, allocatedTask.state);
                }
            }
        }

        if (status.isNullState.get()) {
            log.info("#307.160 Found non-created task, graph consistency is failed");
            return status;
        }

        final Map<Long, TaskApiData.TaskState> newStates = execContextService.getExecStateOfTasks(execContext.id);

        // fix actual state of tasks (can be as a result of OptimisticLockingException)
        // fix IN_PROCESSING state
        // find and reset all hanging up tasks
        newStates.entrySet().stream()
                .filter(e-> EnumsApi.TaskExecState.IN_PROGRESS.value==e.getValue().execState)
                .forEach(e->{
                    Long taskId = e.getKey();
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
                    if (task != null) {
                        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

                        // did this task hang up at processor?
                        if (task.assignedOn!=null && tpy.task.timeoutBeforeTerminate != null && tpy.task.timeoutBeforeTerminate!=0L) {
                            // +2 is for waiting network communications at the last moment. i.e. wait for 4 seconds more
                            final long multiplyBy2 = (tpy.task.timeoutBeforeTerminate + 2) * 2 * 1000;
                            final long oneHourToMills = TimeUnit.HOURS.toMillis(1);
                            long timeout = Math.min(multiplyBy2, oneHourToMills);
                            if ((System.currentTimeMillis() - task.assignedOn) > timeout) {
                                log.info("#307.080 Reset task #{}, multiplyBy2: {}, timeout: {}", task.id, multiplyBy2, timeout);
                                status.taskForResettingIds.add(task.id);
                            }
                        }
                        else if (task.resultReceived && task.isCompleted) {
                            status.taskIsOkIds.add(task.id);
                        }
                    }
                });
        return status;
    }

    @Transactional
    public Void finishReconciliation(ExecContextData.ReconciliationStatus status) {
        execContextSyncService.checkWriteLockPresent(status.execContextId);

        if (!status.isNullState.get() && status.taskIsOkIds.isEmpty() && status.taskForResettingIds.isEmpty()) {
            return null;
        }
        ExecContextImpl execContext = execContextService.findById(status.execContextId);
        if (execContext==null) {
            return null;
        }
        if (status.isNullState.get()) {
            log.info("#307.180 Found non-created task, graph consistency is failed");
            execContext.completedOn = System.currentTimeMillis();
            execContext.state = EnumsApi.ExecContextState.ERROR.code;
            return null;
        }

        for (Long taskForResettingId : status.taskForResettingIds) {
            taskSyncService.getWithSyncForCreation(taskForResettingId, ()-> execContextTaskResettingService.resetTask(execContext, taskForResettingId));
        }
        for (Long taskIsOkId : status.taskIsOkIds) {
            taskSyncService.getWithSyncNullable(taskIsOkId, ()-> {
                TaskImpl task = taskRepository.findById(taskIsOkId).orElse(null);
                if (task==null) {
                    log.error("#307.200 task is null");
                    return null;
                }
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                taskStateService.updateTaskExecStates(task, EnumsApi.TaskExecState.OK, tpy.task.taskContextId);
                return null;
            });
        }
        return null;
    }

}


