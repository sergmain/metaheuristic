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
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskHelperService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderService;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 1/18/2020
 * Time: 3:34 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextFSM {

    private final ExecContextCache execContextCache;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskExecStateService taskExecStateService;
    private final TaskRepository taskRepository;
    private final TaskHelperService taskHelperService;
    private final TaskTransactionalService taskTransactionalService;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskProviderService taskProviderService;
    private final ExecContextTaskFinishingService execContextTaskFinishingService;
    private final ExecContextVariableService execContextVariableService;
    private final ExecContextTaskStateService execContextTaskStateService;
    private final ExecContextService execContextService;
    private final ExecContextReconciliationService execContextReconciliationService;

    public void toFinished(ExecContextImpl execContext) {
        execContextSyncService.checkWriteLockPresent(execContext.id);
        toStateWithCompletion(execContext, EnumsApi.ExecContextState.FINISHED);
    }

    public void toError(ExecContextImpl execContext) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContext.id);
        toStateWithCompletion(execContext, EnumsApi.ExecContextState.ERROR);
    }

    public void toState(Long execContextId, EnumsApi.ExecContextState state) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContextId);
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        if (execContext.state !=state.code) {
            execContext.setState(state.code);
            execContextService.save(execContext);
        }
    }

    @Transactional
    public OperationStatusRest changeExecContextStateWithTx(EnumsApi.ExecContextState execState, Long execContextId, Long companyUniqueId) {
        return changeExecContextState(execState, execContextId, companyUniqueId);
    }

    private OperationStatusRest changeExecContextState(EnumsApi.ExecContextState execState, Long execContextId, Long companyUniqueId) {
        execContextSyncService.checkWriteLockPresent(execContextId);

        OperationStatusRest status = checkExecContext(execContextId);
        if (status != null) {
            return status;
        }
        status = execContextTargetState(execContextId, execState, companyUniqueId);
        return status;
    }

    public OperationStatusRest execContextTargetState(ExecContextImpl execContext, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        execContext.setState(execState.code);
        eventPublisher.publishEvent(new DispatcherInternalEvent.SourceCodeLockingEvent(execContext.getSourceCodeId(), companyUniqueId, true));
        return OperationStatusRest.OPERATION_STATUS_OK;
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

    public OperationStatusRest addTasksToGraph(@Nullable ExecContextImpl execContext, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> taskIds) {
        TxUtils.checkTxExists();

        if (execContext==null) {
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        execContextSyncService.checkWriteLockPresent(execContext.id);
        OperationStatusRest osr = execContextGraphService.addNewTasksToGraph(execContext, parentTaskIds, taskIds);
        return osr;
    }

    private void toStateWithCompletion(ExecContextImpl execContext, EnumsApi.ExecContextState state) {
        if (execContext.state != state.code) {
            execContext.setCompletedOn(System.currentTimeMillis());
            execContext.setState(state.code);
            execContextService.save(execContext);
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
        TxUtils.checkTxExists();
        TaskImpl task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("#303.100 Reporting about non-existed task #{}", result.taskId);
            return null;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        task.setFunctionExecResults(result.getResult());
        task.setResultReceived(true);
//        task = taskService.save(task);

        execContextTaskFinishingService.checkTaskCanBeFinished(task);
        return null;
    }

    @Transactional
    public OperationStatusRest resetTaskWithTx(Long execContextId, Long taskId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "execContext wasn't found");
        }
        return resetTask(execContext, taskId);
    }

    public OperationStatusRest resetTask(ExecContextImpl execContext, Long taskId) {
        execContextSyncService.checkWriteLockPresent(execContext.id);
        TaskImpl t = taskExecStateService.resetTask(taskId);
        if (t == null) {
            String es = S.f("#303.200 Found a non-existed task, graph consistency for execContextId #%s is failed",
                    execContext.id);
            log.error(es);
            toError(execContext);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        execContextTaskStateService.updateTaskExecStates(execContext, t, EnumsApi.TaskExecState.NONE, null);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public Void updateExecContextStatus(Long execContextId, boolean needReconciliation) {
        execContextSyncService.checkWriteLockPresent(execContextId);

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return null;
        }
        if (needReconciliation) {
            ExecContextReconciliationService.ReconciliationStatus status = execContextReconciliationService.reconcileStates(execContext);
            execContextSyncService.getWithSync(execContext.id,
                    () -> execContextReconciliationService.finishReconciliation(status));
        }
        else {
/*
            long countUnfinishedTasks = execContextGraphTopLevelService.getCountUnfinishedTasks(execContext);
            if (countUnfinishedTasks == 0) {
                // workaround for situation when states in graph and db are different
                ExecContextReconciliationService.ReconciliationStatus status = execContextReconciliationService.reconcileStates(execContext);
                execContextSyncService.getWithSync(execContext.id,
                        () -> execContextReconciliationService.finishReconciliation(status));
*/

/*
                execContext = execContextCache.findById(execContextId);
                if (execContext == null) {
                    return null;
                }
                countUnfinishedTasks = execContextGraphTopLevelService.getCountUnfinishedTasks(execContext);
                if (countUnfinishedTasks==0) {
                    log.info("ExecContext #{} was finished", execContextId);
                    toFinished(execContext);
                }
*/
        }
        return null;
    }

    @Transactional
    public Void processResendTaskOutputVariable(@Nullable String processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
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
                execContextTaskFinishingService.finishWithError(task, tpy.task.taskContextId);
                break;
            case OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE:
                Enums.UploadVariableStatus statusResult = execContextVariableService.setVariableReceived(task, variableId);
                if (statusResult == Enums.UploadVariableStatus.OK) {
                    log.info("#303.400 the output resource of task #{} is stored on external storage which was defined by disk://. This is normal operation of sourceCode", task.id);
                } else {
                    log.info("#303.420 can't update isCompleted field for task #{}", task.id);
                }
                break;
        }
        return null;
    }

    public List<Long> getAllByProcessorIdIsNullAndExecContextIdAndIdIn(Long execContextId, List<ExecContextData.TaskVertex> vertices, int page) {
        final List<Long> idsForSearch = ExecContextService.getIdsForSearch(vertices, page, 20);
        if (idsForSearch.isEmpty()) {
            return List.of();
        }
        return taskRepository.findForAssigning(execContextId, idsForSearch);
    }

    @Nullable
    private TaskImpl prepareVariables(TaskImpl task) {
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

        return taskTransactionalService.initOutputVariables(execContext, task, p, taskParams);
    }

    @Nullable
    public DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor(
            ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, ProcessorStatusYaml psy, boolean isAcceptOnlySigned, Long execContextId) {
        TxUtils.checkTxNotExists();

        final TaskImpl t = getTaskAndAssignToProcessorInternal(reportProcessorTaskStatus, processorId, psy, isAcceptOnlySigned, execContextId);
        // task won't be returned for an internal function
        if (t==null) {
            return null;
        }
        try {
            TaskImpl task = prepareVariables(t);
            if (task == null) {
                log.warn("#303.640 The task is null after prepareVariables(task)");
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
                        task.getId(), processorId, psy.taskParamsVersion);
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
            ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, ProcessorStatusYaml psy, boolean isAcceptOnlySigned, Long execContextId) {
        TxUtils.checkTxNotExists();

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            log.warn("#303.700 ExecContext wasn't found for id: {}", execContextId);
            return null;
        }
        if (execContext.getState()!= EnumsApi.ExecContextState.STARTED.code) {
            log.warn("#303.720 ExecContext wasn't started. Current exec state: {}", EnumsApi.ExecContextState.toState(execContext.getState()));
            return null;
        }

        List<Long> activeTaskIds = taskRepository.findActiveForProcessorId(processorId);
        boolean allAssigned = false;
        if (reportProcessorTaskStatus.statuses!=null) {
            List<ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus> lostTasks = reportProcessorTaskStatus.statuses.stream()
                    .filter(o->!activeTaskIds.contains(o.taskId)).collect(Collectors.toList());
            if (lostTasks.isEmpty()) {
                allAssigned = true;
            }
            else {
                log.info("#303.740 Found the lost tasks at processor #{}, tasks #{}", processorId, lostTasks);
                for (ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus lostTask : lostTasks) {
                    TaskImpl task = taskRepository.findById(lostTask.taskId).orElse(null);
                    if (task==null) {
                        continue;
                    }
                    if (task.execState!= EnumsApi.TaskExecState.IN_PROGRESS.value) {
                        log.warn("#303.760 !!! Need to investigate, processor: #{}, task #{}, execStatus: {}, but isCompleted==false",
                                processorId, task.id, EnumsApi.TaskExecState.from(task.execState));
                        // TODO 2020-09-26 because this situation shouldn't be happened what exactly to do isn't known right now
                    }
                    return task;
                }
            }
        }
        if (allAssigned) {
            log.warn("#303.780 ! This processor already has active task. Need to investigate, shouldn't happened, processor #{}, tasks: {}", processorId, activeTaskIds);
            return null;
        }

        TaskImpl result = taskProviderService.findUnassignedTaskAndAssign(execContext.id, processorId, psy, isAcceptOnlySigned);

        return result;
    }

}
