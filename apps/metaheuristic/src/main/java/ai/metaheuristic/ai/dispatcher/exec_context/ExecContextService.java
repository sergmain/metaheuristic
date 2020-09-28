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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskHelperService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.EnumsApi.*;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("UnusedReturnValue")
public class ExecContextService {

    private final Globals globals;
    private final ExecContextRepository execContextRepository;

    private final VariableService variableService;
    private final TaskRepository taskRepository;
    private final ProcessorCache processorCache;
    private final ExecContextCache execContextCache;
    private final ExecContextSyncService execContextSyncService;
    public final ExecContextFSM execContextFSM;
    private final TaskProducingService taskProducingService;
    private final TaskHelperService taskProducingCoreService;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskTransactionalService taskTransactionalService;
    private final TaskSyncService taskSyncService;

    public OperationStatusRest execContextTargetState(Long execContextId, ExecContextState execState, Long companyUniqueId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new OperationStatusRest(OperationStatus.ERROR, "#705.180 execContext wasn't found, execContextId: " + execContextId);
        }

        if (execContext.state !=execState.code) {
            execContextFSM.toState(execContext.id, execState);
            eventPublisher.publishEvent(new DispatcherInternalEvent.SourceCodeLockingEvent(execContext.getSourceCodeId(), companyUniqueId, true));
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public void changeValidStatus(Long execContextId, boolean status) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        execContext.setValid(status);
        execContextCache.save(execContext);
    }

    public ExecContextApiData.ExecContextsResult getExecContextsOrderByCreatedOnDescResult(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        pageable = ControllerUtils.fixPageSize(globals.execContextRowsLimit, pageable);
        ExecContextApiData.ExecContextsResult result = new ExecContextApiData.ExecContextsResult();
        result.instances = execContextRepository.findBySourceCodeIdOrderByCreatedOnDesc(pageable, sourceCodeId);
        return result;
    }

    public @Nullable DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor(ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Long processorId, boolean isAcceptOnlySigned, @Nullable Long execContextId) {

        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            log.error("#705.320 Processor with id #{} wasn't found", processorId);
            return null;
        }
        ProcessorStatusYaml psy = toProcessorStatusYaml(processor);
        if (psy==null) {
            return null;
        }


        TaskImpl task = getTaskAndAssignToProcessorInternal(reportProcessorTaskStatus, processor, psy, isAcceptOnlySigned, execContextId);
        // task won't be returned for an internal function
        if (task==null) {
            return null;
        }

        try {
            task = taskSyncService.getWithSync(task.id, this::prepareVariables);

            if (task==null) {
                log.warn("After prepareVariables(task) the task is null");
                return null;
            }

            String params;
            try {
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                if (tpy.version==psy.taskParamsVersion) {
                    params = task.params;
                }
                else {
                    params = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, psy.taskParamsVersion);
                }
            } catch (DowngradeNotSupportedException e) {
                // TODO 2020-09-267 there is a possible situation when a check in ExecContextFSM.findUnassignedTaskAndAssign() would be ok
                //  but this one fails. that could occur because of prepareVariables(task);
                //  need a better solution for checking
                log.warn("#705.540 Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                        task.getId(), processor.id, psy.taskParamsVersion);
                return null;
            }

            return new DispatcherCommParamsYaml.AssignedTask(params, task.getId(), task.getExecContextId());
        } catch (Throwable th) {
            String es = "#705.270 Something wrong";
            log.error(es, th);
            throw new IllegalStateException(es, th);
        }
    }

    @Nullable
    private TaskImpl prepareVariables(TaskImpl task) {

        // we will use assignedTaskComplex.task.getParams(), not assignedTaskComplex.params,
        // because we need actual TaskParamsYaml for a correct initialization
        TaskParamsYaml taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

        final Long execContextId = task.execContextId;
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            log.warn("#705.280 can't assign a new task in execContext with Id #"+ execContextId +". This execContext doesn't exist");
            return null;
        }
        ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
        ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParams.task.processCode);
        if (p==null) {
            log.warn("#705.300 can't find process '"+taskParams.task.processCode+"' in execContext with Id #"+ execContextId);
            return null;
        }

        // we dont need to create inputs because all inputs are outputs of previous processes,
        // except globals and startInputAs
        // but we need to initialize descriptor of input variable
        p.inputs.stream()
                .map(v -> taskProducingCoreService.toInputVariable(v, taskParams.task.taskContextId, execContextId))
                .collect(Collectors.toCollection(()->taskParams.task.inputs));

        return taskTransactionalService.persistOutputVariables(task, taskParams, execContext, p);
    }

    @Nullable
    private TaskImpl getTaskAndAssignToProcessorInternal(
            ProcessorCommParamsYaml.ReportProcessorTaskStatus reportProcessorTaskStatus, Processor processor, ProcessorStatusYaml psy, boolean isAcceptOnlySigned, @Nullable Long specificExecContextId) {

        List<Long> activeTaskIds = taskRepository.findActiveForProcessorId(processor.id);
        boolean allAssigned = false;
        if (reportProcessorTaskStatus.statuses!=null) {
            List<ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus> lostTasks = reportProcessorTaskStatus.statuses.stream()
                    .filter(o->!activeTaskIds.contains(o.taskId)).collect(Collectors.toList());
            if (lostTasks.isEmpty()) {
                allAssigned = true;
            }
            else {
                log.info("#705.330 Found the lost tasks at processor #{}, tasks #{}", processor.id, lostTasks);
                for (ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus lostTask : lostTasks) {
                    TaskImpl task = taskRepository.findById(lostTask.taskId).orElse(null);
                    if (task==null) {
                        continue;
                    }
                    if (task.execState!= TaskExecState.IN_PROGRESS.value) {
                        log.warn("#705.333 !!! Need to investigate, processor: #{}, task #{}, execStatus: {}, but isCompleted==false",
                                processor.id, task.id, TaskExecState.from(task.execState));
                        // TODO 2020-09-26 because this situation shouldn't happened what exactly to do isn't known right now
                    }
                    return task;
                }
            }
        }
        if (allAssigned) {
            // this processor already has active task
            log.warn("#705.340 !!! Need to investigate, shouldn't happened, processor #{}, tasks: {}", processor.id, activeTaskIds);
            return null;
        }

        List<Long> execContextIds;
        // find task in specific execContext ( i.e. specificExecContextId!=null)?
        if (specificExecContextId != null) {
            ExecContextImpl execContext = execContextCache.findById(specificExecContextId);
            if (execContext==null) {
                log.warn("#705.360 ExecContext wasn't found for id: {}", specificExecContextId);
                return null;
            }
            if (execContext.getState()!= ExecContextState.STARTED.code) {
                log.warn("#705.380 ExecContext wasn't started. Current exec state: {}", ExecContextState.toState(execContext.getState()));
                return null;
            }
            execContextIds = List.of(execContext.id);
        }
        else {
            execContextIds = execContextRepository.findByStateOrderByCreatedOnAsc(ExecContextState.STARTED.code);
        }

        for (Long execContextId : execContextIds) {
            TaskImpl result = execContextSyncService.getWithSyncNullable(execContextId,
                    () -> execContextFSM.findUnassignedTaskAndAssign(execContextId, processor, psy, isAcceptOnlySigned));
            if (result!=null) {
                return result;
            }
        }
        return null;
    }

    @Nullable
    private ProcessorStatusYaml toProcessorStatusYaml(Processor processor) {
        ProcessorStatusYaml ss;
        try {
            ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
            return ss;
        } catch (Throwable e) {
            log.error("#705.400 Error parsing current status of processor:\n{}", processor.status);
            log.error("#705.410 Error ", e);
            return null;
        }
    }

    public List<Long> storeAllConsoleResults(List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results) {

        List<Long> ids = new ArrayList<>();
        for (ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            execContextFSM.storeExecResult(result);
        }
        return ids;
    }

    public void deleteExecContext(Long execContextId, Long companyUniqueId) {
        eventPublisher.publishEvent(new DispatcherInternalEvent.DeleteExperimentByExecContextIdEvent(execContextId));
        variableService.deleteByExecContextId(execContextId);
        ExecContext execContext = execContextCache.findById(execContextId);
        if (execContext != null) {
            // unlock sourceCode if this is the last execContext in the sourceCode
            List<Long> ids = execContextRepository.findIdsBySourceCodeId(execContext.getSourceCodeId());
            if (ids.size()==1) {
                if (ids.get(0).equals(execContextId)) {
                    if (execContext.getSourceCodeId() != null) {
                        eventPublisher.publishEvent(new DispatcherInternalEvent.SourceCodeLockingEvent(execContext.getSourceCodeId(), companyUniqueId, false));
                    }
                }
                else {
                    log.warn("#705.600 unexpected state, execContextId: {}, ids: {}, ", execContextId, ids);
                }
            }
            else if (ids.isEmpty()) {
                log.warn("#705.320 unexpected state, execContextId: {}, ids is empty", execContextId);
            }
            execContextCache.deleteById(execContextId);
        }
    }

    public OperationStatusRest changeExecContextState(String state, Long execContextId, DispatcherContext context) {
        ExecContextState execState = ExecContextState.from(state.toUpperCase());
        if (execState== ExecContextState.UNKNOWN) {
            return new OperationStatusRest(OperationStatus.ERROR, "#560.390 Unknown exec state, state: " + state);
        }
        OperationStatusRest status = checkExecContext(execContextId, context);
        if (status != null) {
            return status;
        }
        status = execContextTargetState(execContextId, execState, context.getCompanyId());
        return status;
    }

    public OperationStatusRest deleteExecContextById(Long execContextId, DispatcherContext context) {
        OperationStatusRest status = checkExecContext(execContextId, context);
        if (status != null) {
            return status;
        }
        eventPublisher.publishEvent( new DispatcherInternalEvent.ExecContextDeletionEvent(this, execContextId) );
        deleteExecContext(execContextId, context.getCompanyId());

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Nullable
    private OperationStatusRest checkExecContext(Long execContextId, DispatcherContext context) {
        ExecContext wb = execContextCache.findById(execContextId);
        if (wb==null) {
            return new OperationStatusRest(OperationStatus.ERROR, "#560.400 ExecContext wasn't found, execContextId: " + execContextId );
        }
        return null;
    }


}
