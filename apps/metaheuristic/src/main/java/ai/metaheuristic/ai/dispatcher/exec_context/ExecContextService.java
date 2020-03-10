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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.Monitoring;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.IdsRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("UnusedReturnValue")
public class ExecContextService {

    private final @NonNull Globals globals;
    private final @NonNull ExecContextRepository execContextRepository;
    private final @NonNull SourceCodeCache sourceCodeCache;

    private final @NonNull VariableService variableService;
    private final @NonNull GlobalVariableService globalVariableService;
    private final @NonNull TaskRepository taskRepository;
    private final @NonNull TaskPersistencer taskPersistencer;
    private final @NonNull ProcessorCache processorCache;
    private final @NonNull ExecContextCache execContextCache;
    private final @NonNull ExecContextGraphService execContextGraphService;
    private final @NonNull ExecContextProcessGraphService execContextProcessGraphService;
    private final @NonNull ExecContextSyncService execContextSyncService;
    private final @NonNull DispatcherEventService dispatcherEventService;
    private final @NonNull ExecContextFSM execContextFSM;
    private final @NonNull TaskProducingService taskProducingService;
    private final @NonNull ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final @NonNull ApplicationEventPublisher applicationEventPublisher;
    private final @NonNull IdsRepository idsRepository;

    public OperationStatusRest resetBrokenTasks(@NonNull Long execContextId) {
        final ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#705.003 Can't find execContext with id #"+execContextId);
        }
        List<ExecContextData.TaskVertex> vertices = execContextGraphTopLevelService.findAllBroken(execContext);
        if (vertices==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#705.005 Can't find execContext with id #"+execContextId);
        }
        for (ExecContextData.TaskVertex vertex : vertices) {
            resetTask(vertex.taskId);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest execContextTargetState(@NonNull Long execContextId, @NonNull EnumsApi.ExecContextState execState) {
        SourceCodeApiData.ExecContextResult result = getExecContextExtended(execContextId);
        if (result.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.getErrorMessagesAsList());
        }

        final ExecContextImpl execContext = (ExecContextImpl) result.execContext;
        final SourceCode sourceCode = result.sourceCode;
        if (sourceCode ==null || execContext ==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#701.110 Error: (result.sourceCode==null || result.execContext==null)");
        }

        if (execContext.state !=execState.code) {
            execContextFSM.toState(execContext.id, execState);
            applicationEventPublisher.publishEvent(new DispatcherInternalEvent.SourceCodeLockingEvent(sourceCode.getId(), sourceCode.getCompanyId(), true));
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest resetTask(@NonNull Long taskId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#705.010 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }

        // TODO 2019-11-03 need to investigate why without this call nothing is working
        Task t = taskPersistencer.resetTask(task.id);
        if (t==null) {
            ExecContextOperationStatusWithTaskList withTaskList = execContextGraphTopLevelService.updateGraphWithSettingAllChildrenTasksAsBroken(task.getExecContextId(), task.id);
            taskPersistencer.updateTasksStateInDb(withTaskList);
            if (withTaskList.status.status== EnumsApi.OperationStatus.ERROR) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.030 Can't re-run task #" + taskId + ", see log for more information");
            }
        }
        else {
            ExecContextOperationStatusWithTaskList withTaskList = execContextGraphTopLevelService.updateGraphWithResettingAllChildrenTasks(task.execContextId, task.id);
            if (withTaskList == null) {
                taskPersistencer.finishTaskAsBrokenOrError(taskId, EnumsApi.TaskExecState.BROKEN);
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#705.020 Can't re-run task "+taskId+", this task is orphan and doesn't belong to any execContext");
            }

            if (withTaskList.status.status== EnumsApi.OperationStatus.ERROR) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.040 Can't re-run task #" + taskId + ", see log for more information");
            }
            taskPersistencer.updateTasksStateInDb(withTaskList);

            ExecContextImpl execContext = execContextCache.findById(task.execContextId);
            if (execContext!=null && execContext.state != EnumsApi.ExecContextState.STARTED.code) {
                execContextFSM.toState(execContext.id, EnumsApi.ExecContextState.STARTED);
            }
        }

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public long getCountUnfinishedTasks(ExecContextImpl execContext) {
        return getCountUnfinishedTasks(execContext.id);
    }

    public Long getCountUnfinishedTasks(Long execContextId) {
        return execContextSyncService.getWithSync(execContextId, execContextGraphService::getCountUnfinishedTasks);
    }

    public List<ExecContextData.TaskVertex> findAllVertices(Long execContextId) {
        return execContextSyncService.getWithSync(execContextId, execContextGraphService::findAll);
    }

    public void changeValidStatus(Long execContextId, boolean status) {
        execContextSyncService.getWithSyncNullable(execContextId, execContext -> {
            execContext.setValid(status);
            execContextCache.save(execContext);
            return null;
        });
    }

    public EnumsApi.TaskProducingStatus toProducing(Long execContextId) {
        return execContextSyncService.getWithSync(execContextId, execContext -> {
            if (execContext.state == EnumsApi.ExecContextState.PRODUCING.code) {
                return EnumsApi.TaskProducingStatus.OK;
            }
            execContext.setState(EnumsApi.ExecContextState.PRODUCING.code);
            execContextCache.save(execContext);
            return EnumsApi.TaskProducingStatus.OK;
        });
    }

    public @NonNull SourceCodeApiData.ExecContextResult getExecContextExtended(Long execContextId) {
        if (execContextId==null) {
            return new SourceCodeApiData.ExecContextResult("#705.090 execContextId is null");
        }
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new SourceCodeApiData.ExecContextResult("#705.100 execContext wasn't found, execContextId: " + execContextId);
        }
        SourceCodeImpl sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
        if (sourceCode == null) {
            return new SourceCodeApiData.ExecContextResult("#705.110 sourceCode wasn't found, sourceCodeId: " + execContext.getSourceCodeId());
        }

        if (!sourceCode.getId().equals(execContext.getSourceCodeId())) {
            changeValidStatus(execContextId, false);
            return new SourceCodeApiData.ExecContextResult("#705.120 sourceCodeId doesn't match to execContext.sourceCodeId, sourceCodeId: " + execContext.getSourceCodeId()+", execContext.sourceCodeId: " + execContext.getSourceCodeId());
        }

        //noinspection UnnecessaryLocalVariable
        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(sourceCode, execContext);
        return result;
    }

    public SourceCodeApiData.ExecContextsResult getExecContextsOrderByCreatedOnDescResult(Long sourceCodeId, Pageable pageable, DispatcherContext context) {
        pageable = ControllerUtils.fixPageSize(globals.execContextRowsLimit, pageable);
        SourceCodeApiData.ExecContextsResult result = new SourceCodeApiData.ExecContextsResult();
        result.instances = execContextRepository.findBySourceCodeIdOrderByCreatedOnDesc(pageable, sourceCodeId);
        result.currentSourceCodeId = sourceCodeId;

        for (ExecContext execContext : result.instances) {
            ExecContextParamsYaml wpy = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.getParams());
            wpy.graph = ConstsApi.EMPTY_GRAPH;
            execContext.setParams( ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(wpy) );
            SourceCode sourceCode = sourceCodeCache.findById(execContext.getSourceCodeId());
            if (sourceCode ==null) {
                log.warn("#705.130 Found execContext with wrong sourceCodeId. sourceCodeId: {}", execContext.getSourceCodeId());
                continue;
            }
            result.sourceCodes.put(execContext.getId(), sourceCode);
        }
        return result;
    }

    public @Nullable DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor(@NonNull Long processorId, boolean isAcceptOnlySigned, @Nullable Long execContextId) {
        ExecContextData.AssignedTaskComplex assignedTaskComplex = getTaskAndAssignToProcessorInternal(processorId, isAcceptOnlySigned, execContextId);
        if (assignedTaskComplex==null) {
            return null;
        }

        //noinspection UnnecessaryLocalVariable
        DispatcherCommParamsYaml.AssignedTask result = execContextSyncService.getWithSync(assignedTaskComplex.execContextId,
                execContext -> {
                    prepareVariables(assignedTaskComplex);
                    return new DispatcherCommParamsYaml.AssignedTask(assignedTaskComplex.params, assignedTaskComplex.execContextId, assignedTaskComplex.task.getId());
        });
        return result;
    }

    private void prepareVariables(ExecContextData.AssignedTaskComplex assignedTaskComplex) {

        TaskParamsYaml taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.to(assignedTaskComplex.task.getParams());

        ExecContextImpl execContext = execContextCache.findById(assignedTaskComplex.execContextId);
        if (execContext==null) {
            log.warn("#705.135 can't assign a new task in execContext with Id #"+assignedTaskComplex.execContextId+". This execContext doesn't exist");
            return;
        }
        ExecContextParamsYaml execContextParamsYaml = execContext.getExecContextParamsYaml();
        ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParams.task.processCode);
        if (p==null) {
            log.warn("#705.136 can't find process '"+taskParams.task.processCode+"' in execContext with Id #"+assignedTaskComplex.execContextId);
            return;
        }
        if (p.function.context== EnumsApi.FunctionExecContext.internal) {
            // resources for internal Function will be prepared by Function it self.
            return;
        }

        // we dont need to create inputs because all inputs are outputs of previous process,
        // except globals and startInputAs
        for (TaskParamsYaml.OutputVariable variable : taskParams.task.outputs) {
            if (variable.isInited) {
                continue;
            }
            Variable v = variableService.createUninitialized(variable.name, assignedTaskComplex.execContextId, p.internalContextId);

            TaskParamsYaml.Resource resource = new TaskParamsYaml.Resource(EnumsApi.VariableContext.local, v.id.toString(), null);
            taskParams.task.outputs.add(
                    new TaskParamsYaml.OutputVariable(
                            variable.name, EnumsApi.VariableContext.local, variable.sourcing, variable.git, variable.disk,
                            resource, false
                    ));
        }
    }

    @Nullable
    private ExecContextData.AssignedTaskComplex getTaskAndAssignToProcessorInternal(@NonNull Long processorId, boolean isAcceptOnlySigned, @Nullable Long specificExecContextId) {

        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            log.error("#705.140 Processor wasn't found for id: {}", processorId);
            return null;
        }
        List<Long> anyTaskId = taskRepository.findAnyActiveForProcessorId(Consts.PAGE_REQUEST_1_REC, processorId);
        if (!anyTaskId.isEmpty()) {
            // this processor already has active task
            log.info("#705.160 can't assign any new task to the processor #{} because this processor has an active task #{}", processorId, anyTaskId);
            return null;
        }

        List<Long> execContextIds;
        // find task in specific execContext ( i.e. specificExecContextId!=null)?
        if (specificExecContextId != null) {
            ExecContextImpl execContext = execContextCache.findById(specificExecContextId);
            if (execContext==null) {
                log.warn("#705.170 ExecContext wasn't found for id: {}", specificExecContextId);
                return null;
            }
            if (execContext.getState()!= EnumsApi.ExecContextState.STARTED.code) {
                log.warn("#705.180 ExecContext wasn't started. Current exec state: {}", EnumsApi.ExecContextState.toState(execContext.getState()));
                return null;
            }
            execContextIds = List.of(execContext.id);
        }
        else {
            execContextIds = execContextRepository.findByStateOrderByCreatedOnAsc(EnumsApi.ExecContextState.STARTED.code);
        }

        ProcessorStatusYaml ss;
        try {
            ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
        } catch (Throwable e) {
            log.error("#705.150 Error parsing current status of processor:\n{}", processor.status);
            log.error("#705.151 Error ", e);
            return null;
        }

        for (Long execContextId : execContextIds) {
            ExecContextData.AssignedTaskComplex result = execContextSyncService.getWithSyncNullable(execContextId,
                    execContext -> findUnassignedTaskAndAssign(execContextId, processor, ss, isAcceptOnlySigned));
            if (result!=null) {
                return result;
            }
        }
        return null;
    }

    private final @NonNull Map<Long, AtomicLong> bannedSince = new HashMap<>();

    public static List<Long> getIdsForSearch(List<ExecContextData.TaskVertex> vertices, int page, int pageSize) {
        final int fromIndex = page * pageSize;
        if (vertices.size()== fromIndex) {
            return List.of();
        }
        int toIndex = fromIndex + (vertices.size()-pageSize>=fromIndex ? pageSize : vertices.size() - fromIndex);
        return vertices.subList(fromIndex, toIndex).stream()
                .map(v -> v.taskId)
                .collect(Collectors.toList());
    }

    private @Nullable ExecContextData.AssignedTaskComplex findUnassignedTaskAndAssign(Long execContextId, Processor processor, ProcessorStatusYaml ss, boolean isAcceptOnlySigned) {

        AtomicLong longHolder = bannedSince.computeIfAbsent(processor.getId(), o -> new AtomicLong(0));
        if (longHolder.get()!=0 && System.currentTimeMillis() - longHolder.get() < TimeUnit.MINUTES.toMillis(30)) {
            return null;
        }
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return null;
        }
        final List<ExecContextData.TaskVertex> vertices = execContextGraphTopLevelService.findAllForAssigning(execContext);
        final ProcessorStatusYaml processorStatus = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);

        int page = 0;
        Task resultTask = null;
        String resultTaskParams = null;
        List<Task> tasks;
        while ((tasks = getAllByProcessorIdIsNullAndExecContextIdAndIdIn(execContextId, vertices, page++)).size()>0) {
            for (Task task : tasks) {
                final TaskParamsYaml taskParamYaml;
                try {
                    taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                }
                catch (YAMLException e) {
                    log.error("#705.190 Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.toString(),task.getParams());
                    taskPersistencer.finishTaskAsBrokenOrError(task.getId(), EnumsApi.TaskExecState.BROKEN);
                    continue;
                }
/*
                catch (Exception e) {
                    throw new RuntimeException("#705.200 Error", e);
                }
*/

                if (gitUnavailable(taskParamYaml.task, processorStatus.gitStatusInfo.status!= Enums.GitStatus.installed) ) {
                    log.warn("#705.210 Can't assign task #{} to processor #{} because this processor doesn't correctly installed git, git status info: {}",
                            processor.getId(), task.getId(), processorStatus.gitStatusInfo
                    );
                    longHolder.set(System.currentTimeMillis());
                    continue;
                }

                if (!S.b(taskParamYaml.task.function.env)) {
                    String interpreter = processorStatus.env.getEnvs().get(taskParamYaml.task.function.env);
                    if (interpreter == null) {
                        log.warn("#705.213 Can't assign task #{} to processor #{} because this processor doesn't have defined interpreter for function's env {}",
                                processor.getId(), task.getId(), taskParamYaml.task.function.env
                        );
                        longHolder.set(System.currentTimeMillis());
                        continue;
                    }
                }

                final List<EnumsApi.OS> supportedOS = FunctionCoreUtils.getSupportedOS(taskParamYaml.task.function.metas);
                if (processorStatus.os!=null && !supportedOS.isEmpty() && !supportedOS.contains(processorStatus.os)) {
                    log.info("#705.217 Can't assign task #{} to processor #{}, " +
                                    "because this processor doesn't support required OS version. processor: {}, function: {}",
                            processor.getId(), task.getId(), processorStatus.os, supportedOS
                    );
                    longHolder.set(System.currentTimeMillis());
                    continue;
                }

                if (isAcceptOnlySigned) {
                    if (!taskParamYaml.task.function.info.isSigned()) {
                        log.warn("#705.220 Function with code {} wasn't signed", taskParamYaml.task.function.getCode());
                        continue;
                    }
                }
                resultTask = task;
                try {
                    TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                    resultTaskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, ss.taskParamsVersion);
                } catch (DowngradeNotSupportedException e) {
                    log.warn("Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                            resultTask.getId(), processor.id, ss.taskParamsVersion);
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

        ExecContextData.AssignedTaskComplex assignedTaskComplex = new ExecContextData.AssignedTaskComplex();
        assignedTaskComplex.setTask(resultTask);
        assignedTaskComplex.setExecContextId(execContextId);
        assignedTaskComplex.setParams(resultTaskParams);

        resultTask.setAssignedOn(System.currentTimeMillis());
        resultTask.setProcessorId(processor.getId());
        resultTask.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        resultTask.setResultResourceScheduledOn(0);

        taskRepository.save((TaskImpl)resultTask);
        execContextGraphTopLevelService.updateTaskExecStateByExecContextId(execContextId, resultTask.getId(), EnumsApi.TaskExecState.IN_PROGRESS.value);
        dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ASSIGNED, processor.getId(), resultTask.getId(), execContextId);

        return assignedTaskComplex;
    }

    private static boolean gitUnavailable(TaskParamsYaml.TaskYaml task, boolean gitNotInstalled) {
        if (task.function.sourcing == EnumsApi.FunctionSourcing.git && gitNotInstalled) {
            return true;
        }
        if (task.preFunctions != null) {
            for (TaskParamsYaml.FunctionConfig preFunction : task.preFunctions) {
                if (preFunction.sourcing == EnumsApi.FunctionSourcing.git && gitNotInstalled) {
                    return true;
                }
            }
        }
        if (task.postFunctions != null) {
            for (TaskParamsYaml.FunctionConfig postFunction : task.postFunctions) {
                if (postFunction.sourcing == EnumsApi.FunctionSourcing.git && gitNotInstalled) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Task> getAllByProcessorIdIsNullAndExecContextIdAndIdIn(Long execContextId, List<ExecContextData.TaskVertex> vertices, int page) {
        final List<Long> idsForSearch = getIdsForSearch(vertices, page, 20);
        if (idsForSearch.isEmpty()) {
            return List.of();
        }
        return taskRepository.findForAssigning(execContextId, idsForSearch);
    }

    public List<Long> storeAllConsoleResults(List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results) {

        List<Long> ids = new ArrayList<>();
        for (ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            taskPersistencer.storeExecResult(result, t -> {
                if (t!=null) {
                    execContextGraphTopLevelService.updateTaskExecStateByExecContextId(t.getExecContextId(), t.getId(), t.getExecState());
                }
            });
        }
        return ids;
    }

    public SourceCodeApiData.TaskProducingResultComplex produceTasks(boolean isPersist, @NonNull ExecContextImpl execContext) {

        Monitoring.log("##023", Enums.Monitor.MEMORY);
        ExecContextParamsYaml execContextParamsYaml = execContext.getExecContextParamsYaml();

        // create all not dynamic tasks
        TaskData.ProduceTaskResult produceTaskResult = taskProducingService.produceTasks(isPersist, execContext.sourceCodeId, execContext.id, execContextParamsYaml);

        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();
        if (isPersist) {
            execContextFSM.toProduced(execContext.id);
        }
        result.numberOfTasks = produceTaskResult.numberOfTasks;
        result.sourceCodeValidateStatus = EnumsApi.SourceCodeValidateStatus.OK;
        result.taskProducingStatus = EnumsApi.TaskProducingStatus.OK;

        return result;
    }

    public void deleteExecContext(Long execContextId, Long companyUniqueId) {
//        experimentService.resetExperimentByExecContextId(execContextId);
        applicationEventPublisher.publishEvent(new DispatcherInternalEvent.DeleteExecContextEvent(execContextId));
        variableService.deleteByExecContextId(execContextId);
        ExecContext execContext = execContextCache.findById(execContextId);
        if (execContext != null) {
            // unlock sourceCode if this is the last execContext in the sourceCode
            List<Long> ids = execContextRepository.findIdsBySourceCodeId(execContext.getSourceCodeId());
            if (ids.size()==1) {
                if (ids.get(0).equals(execContextId)) {
                    if (execContext.getSourceCodeId() != null) {
//                        setLockedTo(execContext.getSourceCodeId(), companyUniqueId, false);
                        applicationEventPublisher.publishEvent(new DispatcherInternalEvent.SourceCodeLockingEvent(execContext.getSourceCodeId(), companyUniqueId, false));
                    }
                }
                else {
                    log.warn("#701.300 unexpected state, execContextId: {}, ids: {}, ", execContextId, ids);
                }
            }
            else if (ids.isEmpty()) {
                log.warn("#701.310 unexpected state, execContextId: {}, ids is empty", execContextId);
            }
            execContextCache.deleteById(execContextId);
        }
    }



}
