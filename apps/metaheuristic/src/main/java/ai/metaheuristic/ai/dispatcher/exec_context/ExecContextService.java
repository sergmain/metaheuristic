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
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeValidationService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.BreakFromForEachException;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherInternalEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.IdsRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeUtils;
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphFactory;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.utils.holders.LongHolder;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("UnusedReturnValue")
public class ExecContextService {

    private final Globals globals;
    private final ExecContextRepository execContextRepository;
    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeValidationService sourceCodeValidationService;
    private final SourceCodeRepository sourceCodeRepository;

    private final VariableService variableService;
    private final GlobalVariableService globalVariableService;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final ProcessorCache processorCache;
    private final ExecContextCache execContextCache;
    private final ExecContextGraphService execContextGraphService;
    private final ExecContextSyncService execContextSyncService;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextFSM execContextFSM;
    private final TaskProducingService taskProducingService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final IdsRepository idsRepository;

    public OperationStatusRest resetBrokenTasks(Long execContextId) {
        final ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#705.003 Can't find execContext with id #"+execContextId);
        }
        List<ExecContextParamsYaml.TaskVertex> vertices = execContextGraphTopLevelService.findAllBroken(execContext);
        if (vertices==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#705.005 Can't find execContext with id #"+execContextId);
        }
        for (ExecContextParamsYaml.TaskVertex vertex : vertices) {
            resetTask(vertex.taskId);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest execContextTargetState(Long execContextId, EnumsApi.ExecContextState execState) {
        SourceCodeApiData.ExecContextResult result = getExecContextExtended(execContextId);
        if (result.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages);
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

    public OperationStatusRest resetTask(Long taskId) {
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
            if (execContext.state != EnumsApi.ExecContextState.STARTED.code) {
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

    public List<ExecContextParamsYaml.TaskVertex> findAllVertices(Long execContextId) {
        return execContextSyncService.getWithSync(execContextId, execContextGraphService::findAll);
    }

    public SourceCodeApiData.TaskProducingResultComplex createExecContext(Long sourceCodeId, ExecContextParamsYaml.ExecContextYaml execContextYaml) {
        return createExecContext(sourceCodeId, execContextYaml, true);
    }

    public SourceCodeApiData.TaskProducingResultComplex createExecContext(Long sourceCodeId, ExecContextParamsYaml.ExecContextYaml execContextYaml, boolean checkResources) {
        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();

        ExecContextImpl ec = new ExecContextImpl();
        ec.setSourceCodeId(sourceCodeId);
        ec.setCreatedOn(System.currentTimeMillis());
        ec.setState(EnumsApi.ExecContextState.NONE.code);
        ec.setCompletedOn(null);
        ExecContextParamsYaml params = new ExecContextParamsYaml();
        params.execContextYaml = execContextYaml;
        params.graph = Consts.EMPTY_GRAPH;
        ec.updateParams(params);
        ec.setValid(true);

        if (checkResources) {
            ExecContextParamsYaml resourceParam = ec.getExecContextParamsYaml();
            List<SimpleVariableAndStorageUrl> inputResourceCodes = variableService.getIdInVariables(resourceParam.getAllVariables());
            if (inputResourceCodes == null || inputResourceCodes.isEmpty()) {
                result.sourceCodeProducingStatus = EnumsApi.SourceCodeProducingStatus.INPUT_POOL_CODE_DOESNT_EXIST_ERROR;
                return result;
            }
        }

        result.execContext = execContextCache.save(ec);
        result.sourceCodeProducingStatus = EnumsApi.SourceCodeProducingStatus.OK;

        return result;
    }

    public Void changeValidStatus(Long execContextId, boolean status) {
        return execContextSyncService.getWithSync(execContextId, execContext -> {
            execContext.setValid(status);
            execContextCache.save(execContext);
            return null;
        });
    }

    public EnumsApi.SourceCodeProducingStatus toProducing(Long execContextId) {
        return execContextSyncService.getWithSync(execContextId, execContext -> {
            if (execContext.state == EnumsApi.ExecContextState.PRODUCING.code) {
                return EnumsApi.SourceCodeProducingStatus.OK;
            }
            execContext.setState(EnumsApi.ExecContextState.PRODUCING.code);
            execContextCache.save(execContext);
            return EnumsApi.SourceCodeProducingStatus.OK;
        });
    }

    public SourceCodeApiData.ExecContextResult getExecContextExtended(Long execContextId) {
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
            wpy.graph = null;
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

    // TODO 2019.08.27 is it good to synchronize the whole method?
    //  but it's working actually
    public synchronized DispatcherCommParamsYaml.AssignedTask getTaskAndAssignToProcessor(long processorId, boolean isAcceptOnlySigned, Long execContextId) {

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
        // find task in specific execContext ( i.e. execContextId==null)?
        if (execContextId==null) {
            execContextIds = execContextRepository.findByStateOrderByCreatedOnAsc(EnumsApi.ExecContextState.STARTED.code);
        }
        else {
            ExecContextImpl execContext = execContextCache.findById(execContextId);
            if (execContext==null) {
                log.warn("#705.170 ExecContext wasn't found for id: {}", execContextId);
                return null;
            }
            if (execContext.getState()!= EnumsApi.ExecContextState.STARTED.code) {
                log.warn("#705.180 ExecContext wasn't started. Current exec state: {}", EnumsApi.ExecContextState.toState(execContext.getState()));
                return null;
            }
            execContextIds = List.of(execContext.id);
        }

        ProcessorStatusYaml ss;
        try {
            ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
        } catch (Throwable e) {
            log.error("#705.150 Error parsing current status of processor:\n{}", processor.status);
            log.error("#705.151 Error ", e);
            return null;
        }

        for (Long wbId : execContextIds) {
            DispatcherCommParamsYaml.AssignedTask result = findUnassignedTaskAndAssign(wbId, processor, ss, isAcceptOnlySigned);
            if (result!=null) {
                return result;
            }
        }
        return null;
    }

    private final Map<Long, LongHolder> bannedSince = new HashMap<>();

    public static List<Long> getIdsForSearch(List<ExecContextParamsYaml.TaskVertex> vertices, int page, int pageSize) {
        final int fromIndex = page * pageSize;
        if (vertices.size()== fromIndex) {
            return List.of();
        }
        int toIndex = fromIndex + (vertices.size()-pageSize>=fromIndex ? pageSize : vertices.size() - fromIndex);
        return vertices.subList(fromIndex, toIndex).stream()
                .map(v -> v.taskId)
                .collect(Collectors.toList());
    }

    private DispatcherCommParamsYaml.AssignedTask findUnassignedTaskAndAssign(Long execContextId, Processor processor, ProcessorStatusYaml ss, boolean isAcceptOnlySigned) {

        LongHolder longHolder = bannedSince.computeIfAbsent(processor.getId(), o -> new LongHolder(0));
        if (longHolder.value!=0 && System.currentTimeMillis() - longHolder.value < TimeUnit.MINUTES.toMillis(30)) {
            return null;
        }
        ExecContextImpl execContext = execContextRepository.findByIdForUpdate(execContextId);
        if (execContext==null) {
            return null;
        }
        final List<ExecContextParamsYaml.TaskVertex> vertices = execContextGraphTopLevelService.findAllForAssigning(execContext);
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
                catch (Exception e) {
                    throw new RuntimeException("#705.200 Error", e);
                }

                if (taskParamYaml.taskYaml.function.sourcing== EnumsApi.FunctionSourcing.git &&
                        processorStatus.gitStatusInfo.status!= Enums.GitStatus.installed) {
                    log.warn("#705.210 Can't assign task #{} to processor #{} because this processor doesn't correctly installed git, git status info: {}",
                            processor.getId(), task.getId(), processorStatus.gitStatusInfo
                    );
                    longHolder.value = System.currentTimeMillis();
                    continue;
                }

                if (taskParamYaml.taskYaml.function.env!=null) {
                    String interpreter = processorStatus.env.getEnvs().get(taskParamYaml.taskYaml.function.env);
                    if (interpreter == null) {
                        log.warn("#705.213 Can't assign task #{} to processor #{} because this processor doesn't have defined interpreter for function's env {}",
                                processor.getId(), task.getId(), taskParamYaml.taskYaml.function.env
                        );
                        longHolder.value = System.currentTimeMillis();
                        continue;
                    }
                }

                final List<EnumsApi.OS> supportedOS = getSupportedOS(taskParamYaml);
                if (processorStatus.os!=null && !supportedOS.isEmpty() && !supportedOS.contains(processorStatus.os)) {
                    log.info("#705.217 Can't assign task #{} to processor #{}, " +
                                    "because this processor doesn't support required OS version. processor: {}, function: {}",
                            processor.getId(), task.getId(), processorStatus.os, supportedOS
                    );
                    longHolder.value = System.currentTimeMillis();
                    continue;
                }

                if (isAcceptOnlySigned) {
                    if (!taskParamYaml.taskYaml.function.info.isSigned()) {
                        log.warn("#705.220 Function with code {} wasn't signed", taskParamYaml.taskYaml.function.getCode());
                        continue;
                    }
                }
                resultTask = task;
                try {
                    TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(resultTask.getParams());
                    resultTaskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, ss.taskParamsVersion);
                } catch (DowngradeNotSupportedException e) {
                    log.warn("Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
                            resultTask.getId(), processor.id, ss.taskParamsVersion);
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

        if (resultTaskParams==null) {
            throw new IllegalStateException("(resultTaskParams==null)");
        }

        // normal way of operation
        longHolder.value = 0;

        DispatcherCommParamsYaml.AssignedTask assignedTask = new DispatcherCommParamsYaml.AssignedTask();
        assignedTask.setTaskId(resultTask.getId());
        assignedTask.setExecContextId(execContextId);
        assignedTask.setParams(resultTaskParams);

        resultTask.setAssignedOn(System.currentTimeMillis());
        resultTask.setProcessorId(processor.getId());
        resultTask.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        resultTask.setResultResourceScheduledOn(0);

        taskRepository.save((TaskImpl)resultTask);
        execContextGraphTopLevelService.updateTaskExecStateByExecContextId(execContextId, resultTask.getId(), EnumsApi.TaskExecState.IN_PROGRESS.value);
        dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ASSIGNED, processor.getId(), resultTask.getId(), execContextId);

        return assignedTask;
    }

    private List<EnumsApi.OS> getSupportedOS(TaskParamsYaml taskParamYaml) {
        if (taskParamYaml.taskYaml.function !=null) {
            return FunctionCoreUtils.getSupportedOS(taskParamYaml.taskYaml.function.metas);
        }
        return List.of();
    }

    private List<Task> getAllByProcessorIdIsNullAndExecContextIdAndIdIn(Long execContextId, List<ExecContextParamsYaml.TaskVertex> vertices, int page) {
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

    public SourceCodeApiData.TaskProducingResultComplex produceTasks(boolean isPersist, SourceCodeImpl sourceCode, Long execContextId) {

        Monitoring.log("##023", Enums.Monitor.MEMORY);
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            log.error("#701.175 Can't find execContext #{}", execContextId);
            return new SourceCodeApiData.TaskProducingResultComplex(EnumsApi.SourceCodeValidateStatus.EXEC_CONTEXT_NOT_FOUND_ERROR);
        }

        ExecContextParamsYaml resourceParams = execContext.getExecContextParamsYaml();

        SourceCodeStoredParamsYaml sourceCodeStoredParams = sourceCode.getSourceCodeStoredParamsYaml();

        // parse concrete sourceCode into graph-based meta-model
        SourceCodeData.SourceCodeGraph sourceCodeGraph = SourceCodeGraphFactory.parse(
                sourceCodeStoredParams.lang, sourceCodeStoredParams.source, () -> "" + idsRepository.save(new Ids()).id);

        // create all internal variables as uninitialized
        variableService.createUninitialized(execContextId, sourceCodeGraph);

        // create all not dynamic tasks
        SourceCodeService.ProduceTaskResult produceTaskResult =taskProducingService.produceTasks(sourceCodeGraph);

        long mill = System.currentTimeMillis();
        List<SimpleVariableAndStorageUrl> initialInputResourceCodes = variableService.getIdInVariables(resourceParams.getAllVariables());
        log.debug("#701.180 Resources was acquired for {} ms", System.currentTimeMillis() - mill);

        SourceCodeService.ResourcePools pools = new SourceCodeService.ResourcePools(initialInputResourceCodes);
        if (pools.status!= EnumsApi.SourceCodeProducingStatus.OK) {
            return new SourceCodeApiData.TaskProducingResultComplex(pools.status);
        }

        // todo 2020-02-15 what do we do here?
        if (resourceParams.execContextYaml.preservePoolNames) {
            final Map<String, List<String>> collectedInputs = new HashMap<>();
            try {
                pools.collectedInputs.forEach( (key, value) -> {
                    String newKey = resourceParams.execContextYaml.variables.entrySet().stream()
                            .filter(entry -> entry.getValue().contains(key))
                            .findFirst()
                            .map(Map.Entry::getKey)
                            .orElseThrow(()-> {
                                log.error("#701.190 Can't find key for pool code {}", key );
                                throw new BreakFromForEachException();
                            });
                    collectedInputs.put(newKey, value);
                });
            } catch (BreakFromForEachException e) {
                return new SourceCodeApiData.TaskProducingResultComplex(EnumsApi.SourceCodeProducingStatus.ERROR);
            }

            pools.collectedInputs.clear();
            pools.collectedInputs.putAll(collectedInputs);
        }

        Monitoring.log("##025", Enums.Monitor.MEMORY);

        SourceCodeApiData.TaskProducingResultComplex result = new SourceCodeApiData.TaskProducingResultComplex();
        if (isPersist) {
            execContextFSM.toProduced(execContextId);
        }

        result.execContext = execContextCache.findById(execContextId);
        result.numberOfTasks = produceTaskResult.numberOfTasks;
        result.sourceCodeValidateStatus = EnumsApi.SourceCodeValidateStatus.OK;
        result.sourceCodeProducingStatus = EnumsApi.SourceCodeProducingStatus.OK;

        return result;
    }

    public SourceCodeApiData.ExecContextResult createExecContext(Long sourceCodeId, String variable, DispatcherContext context) {
        return createExecContext(variable, context, sourceCodeCache.findById(sourceCodeId));
    }

    public SourceCodeApiData.ExecContextResult createExecContext(String sourceCodeUid, String variable, DispatcherContext context) {
        return createExecContext(variable, context, sourceCodeRepository.findByUidAndCompanyId(sourceCodeUid, context.getCompanyId()));
    }

    private SourceCodeApiData.ExecContextResult createExecContext(String variable, DispatcherContext context, SourceCodeImpl sourceCode) {
        if (sourceCode==null) {
            return new SourceCodeApiData.ExecContextResult("#560.006 source code wasn't found");
        }
        if (S.b(variable)) {
            return new SourceCodeApiData.ExecContextResult("#560.006 name of variable is empty");
        }
        // validate the sourceCode
        SourceCodeApiData.SourceCodeValidation sourceCodeValidation = sourceCodeValidationService.validate(sourceCode);
        if (sourceCodeValidation.status != EnumsApi.SourceCodeValidateStatus.OK) {
            return new SourceCodeApiData.ExecContextResult(sourceCodeValidation.errorMessages);
        }

        if (globalVariableService.getIdInVariables(List.of(variable)).isEmpty()) {
            return new SourceCodeApiData.ExecContextResult( "#560.008 global variable " + variable +" wasn't found");
        }

        OperationStatusRest status = SourceCodeValidationService.checkSourceCode(sourceCode, context);
        if (status != null) {
            return new SourceCodeApiData.ExecContextResult( "#560.011 access denied: " + status.getErrorMessagesAsStr());
        }
        return createExecContextInternal(sourceCode, variable);
    }

    public SourceCodeApiData.ExecContextResult createExecContextInternal(@NonNull SourceCodeImpl sourceCode, String variable) {
        ExecContextParamsYaml.ExecContextYaml wrc = SourceCodeUtils.asExecContextParamsYaml(variable);
        SourceCodeApiData.TaskProducingResultComplex producingResult = createExecContext(sourceCode.getId(), wrc);
        if (producingResult.sourceCodeProducingStatus != EnumsApi.SourceCodeProducingStatus.OK) {
            return new SourceCodeApiData.ExecContextResult("#560.072 Error creating execContext: " + producingResult.sourceCodeProducingStatus);
        }

        SourceCodeApiData.TaskProducingResultComplex countTasks = produceTasks(false, sourceCode, producingResult.execContext.getId());
        if (countTasks.sourceCodeProducingStatus != EnumsApi.SourceCodeProducingStatus.OK) {
            changeValidStatus(producingResult.execContext.getId(), false);
            return new SourceCodeApiData.ExecContextResult("#560.077 sourceCode producing was failed, status: " + countTasks.sourceCodeProducingStatus);
        }

        if (globals.maxTasksPerExecContext < countTasks.numberOfTasks) {
            changeValidStatus(producingResult.execContext.getId(), false);
            return new SourceCodeApiData.ExecContextResult("#560.081 number of tasks for this execContext exceeded the allowed maximum number. ExecContext was created but its status is 'not valid'. " +
                    "Allowed maximum number of tasks: " + globals.maxTasksPerExecContext + ", tasks in this execContext:  " + countTasks.numberOfTasks);
        }

        SourceCodeApiData.ExecContextResult result = new SourceCodeApiData.ExecContextResult(sourceCode);
        result.execContext = producingResult.execContext;

        changeValidStatus(producingResult.execContext.getId(), true);

        return result;
    }

    public void deleteExecContext(Long execContextId, Long companyUniqueId) {
//        experimentService.resetExperimentByExecContextId(execContextId);
        applicationEventPublisher.publishEvent(new DispatcherInternalEvent.ExperimentResetEvent(execContextId));
        variableService.deleteByExecContextId(execContextId);
        ExecContext execContext = execContextCache.findById(execContextId);
        if (execContext != null) {
            // unlock sourceCode if this is the last execContext in the sourceCode
            List<Long> ids = execContextRepository.findIdsBysourceCodeId(execContext.getSourceCodeId());
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
