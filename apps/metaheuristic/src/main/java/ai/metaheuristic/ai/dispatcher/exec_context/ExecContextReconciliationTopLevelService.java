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
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsTopLevelService;
import ai.metaheuristic.ai.dispatcher.event.UpdateTaskExecStatesInGraphEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static ai.metaheuristic.api.EnumsApi.FunctionExecContext;
import static ai.metaheuristic.api.EnumsApi.TaskExecState;

/**
 * @author Serge
 * Date: 4/20/2021
 * Time: 9:58 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextReconciliationTopLevelService {

    private final ExecContextService execContextService;
    private final ExecContextGraphService execContextGraphService;
    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DispatcherParamsTopLevelService dispatcherParamsTopLevelService;
    private final ExecContextReadinessStateService execContextReadinessStateService;

    public ExecContextData.ReconciliationStatus reconcileStates(final Long execContextId, final Long execContextGraphId, final Long execContextTaskStateId) {
        TxUtils.checkTxNotExists();
        ExecContextData.ReconciliationStatus status = new ExecContextData.ReconciliationStatus(execContextId);

        if (execContextReadinessStateService.isNotReady(execContextId)) {
            return status;
        }

        // Reconcile states in db and in graph
        List<ExecContextData.TaskVertex> rootVertices = execContextGraphService.findAllRootVertices(execContextGraphId);
        if (rootVertices.size()>1) {
            log.error("#307.020 Too many root vertices, Will be used only first vertex, actual number: " + rootVertices.size());
        }

        if (rootVertices.isEmpty()) {
            return status;
        }
        final Set<ExecContextData.TaskWithState> vertices = execContextGraphService.findDescendantsWithState(
                execContextGraphId, execContextTaskStateId, rootVertices.get(0).taskId);

        final Map<Long, TaskApiData.TaskState> states = execContextService.getExecStateOfTasks(execContextId);
        final Map<Long, TaskQueue.AllocatedTask> allocatedTasks = TaskProviderTopLevelService.getTaskExecStates(execContextId);

        for (ExecContextData.TaskWithState tv : vertices) {

            TaskApiData.TaskState taskState = states.get(tv.taskId);
            if (taskState==null) {
                status.isNullState.set(true);
            }
            else if (System.currentTimeMillis()-taskState.updatedOn>5_000 && tv.state.value!=taskState.execState) {

                TaskQueue.AllocatedTask allocatedTask = allocatedTasks.get(tv.taskId);
                if (allocatedTask==null) {
                    if (taskState.execState== TaskExecState.IN_PROGRESS.value && tv.state== TaskExecState.NONE) {
                        if (dispatcherParamsTopLevelService.isLongRunning(tv.taskId)) {
                            log.warn("#307.030 Found different states for task #{}, db: {}, graph: {}, allocatedTask wasn't found, state of long-running task will be updated in graph",
                                    tv.taskId, TaskExecState.from(taskState.execState), tv.state);
                            eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                        }
                        else{
                            // #307.040 Found different states for task #191791, db: IN_PROGRESS, graph: NONE, allocatedTask wasn't found, non-long-running task will be reset
                            log.warn("#307.040 Found different states for task #{}, db: {}, graph: {}, allocatedTask wasn't found, non-long-running task will be reset",
                                    tv.taskId, TaskExecState.from(taskState.execState), tv.state);
                            eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
//                            status.taskForResettingIds.add(tv.taskId);
                        }
                    }
                    else if (taskState.execState== TaskExecState.NONE.value && tv.state== TaskExecState.CHECK_CACHE) {
                        // #307.060 Found different states for task, db: NONE, graph: CHECK_CACHE, allocatedTask wasn't found
                        // ---> This is a normal situation, will occur after restarting dispatcher
                    }
                    else if (taskState.execState== TaskExecState.OK.value && tv.state== TaskExecState.CHECK_CACHE) {
                        // #307.055 Found different states for task #375939, db: OK, graph: CHECK_CACHE, allocatedTask wasn't found. trying to update a state of task in execContext.
                        log.warn("#307.055 Found different states for task #{}, db: {}, graph: {}, allocatedTask wasn't found. trying to update a state of task in execContext.",
                                tv.taskId, TaskExecState.from(taskState.execState), tv.state);
                        // this situation can occure if task was stoped, then MH was restarted, and then task was started again
                        eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                    }
                    else {
                        log.warn("#307.060 Found different states for task #{}, db: {}, graph: {}, allocatedTask wasn't found, trying to update a state of task in execContext",
                                tv.taskId, TaskExecState.from(taskState.execState), tv.state);
                        eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                    }
                }
                else if (!allocatedTask.assigned) {
                    if (taskState.execState== TaskExecState.NONE.value &&  tv.state== TaskExecState.CHECK_CACHE && allocatedTask.state==null) {
                        // #307.070 Found different states for task, db: NONE, graph: CHECK_CACHE, assigned: false, state in queue: null
                        // ---> This is a normal situation, will occur after restarting a dispatcher
                        log.warn("#307.070 Found different states for task #{}, db: {}, graph: {}, assigned: false, state in queue: null, trying to update a state of task in execContext",
                                tv.taskId, TaskExecState.from(taskState.execState), tv.state);
                        eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                    }
                    else if (taskState.execState== TaskExecState.NONE.value &&  tv.state== TaskExecState.CHECK_CACHE && allocatedTask.state== TaskExecState.NONE) {
                        // #307.080 Found different states for task, db: NONE, graph: CHECK_CACHE, assigned: false, state in queue: null
                        // ---> This is a normal situation, will occur after restarting a dispatcher
                        log.warn("#307.080 Found different states for task #{}, db: {}, graph: {}, assigned: false, state in queue: {}, trying to update a state of task in execContextGraph",
                                tv.taskId, TaskExecState.from(taskState.execState), tv.state, allocatedTask.state);
                        eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                    }
                    else if (taskState.execState== TaskExecState.ERROR_WITH_RECOVERY.value &&  tv.state== TaskExecState.NONE && allocatedTask.state== TaskExecState.NONE) {
                        // #307.085 Found different states for task #31786, db: ERROR_WITH_RECOVERY, graph: NONE, assigned: false, state in queue: NONE, required steps are unknown
                        // ---> This is a normal situation, will occur after restarting a dispatcher
                        log.warn("#307.085 Found different states for task #{}, db: {}, graph: {}, assigned: false, state in queue: {}, trying to update a state of task in execContextGraph",
                                tv.taskId, TaskExecState.from(taskState.execState), tv.state, allocatedTask.state);
                        TaskProviderTopLevelService.deregisterTask(execContextId, tv.taskId);
                        eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                    }
                    else if (taskState.execState== TaskExecState.ERROR_WITH_RECOVERY.value &&  tv.state==TaskExecState.CHECK_CACHE && allocatedTask.state== TaskExecState.NONE) {
                        // ---> This is a normal situation, will occur after restarting a dispatcher
                        log.warn("#307.087 Found different states for task #{}, db: {}, graph: {}, assigned: false, state in queue: {}, trying to update a state of task in execContextGraph",
                                tv.taskId, TaskExecState.from(taskState.execState), tv.state, allocatedTask.state);
                        TaskProviderTopLevelService.deregisterTask(execContextId, tv.taskId);
                        eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                    }
                    else {
                        log.warn("#307.100 Found different states for task #{}, db: {}, graph: {}, assigned: false, state in queue: {}, required steps are unknown",
                                tv.taskId, TaskExecState.from(taskState.execState), tv.state, allocatedTask.state);
                    }
                }
                else if (EnumsApi.TaskExecState.isFinishedState(taskState.execState) && taskState.execState==allocatedTask.state.value) {
                    // ---> This is a normal situation
                    // statuses of tasks are copying from taskQueue when all tasks in a group will be finished. so there is a period of time when the state is as this
                }
                else if (taskState.execState== TaskExecState.IN_PROGRESS.value &&  tv.state== TaskExecState.CHECK_CACHE && allocatedTask.state== TaskExecState.IN_PROGRESS) {
                    // Found different states for task , db: IN_PROGRESS, graph: CHECK_CACHE, state in queue: IN_PROGRESS
                    // ---> This is a normal situation
                }
                else if (taskState.execState== TaskExecState.IN_PROGRESS.value &&  tv.state== TaskExecState.NONE && allocatedTask.state== TaskExecState.IN_PROGRESS) {
                    // #307.120 Found different states for task , db: IN_PROGRESS, graph: NONE, state in queue: IN_PROGRESS
                    // ---> This is a normal situation
                }
                else if (taskState.execState== TaskExecState.OK.value &&  tv.state== TaskExecState.NONE && allocatedTask.state== TaskExecState.NONE) {
                    // #307.130 Found different states for task #222176, db: OK, graph: NONE, state in queue: NONE, required steps are unknown
                    log.warn("#307.130 Found different states for task #{}, db: OK, graph: NONE, allocatedTask: NONE, trying to update a state of task in execContext", tv.taskId);
                    eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                }
                else if (taskState.execState== TaskExecState.OK.value &&  tv.state== TaskExecState.NONE && allocatedTask.state== TaskExecState.IN_PROGRESS) {
                    // #307.135 Found different states for task #222154, db: OK, graph: NONE, state in queue: IN_PROGRESS, required steps are unknown
                    log.warn("#307.135 Found different states for task #{}, db: OK, graph: NONE, allocatedTask: IN_PROGRESS, trying to update a state of task in execContext", tv.taskId);
                    eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                }
                else if (taskState.execState== TaskExecState.CHECK_CACHE.value &&  tv.state== TaskExecState.NONE && allocatedTask.state== TaskExecState.CHECK_CACHE) {
                    log.warn("#307.137 Found different states for task #{}, db: CHECK_CACHE, graph: NONE, allocatedTask: CHECK_CACHE, trying to update a state of task in execContext", tv.taskId);
                    eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                }
                else if (taskState.execState== TaskExecState.CHECK_CACHE.value &&  tv.state== TaskExecState.IN_PROGRESS && allocatedTask.state== TaskExecState.CHECK_CACHE) {
                    // #307.135 Found different states for task #222154, db: OK, graph: NONE, state in queue: IN_PROGRESS, required steps are unknown
                    log.warn("#307.141 Found different states for task #{}, db: CHECK_CACHE, graph: IN_PROGRESS, allocatedTask: CHECK_CACHE, trying to update a state of task in execContext", tv.taskId);
                    eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                }
                else if (taskState.execState== TaskExecState.ERROR_WITH_RECOVERY.value &&  tv.state== TaskExecState.NONE && allocatedTask.state== TaskExecState.ERROR_WITH_RECOVERY) {
                    log.warn("#307.145 Found different states for task #{}, db: ERROR_WITH_RECOVERY, graph: NONE, allocatedTask: ERROR_WITH_RECOVERY, trying to update a state of task in execContext", tv.taskId);
                    TaskProviderTopLevelService.deregisterTask(execContextId, tv.taskId);
                    eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                }
                else if (taskState.execState== TaskExecState.ERROR_WITH_RECOVERY.value &&  tv.state== TaskExecState.CHECK_CACHE && allocatedTask.state== TaskExecState.ERROR_WITH_RECOVERY) {
                    log.warn("#307.150 Found different states for task #{}, db: ERROR_WITH_RECOVERY, graph: CHECK_CACHE, allocatedTask: ERROR_WITH_RECOVERY, trying to update a state of task in execContext", tv.taskId);
                    TaskProviderTopLevelService.deregisterTask(execContextId, tv.taskId);
                    eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContextId, tv.taskId));
                }
                else if (taskState.execState== TaskExecState.CHECK_CACHE.value &&  tv.state== TaskExecState.ERROR_WITH_RECOVERY && allocatedTask.state== TaskExecState.CHECK_CACHE) {
//                    log.warn("#307.155 Found different states for task #{}, db: CHECK_CACHE, graph: ERROR_WITH_RECOVERY, allocatedTask: CHECK_CACHE, trying to update a state of task in execContext", tv.taskId);
//                    eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphEvent(execContext.id, tv.taskId));
                }
                else {
                    // #307.140 Found different states for task #66935, db: OK, graph: NONE, state in queue: IN_PROGRESS, required steps are unknown
                    log.error("#307.270 Found different states for task #{}, db: {}, graph: {}, state in queue: {}, required steps are unknown",
                            tv.taskId, TaskExecState.from(taskState.execState), tv.state, allocatedTask.state);
                }
            }
        }

        if (status.isNullState.get()) {
            log.info("#307.300 Found non-created task, graph consistency is failed");
            return status;
        }

        // fix actual state of tasks (can be as a result of OptimisticLockingException)
        // fix IN_PROCESSING state
        // find and reset all hanging up tasks
        states.entrySet().stream()
                .filter(e-> TaskExecState.IN_PROGRESS.value==e.getValue().execState)
                .forEach(e->{
                    Long taskId = e.getKey();
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
                    if (task != null) {
                        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

                        if (task.resultReceived!=0 && task.completed!=0) {
                            status.taskIsOkIds.add(task.id);
                        }
                        else {
                            // did this task hang up at processor?
                            if (task.assignedOn != null) {
                                if (task.accessByProcessorOn == null) {
                                    // processor must start to download variables in 2 minutes
                                    // only if there is any input variable and context of task is FunctionExecContext.external
                                    // TODO 2021-05-17 check that internal function is setting task.accessByProcessorOn at all
                                    if (tpy.task.context== FunctionExecContext.external && !tpy.task.inputs.isEmpty() && (System.currentTimeMillis() - task.assignedOn) > 120_000) {
                                        log.info("#307.360 Reset task #{} at processor #{}, The reason - hasn't started to download variables in 2 minutes",
                                                task.id, task.coreId);
                                        status.taskForResettingIds.add(task.id);
                                    }
                                }
                                else {
                                    if (tpy.task.timeoutBeforeTerminate != null && tpy.task.timeoutBeforeTerminate != 0L) {
                                        // +4 is for waiting network communications at the last moment. i.e. wait for 4 seconds more
                                        // +180 second for finish uploading of results
                                        // TODO 2021-04-22 need to rewrite with better formula of decision
                                        final long timeoutForChecking = (tpy.task.timeoutBeforeTerminate + 184 ) * 1000;
                                        final long oneHourToMills = TimeUnit.HOURS.toMillis(1);
                                        long effectiveTimeout = Math.min(timeoutForChecking, oneHourToMills);
                                        if ((System.currentTimeMillis() - task.accessByProcessorOn) > effectiveTimeout) {
                                            log.info("#307.400 Reset task #{} at processor #{}, timeoutBeforeTerminate: {}, timeoutForChecking: {}, effective timeout: {}",
                                                    task.id, task.coreId, tpy.task.timeoutBeforeTerminate, timeoutForChecking, effectiveTimeout);
                                            status.taskForResettingIds.add(task.id);
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
        return status;
    }

}
