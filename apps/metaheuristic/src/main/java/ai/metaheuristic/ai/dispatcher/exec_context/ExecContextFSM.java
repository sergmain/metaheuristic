/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskWithInternalContextService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskHelperService;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 1/18/2020
 * Time: 3:34 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextFSM {

    private final ExecContextCache execContextCache;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskExecStateService taskExecStateService;
    private final TaskRepository taskRepository;
    private final TaskHelperService taskHelperService;
    private final TaskTransactionalService taskTransactionalService;
    private final DispatcherEventService dispatcherEventService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProcessorCache processorCache;

    @Transactional
    public void toStarted(ExecContext execContext) {
        execContextSyncService.checkWriteLockPresent(execContext.getId());
        toStarted(execContext.getId());
    }

    @Transactional
    public void toStarted(Long execContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        toState(execContextId, EnumsApi.ExecContextState.STARTED);
    }

    @Transactional
    public void toStopped(Long execContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        toState(execContextId, EnumsApi.ExecContextState.STOPPED);
    }

    @Transactional
    public void toProduced(Long execContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        toState(execContextId, EnumsApi.ExecContextState.PRODUCED);
    }

    @Transactional
    public void toFinished(Long execContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        toStateWithCompletion(execContextId, EnumsApi.ExecContextState.FINISHED);
    }

    @Transactional
    public void toError(Long execContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        toStateWithCompletion(execContextId, EnumsApi.ExecContextState.ERROR);
    }

    private void toState(Long execContextId, EnumsApi.ExecContextState state) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        if (execContext.state !=state.code) {
            execContext.setState(state.code);
            execContextCache.save(execContext);
        }
    }

    @Transactional
    public OperationStatusRest changeExecContextState(String state, Long execContextId, Long companyUniqueId) {
        EnumsApi.ExecContextState execState = EnumsApi.ExecContextState.from(state.toUpperCase());
        if (execState== EnumsApi.ExecContextState.UNKNOWN) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#303.020 Unknown exec state, state: " + state);
        }
        return changeExecContextState(execState, execContextId, companyUniqueId);
    }

    @Transactional
    public OperationStatusRest changeExecContextStateWithTx(EnumsApi.ExecContextState execState, Long execContextId, Long companyUniqueId) {
        return changeExecContextState(execState, execContextId, companyUniqueId);
    }

    public OperationStatusRest changeExecContextState(EnumsApi.ExecContextState execState, Long execContextId, Long companyUniqueId) {
        execContextSyncService.checkWriteLockPresent(execContextId);

        OperationStatusRest status = checkExecContext(execContextId);
        if (status != null) {
            return status;
        }
        status = execContextTargetState(execContextId, execState, companyUniqueId);
        return status;
    }

    private OperationStatusRest execContextTargetState(Long execContextId, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#303.040 execContext wasn't found, execContextId: " + execContextId);
        }

        if (execContext.state !=execState.code) {
            toState(execContext.id, execState);
            eventPublisher.publishEvent(new DispatcherInternalEvent.SourceCodeLockingEvent(execContext.getSourceCodeId(), companyUniqueId, true));
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Nullable
    private OperationStatusRest checkExecContext(Long execContextId) {
        ExecContext wb = execContextCache.findById(execContextId);
        if (wb==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#303.060 ExecContext wasn't found, execContextId: " + execContextId );
        }
        return null;
    }

    @Transactional
    public OperationStatusRest addTasksToGraph(@Nullable ExecContextImpl execContext, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> taskIds) {
        if (execContext==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        execContextSyncService.checkWriteLockPresent(execContext.id);
        OperationStatusRest osr = execContextGraphService.addNewTasksToGraph(execContext, parentTaskIds, taskIds);
        return osr;
    }

    private void toStateWithCompletion(Long execContextId, EnumsApi.ExecContextState state) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        if (execContext.state != state.code) {
            execContext.setCompletedOn(System.currentTimeMillis());
            execContext.setState(state.code);
            execContextCache.save(execContext);
        } else if (execContext.state!= EnumsApi.ExecContextState.FINISHED.code && execContext.completedOn != null) {
            log.error("#303.080 Integrity failed, current state: {}, new state: {}, but execContext.completedOn!=null",
                    execContext.state, state.code);
        }
    }

    @Transactional
    public Void storeExecResultWithTx(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        return storeExecResult(result);
    }

    public Void storeExecResult(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        TaskImpl task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("#303.100 Reporting about non-existed task #{}", result.taskId);
            return null;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(result.getResult());
        if (functionExec==null) {
            String es = "#303.120 Task #" + result.taskId + " has empty execResult";
            log.info(es);
            functionExec = new FunctionApiData.FunctionExec();
        }
        FunctionApiData.SystemExecResult systemExecResult = functionExec.generalExec!=null ? functionExec.generalExec : functionExec.exec;
        if (!systemExecResult.isOk) {
            log.warn("#303.140 Task #{} finished with error, functionCode: {}, console: {}",
                    result.taskId,
                    systemExecResult.functionCode,
                    StringUtils.isNotBlank(systemExecResult.console) ? systemExecResult.console : "<console output is empty>");
        }
        EnumsApi.TaskExecState state = functionExec.allFunctionsAreOk() ? EnumsApi.TaskExecState.OK : EnumsApi.TaskExecState.ERROR;
        Task t = prepareAndSaveTask(task, result, state);

        dispatcherEventService.publishTaskEvent(
                state==EnumsApi.TaskExecState.OK ? EnumsApi.DispatcherEventType.TASK_FINISHED : EnumsApi.DispatcherEventType.TASK_ERROR,
                null, result.taskId, t.getExecContextId());

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(t.getParams());
        if (state==EnumsApi.TaskExecState.ERROR) {
            log.info("#303.160 store result with the state ERROR");
            finishWithErrorInternal(
                    t.getId(),
                    StringUtils.isNotBlank(systemExecResult.console) ? systemExecResult.console : "<console output is empty>",
                    t.getExecContextId(), tpy.task.taskContextId);
        }
        else {
            log.info("#303.180 store result with the state {}", EnumsApi.ExecContextState.toState(t.getExecState()));
            updateTaskExecStates( execContextCache.findById(t.getExecContextId()), t.getId(), t.getExecState(), tpy.task.taskContextId);
        }
        return null;
    }

    @Transactional
    public OperationStatusRest resetTask(Long execContextId, Long taskId) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        Task t = taskExecStateService.resetTask(taskId);
        if (t == null) {
            String es = S.f("#303.200 Found a non-existed task, graph consistency for execContextId #%s is failed",
                    execContextId);
            log.error(es);
            toError(execContextId);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        updateTaskExecStates(execContextCache.findById(execContextId), taskId, EnumsApi.TaskExecState.NONE.value, null);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public void markAsFinishedWithError(Long taskId, Long sourceCodeId, Long execContextId, TaskParamsYaml taskParamsYaml, InternalFunctionData.InternalFunctionProcessingResult result) {
        TxUtils.checkTxExists();

        log.error("#303.220 error type: {}, message: {}\n\tsourceCodeId: {}, execContextId: {}", result.processing, result.error, sourceCodeId, execContextId);
        final String console = "#303.240 Task #" + taskId + " was finished with status '" + result.processing + "', text of error: " + result.error;

        final String taskContextId = taskParamsYaml.task.taskContextId;

        finishWithErrorInternal(taskId, console, execContextId, taskContextId);
    }

    @Transactional
    public void finishWithError(Long taskId, Long execContextId, @Nullable String taskContextId, @Nullable String params) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        finishWithErrorInternal(taskId, "#303.260 Task was finished with an unknown error, can't process it", execContextId, taskContextId);
    }

    @Transactional
    public Void finishWithError(Long taskId, String console, Long execContextId, @Nullable String taskContextId) {
        return finishWithErrorInternal(taskId, console, execContextId, taskContextId);
    }

    public Void finishWithErrorInternal(Long taskId, String console, Long execContextId, @Nullable String taskContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        taskExecStateService.finishTaskAsError(taskId, EnumsApi.TaskExecState.ERROR, -10001, console);

        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(
                execContextCache.findById(execContextId), taskId, EnumsApi.TaskExecState.ERROR.value, taskContextId);
        taskExecStateService.updateTasksStateInDb(status);
        return null;
    }

    @Transactional
    public Void updateExecContextStatus(Long execContextId, boolean needReconciliation) {
        execContextSyncService.checkWriteLockPresent(execContextId);

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return null;
        }
        long countUnfinishedTasks = execContextGraphTopLevelService.getCountUnfinishedTasks(execContext);
        if (countUnfinishedTasks==0) {
            // workaround for situation when states in graph and db are different
            reconcileStatesInternal(execContextId);
            execContext = execContextCache.findById(execContextId);
            if (execContext==null) {
                return null;
            }
            countUnfinishedTasks = execContextGraphTopLevelService.getCountUnfinishedTasks(execContext);
            if (countUnfinishedTasks==0) {
                log.info("ExecContext #{} was finished", execContextId);
                toFinished(execContextId);
            }
        }
        else {
            if (needReconciliation) {
                reconcileStatesInternal(execContextId);
            }
        }
        return null;
    }

    @Transactional
    public Void reconcileStatesWithTx(Long execContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        return reconcileStatesInternal(execContextId);
    }

    private Void reconcileStatesInternal(Long execContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return null;
        }

        // Reconcile states in db and in graph
        List<ExecContextData.TaskVertex> rootVertices = execContextGraphService.findAllRootVertices(execContext);
        if (rootVertices.size()>1) {
            log.error("#303.280 Too many root vertices, count: " + rootVertices.size());
        }

        if (rootVertices.isEmpty()) {
            return null;
        }
        Set<ExecContextData.TaskVertex> vertices = execContextGraphService.findDescendants(execContext, rootVertices.get(0).taskId);

        final Map<Long, TaskData.TaskState> states = getExecStateOfTasks(execContextId);

        Map<Long, TaskData.TaskState> taskStates = new HashMap<>();
        AtomicBoolean isNullState = new AtomicBoolean(false);

        for (ExecContextData.TaskVertex tv : vertices) {

            TaskData.TaskState taskState = states.get(tv.taskId);
            if (taskState==null) {
                isNullState.set(true);
            }
/*
            else if (System.currentTimeMillis()-taskState.updatedOn>5_000 && tv.execState.value!=taskState.execState) {
                log.info("#303.300 Found different states for task #"+tv.taskId+", " +
                        "db: "+ EnumsApi.TaskExecState.from(taskState.execState)+", " +
                        "graph: "+tv.execState);

                if (taskState.execState== EnumsApi.TaskExecState.ERROR.value) {
                    finishWithError(tv.taskId, execContext.id, null, null);
                }
                else {
                    updateTaskExecStates(execContext, tv.taskId, taskState.execState, null);
                }
                break;
            }
*/
        }

        if (isNullState.get()) {
            log.info("#303.320 Found non-created task, graph consistency is failed");
            toError(execContextId);
            return null;
        }

        final Map<Long, TaskData.TaskState> newStates = getExecStateOfTasks(execContextId);

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
                                log.info("#303.340 Reset task #{}, multiplyBy2: {}, timeout: {}", task.id, multiplyBy2, timeout);
                                resetTask(task.execContextId, task.id);
                            }
                        }
                        else if (task.resultReceived && task.isCompleted) {
                            updateTaskExecStates(execContextCache.findById(execContextId), task.id, EnumsApi.TaskExecState.OK.value, tpy.task.taskContextId);
                        }
                    }
                });
        return null;
    }

    @NonNull
    private Map<Long, TaskData.TaskState> getExecStateOfTasks(Long execContextId) {
        List<Object[]> list = taskRepository.findAllExecStateByExecContextId(execContextId);

        Map<Long, TaskData.TaskState> states = new HashMap<>(list.size()+1);
        for (Object[] o : list) {
            TaskData.TaskState taskState = new TaskData.TaskState(o);
            states.put(taskState.taskId, taskState);
        }
        return states;
    }

    public OperationStatusRest updateTaskExecStates(@Nullable ExecContextImpl execContext, Long taskId, int execState, @Nullable String taskContextId) {
        TxUtils.checkTxExists();
        if (execContext==null) {
            // this execContext was deleted
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        execContextSyncService.checkWriteLockPresent(execContext.id);

        taskExecStateService.changeTaskState(taskId, EnumsApi.TaskExecState.from(execState));
        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(execContext, taskId, execState, taskContextId);
        taskExecStateService.updateTasksStateInDb(status);
        return status.status;
    }

    @Transactional
    public Void processResendTaskOutputResourceResult(@Nullable String processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#303.360 Task obsolete and was already deleted");
            return null;
        }

        execContextSyncService.checkWriteLockPresent(task.execContextId);
        switch (status) {
            case SEND_SCHEDULED:
                log.info("#303.380 Processor #{} scheduled sending of output variables of task #{} for sending. This is normal operation of Processor", processorId, task.id);
                break;
            case VARIABLE_NOT_FOUND:
            case TASK_IS_BROKEN:
            case TASK_PARAM_FILE_NOT_FOUND:
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                finishWithError(task.id, task.execContextId, tpy.task.taskContextId, task.params);
                break;
            case OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE:
                Enums.UploadResourceStatus uploadResourceStatus = taskTransactionalService.setResultReceived(task, variableId);
                if (uploadResourceStatus == Enums.UploadResourceStatus.OK) {
                    log.info("#303.400 the output resource of task #{} is stored on external storage which was defined by disk://. This is normal operation of sourceCode", task.id);
                } else {
                    log.info("#303.420 can't update isCompleted field for task #{}", task.id);
                }
                break;
        }
        return null;
    }

    private final Map<Long, AtomicLong> bannedSince = new HashMap<>();

    public List<TaskImpl> getAllByProcessorIdIsNullAndExecContextIdAndIdIn(Long execContextId, List<ExecContextData.TaskVertex> vertices, int page) {
        final List<Long> idsForSearch = ExecContextService.getIdsForSearch(vertices, page, 20);
        if (idsForSearch.isEmpty()) {
            return List.of();
        }
        return taskRepository.findForAssigning(execContextId, idsForSearch);
    }

    @Nullable
    private TaskImpl findUnassignedTaskAndAssign(Long execContextId, Processor processor, ProcessorStatusYaml psy, boolean isAcceptOnlySigned) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContextId);

        AtomicLong longHolder = bannedSince.computeIfAbsent(processor.getId(), o -> new AtomicLong(0));
        if (longHolder.get()!=0 && System.currentTimeMillis() - longHolder.get() < TimeUnit.MINUTES.toMillis(30)) {
            return null;
        }
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return null;
        }
        final List<ExecContextData.TaskVertex> vertices = execContextGraphTopLevelService.findAllForAssigning(execContext);
        if (vertices.isEmpty()) {
            return null;
        }

        int page = 0;
        TaskImpl resultTask = null;
        List<TaskImpl> tasks;
        while ((tasks = getAllByProcessorIdIsNullAndExecContextIdAndIdIn(execContextId, vertices, page++)).size()>0) {
            for (TaskImpl task : tasks) {
                final TaskParamsYaml taskParamYaml;
                try {
                    taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                }
                catch (YAMLException e) {
                    log.error("#303.440 Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.toString(),task.getParams());
                    finishWithError(task.getId(), task.execContextId, null, null);
                    continue;
                }
                if (task.execState==EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    // may be happened because of multi-threaded processing of internal function
                    continue;
                }
                if (task.execState!=EnumsApi.TaskExecState.NONE.value) {
                    log.warn("#303.460 Task #{} with function '{}' was already processed with status {}",
                            task.getId(), taskParamYaml.task.function.code, EnumsApi.TaskExecState.from(task.execState));
                    continue;
                }
                // all tasks with internal function will be processed by scheduler
                if (taskParamYaml.task.context== EnumsApi.FunctionExecContext.internal) {
                    continue;
                }

                if (gitUnavailable(taskParamYaml.task, psy.gitStatusInfo.status!= Enums.GitStatus.installed) ) {
                    log.warn("#303.480 Can't assign task #{} to processor #{} because this processor doesn't correctly installed git, git status info: {}",
                            processor.getId(), task.getId(), psy.gitStatusInfo
                    );
                    longHolder.set(System.currentTimeMillis());
                    continue;
                }

                if (!S.b(taskParamYaml.task.function.env)) {
                    String interpreter = psy.env.getEnvs().get(taskParamYaml.task.function.env);
                    if (interpreter == null) {
                        log.warn("#303.500 Can't assign task #{} to processor #{} because this processor doesn't have defined interpreter for function's env {}",
                                task.getId(), processor.getId(), taskParamYaml.task.function.env
                        );
                        longHolder.set(System.currentTimeMillis());
                        continue;
                    }
                }

                final List<EnumsApi.OS> supportedOS = FunctionCoreUtils.getSupportedOS(taskParamYaml.task.function.metas);
                if (psy.os!=null && !supportedOS.isEmpty() && !supportedOS.contains(psy.os)) {
                    log.info("#303.520 Can't assign task #{} to processor #{}, " +
                                    "because this processor doesn't support required OS version. processor: {}, function: {}",
                            processor.getId(), task.getId(), psy.os, supportedOS
                    );
                    longHolder.set(System.currentTimeMillis());
                    continue;
                }

                if (isAcceptOnlySigned) {
                    if (taskParamYaml.task.function.checksumMap==null || taskParamYaml.task.function.checksumMap.keySet().stream().noneMatch(o->o.isSigned)) {
                        log.warn("#303.540 Function with code {} wasn't signed", taskParamYaml.task.function.getCode());
                        continue;
                    }
                }
                resultTask = task;
                // check that downgrading is supported
                try {
                    TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                    String params = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, psy.taskParamsVersion);
                } catch (DowngradeNotSupportedException e) {
                    log.warn("#303.560 Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                            resultTask.getId(), processor.id, psy.taskParamsVersion);
                    longHolder.set(System.currentTimeMillis());
                    resultTask = null;
                }
                if (resultTask!=null) {
                    break;
                }
            }
            if (resultTask!=null) {
                break;
            }
        }
        if (resultTask==null) {
            return null;
        }

        // normal way of operation
        longHolder.set(0);

        resultTask.setAssignedOn(System.currentTimeMillis());
        resultTask.setProcessorId(processor.getId());
        resultTask.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        resultTask.setResultResourceScheduledOn(0);
        taskTransactionalService.save(resultTask);

        try {
            updateTaskExecStates(execContextCache.findById(execContextId), resultTask.getId(), EnumsApi.TaskExecState.IN_PROGRESS.value, null);
        } catch (ObjectOptimisticLockingFailureException e) {
            e.printStackTrace();
        }
        dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ASSIGNED, processor.getId(), resultTask.getId(), execContextId);

        return resultTask;
    }

    @Nullable
    public TaskImpl prepareVariables(TaskImpl task) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        TaskParamsYaml taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

        final Long execContextId = task.execContextId;
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            log.warn("#303.580 can't assign a new task in execContext with Id #"+ execContextId +". This execContext doesn't exist");
            return null;
        }
        ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
        ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParams.task.processCode);
        if (p==null) {
            log.warn("#303.600 can't find process '"+taskParams.task.processCode+"' in execContext with Id #"+ execContextId);
            return null;
        }

        // we dont need to create inputs because all inputs are outputs of previous processes,
        // except globals and startInputAs
        // but we need to initialize descriptor of input variable
        p.inputs.stream()
                .map(v -> taskHelperService.toInputVariable(v, taskParams.task.taskContextId, execContextId))
                .collect(Collectors.toCollection(()->taskParams.task.inputs));

        return taskTransactionalService.persistOutputVariables(task, taskParams, execContext, p);
    }


    private static boolean gitUnavailable(TaskParamsYaml.TaskYaml task, boolean gitNotInstalled) {
        if (task.function.sourcing == EnumsApi.FunctionSourcing.git && gitNotInstalled) {
            return true;
        }
        for (TaskParamsYaml.FunctionConfig preFunction : task.preFunctions) {
            if (preFunction.sourcing == EnumsApi.FunctionSourcing.git && gitNotInstalled) {
                return true;
            }
        }
        for (TaskParamsYaml.FunctionConfig postFunction : task.postFunctions) {
            if (postFunction.sourcing == EnumsApi.FunctionSourcing.git && gitNotInstalled) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public EnumsApi.TaskProducingStatus toProducing(Long execContextId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return EnumsApi.TaskProducingStatus.EXEC_CONTEXT_NOT_FOUND_ERROR;
        }
        if (execContext.state == EnumsApi.ExecContextState.PRODUCING.code) {
            return EnumsApi.TaskProducingStatus.OK;
        }
        execContext.setState(EnumsApi.ExecContextState.PRODUCING.code);
        execContextCache.save(execContext);
        return EnumsApi.TaskProducingStatus.OK;
    }

    private Task prepareAndSaveTask(TaskImpl task, ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result, EnumsApi.TaskExecState state) {
        task.setExecState(state.value);
        if (state==EnumsApi.TaskExecState.ERROR) {
            task.setCompleted(true);
            task.setCompletedOn(System.currentTimeMillis());
            task.setResultReceived(true);
        }
        else {
            task.setResultResourceScheduledOn(System.currentTimeMillis());
            TaskParamsYaml yaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            // if there isn't any output variable which has to be uploaded to dispatcher then complete this task
            if (yaml.task.outputs.stream().noneMatch(o->o.sourcing== EnumsApi.DataSourcing.dispatcher)) {
                task.setCompleted(true);
                task.setCompletedOn(System.currentTimeMillis());
            }
        }
        task.setFunctionExecResults(result.getResult());
        task = taskTransactionalService.save(task);

        return task;
    }

    @Nullable
    @Transactional
    public DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor(ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, boolean isAcceptOnlySigned, Long execContextId) {
        execContextSyncService.checkWriteLockPresent(execContextId);

        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            log.error("#303.620 Processor with id #{} wasn't found", processorId);
            return null;
        }
        ProcessorStatusYaml psy = toProcessorStatusYaml(processor);
        if (psy==null) {
            return null;
        }

        final TaskImpl t = getTaskAndAssignToProcessorInternal(reportProcessorTaskStatus, processor, psy, isAcceptOnlySigned, execContextId);
        // task won't be returned for an internal function
        if (t==null) {
            return null;
        }
        try {
            TaskImpl task = prepareVariables(t);
            if (task == null) {
                log.warn("#303.640 After prepareVariables(task) the task is null");
                return null;
            }

            String params;
            try {
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                if (tpy.version == psy.taskParamsVersion) {
                    params = task.params;
                } else {
                    params = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, psy.taskParamsVersion);
                }
            } catch (DowngradeNotSupportedException e) {
                // TODO 2020-09-267 there is a possible situation when a check in ExecContextFSM.findUnassignedTaskAndAssign() would be ok
                //  but this one fails. that could occur because of prepareVariables(task);
                //  need a better solution for checking
                log.warn("#303.660 Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                        task.getId(), processor.id, psy.taskParamsVersion);
                return null;
            }

            return new DispatcherCommParamsYaml.AssignedTask(params, task.getId(), task.getExecContextId());
        } catch (Throwable th) {
            String es = "#303.680 Something wrong";
            log.error(es, th);
            throw new IllegalStateException(es, th);
        }
    }

    @Nullable
    private TaskImpl getTaskAndAssignToProcessorInternal(
            ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Processor processor, ProcessorStatusYaml psy, boolean isAcceptOnlySigned, Long execContextId) {

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            log.warn("#303.700 ExecContext wasn't found for id: {}", execContextId);
            return null;
        }
        if (execContext.getState()!= EnumsApi.ExecContextState.STARTED.code) {
            log.warn("#303.720 ExecContext wasn't started. Current exec state: {}", EnumsApi.ExecContextState.toState(execContext.getState()));
            return null;
        }

        List<Long> activeTaskIds = taskRepository.findActiveForProcessorId(processor.id);
        boolean allAssigned = false;
        if (reportProcessorTaskStatus.statuses!=null) {
            List<ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus> lostTasks = reportProcessorTaskStatus.statuses.stream()
                    .filter(o->!activeTaskIds.contains(o.taskId)).collect(Collectors.toList());
            if (lostTasks.isEmpty()) {
                allAssigned = true;
            }
            else {
                log.info("#303.740 Found the lost tasks at processor #{}, tasks #{}", processor.id, lostTasks);
                for (ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus lostTask : lostTasks) {
                    TaskImpl task = taskRepository.findById(lostTask.taskId).orElse(null);
                    if (task==null) {
                        continue;
                    }
                    if (task.execState!= EnumsApi.TaskExecState.IN_PROGRESS.value) {
                        log.warn("#303.760 !!! Need to investigate, processor: #{}, task #{}, execStatus: {}, but isCompleted==false",
                                processor.id, task.id, EnumsApi.TaskExecState.from(task.execState));
                        // TODO 2020-09-26 because this situation shouldn't happened what exactly to do isn't known right now
                    }
                    return task;
                }
            }
        }
        if (allAssigned) {
            // this processor already has active task
            log.warn("#303.780 !!! Need to investigate, shouldn't happened, processor #{}, tasks: {}", processor.id, activeTaskIds);
            return null;
        }

        TaskImpl result = findUnassignedTaskAndAssign(execContextId, processor, psy, isAcceptOnlySigned);

        return result;
    }

    @Nullable
    private ProcessorStatusYaml toProcessorStatusYaml(Processor processor) {
        ProcessorStatusYaml ss;
        try {
            ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
            return ss;
        } catch (Throwable e) {
            log.error("#303.800 Error parsing current status of processor:\n{}", processor.status);
            log.error("#303.820 Error ", e);
            return null;
        }
    }

}
