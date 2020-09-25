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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Serge
 * Date: 9/23/2020
 * Time: 11:39 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
@Transactional
public class TaskTransactionalService {

    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final VariableService variableService;
    private final TaskProducingCoreService taskProducingCoreService;
    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final DispatcherEventService dispatcherEventService;
    private final TaskSyncService taskSyncService;
    private final ExecContextCache execContextCache;
    private final ExecContextFSM execContextFSM;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskExecStateService taskExecStateService;
    private final ExecContextSyncService execContextSyncService;

    public OperationStatusRest resetTask(Long taskId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#705.080 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }

        return execContextSyncService.getWithSync(task.execContextId, execContext -> {

            Task t = taskExecStateService.resetTask(task.id);
            if (t == null) {
                ExecContextOperationStatusWithTaskList withTaskList = execContextGraphTopLevelService.updateGraphWithSettingAllChildrenTasksAsError(task.getExecContextId(), task.id);
                taskExecStateService.updateTasksStateInDb(withTaskList);
                if (withTaskList.status.status == EnumsApi.OperationStatus.ERROR) {
                    return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.100 Can't re-run task #" + taskId + ", see log for more information");
                }
            } else {
                ExecContextOperationStatusWithTaskList withTaskList = execContextGraphTopLevelService.updateGraphWithResettingAllChildrenTasks(task.execContextId, task.id);
                if (withTaskList == null) {
                    TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                    finishWithError(taskId, task.execContextId, tpy.task.taskContextId, task.params);
                    return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                            "#705.120 Can't re-run task " + taskId + ", this task is orphan and doesn't belong to any execContext");
                }

                if (withTaskList.status.status == EnumsApi.OperationStatus.ERROR) {
                    return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.140 Can't re-run task #" + taskId + ", see log for more information");
                }
                taskExecStateService.updateTasksStateInDb(withTaskList);

                ExecContextImpl ec = execContextCache.findById(task.execContextId);
                if (ec != null && ec.state != EnumsApi.ExecContextState.STARTED.code) {
                    execContextFSM.toState(ec.id, EnumsApi.ExecContextState.STARTED);
                }
            }

            return OperationStatusRest.OPERATION_STATUS_OK;
        });
    }

    public void storeExecResult(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result, Consumer<Task> action) {
        FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(result.getResult());
        if (functionExec==null) {
            String es = "#303.045 Task #" + result.taskId + " has empty execResult";
            log.info(es);
            functionExec = new FunctionApiData.FunctionExec();
        }
        FunctionApiData.SystemExecResult systemExecResult = functionExec.generalExec!=null ? functionExec.generalExec : functionExec.exec;
        if (!systemExecResult.isOk) {
            log.warn("#303.050 Task #{} finished with error, functionCode: {}, console: {}",
                    result.taskId,
                    systemExecResult.functionCode,
                    StringUtils.isNotBlank(systemExecResult.console) ? systemExecResult.console : "<console output is empty>");
        }
        try {
            EnumsApi.TaskExecState state = functionExec.allFunctionsAreOk() ? EnumsApi.TaskExecState.OK : EnumsApi.TaskExecState.ERROR;
            Task t = prepareAndSaveTask(result, state);
            if (t==null) {
                return;
            }
            dispatcherEventService.publishTaskEvent(
                    state==EnumsApi.TaskExecState.OK ? EnumsApi.DispatcherEventType.TASK_FINISHED : EnumsApi.DispatcherEventType.TASK_ERROR,
                    null, result.taskId, t.getExecContextId());
            action.accept(t);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#303.060 !!!NEED TO INVESTIGATE. Error while storing result of execution of task, taskId: {}, error: {}", result.taskId, e.toString());
            log.error("#303.061 ObjectOptimisticLockingFailureException", e);
        }
    }

    @Nullable
    private Task prepareAndSaveTask(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result, EnumsApi.TaskExecState state) {
        return taskSyncService.getWithSync(result.taskId, (task) -> {
            if (task==null) {
                log.warn("#303.110 Can't find Task for Id: {}", result.taskId);
                return null;
            }
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
            task = taskRepository.save(task);

            return task;
        });
    }

    public void storeExecResult(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        TaskImpl task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("Reporting about non-existed task #{}", result.taskId);
            return;
        }
        execContextSyncService.getWithSyncNullable(task.execContextId, execContext -> {
            storeExecResult(result, t -> {
                if (t != null) {
                    TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(t.getParams());
                    ExecContextOperationStatusWithTaskList status = updateTaskExecStateByExecContextId(t.getExecContextId(), t.getId(), t.getExecState(), tpy.task.taskContextId);
                    for (ExecContextData.TaskVertex childrenTask : status.childrenTasks) {
                        taskExecStateService.changeTaskState(childrenTask.taskId, childrenTask.execState);
                    }
                }
            });
            return null;
        });
    }

    @Transactional(readOnly = true)
    public List<TaskData.SimpleTaskInfo> getSimpleTaskInfos(Long execContextId) {
        try (Stream<TaskImpl> stream = taskRepository.findAllByExecContextIdAsStream(execContextId)) {
            return stream.map(o-> {
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params);
                return new TaskData.SimpleTaskInfo(o.id,  EnumsApi.TaskExecState.from(o.execState).toString(), tpy.task.taskContextId, tpy.task.processCode, tpy.task.function.code);
            }).collect(Collectors.toList());
        }
    }

    /**
     * @param files
     * @param execContextId
     * @param currTaskNumber
     * @param parentTaskId
     * @param inputVariableName
     * @param lastIds
     */
    public void createTasksForSubProcesses(
            Stream<BatchTopLevelService.FileWithMapping> files, @Nullable String inputVariableContent, Long execContextId, InternalFunctionData.ExecutionContextData executionContextData,
            AtomicInteger currTaskNumber, Long parentTaskId, String inputVariableName,
            List<Long> lastIds) {

        ExecContextParamsYaml execContextParamsYaml = executionContextData.execContextParamsYaml;
        List<ExecContextData.ProcessVertex> subProcesses = executionContextData.subProcesses;
        Map<String, Map<String, String>> inlines = executionContextData.execContextParamsYaml.variables.inline;
        ExecContextParamsYaml.Process process = executionContextData.process;

        List<Long> parentTaskIds = List.of(parentTaskId);
        TaskImpl t = null;
        String subProcessContextId = null;
        for (ExecContextData.ProcessVertex subProcess : subProcesses) {
            final ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(subProcess.process);
            if (p==null) {
                throw new BreakFromLambdaException("#995.320 Process '" + subProcess.process + "' wasn't found");
            }

            if (process.logic!= EnumsApi.SourceCodeSubProcessLogic.sequential) {
                throw new BreakFromLambdaException("#995.340 only the 'sequential' logic is supported");
            }
            // all subProcesses must have the same processContextId
            if (subProcessContextId!=null && !subProcessContextId.equals(subProcess.processContextId)) {
                throw new BreakFromLambdaException("#995.360 Different contextId, prev: "+ subProcessContextId+", next: " +subProcess.processContextId);
            }

            String currTaskContextId = ContextUtils.getTaskContextId(subProcess.processContextId, Integer.toString(currTaskNumber.get()));
            t = taskProducingCoreService.createTaskInternal(execContextId, execContextParamsYaml, p, currTaskContextId, inlines);
            if (t==null) {
                throw new BreakFromLambdaException("#995.380 Creation of task failed");
            }
            List<TaskApiData.TaskWithContext> currTaskIds = List.of(new TaskApiData.TaskWithContext(t.getId(), currTaskContextId));
            execContextGraphTopLevelService.addNewTasksToGraph(execContextId, parentTaskIds, currTaskIds);
            parentTaskIds = List.of(t.getId());
            subProcessContextId = subProcess.processContextId;
        }

        if (subProcessContextId!=null) {
            String currTaskContextId = ContextUtils.getTaskContextId(subProcessContextId, Integer.toString(currTaskNumber.get()));
            lastIds.add(t.id);

            List<VariableUtils.VariableHolder> variableHolders = files
                    .map(f-> {
                        String variableName = S.f("mh.array-element-%s-%d", UUID.randomUUID().toString(), System.currentTimeMillis());

                        Variable v;
                        try {
                            try( FileInputStream fis = new FileInputStream(f.file)) {
                                v = variableService.createInitialized(fis, f.file.length(), variableName, f.originName, execContextId, currTaskContextId);
                            }
                        } catch (IOException e) {
                            throw new BreakFromLambdaException(e);
                        }
                        SimpleVariable sv = new SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId);
                        return new VariableUtils.VariableHolder(sv);
                    })
                    .collect(Collectors.toList());


            if (!S.b(inputVariableContent)) {
                String variableName = S.f("mh.array-element-%s-%d", UUID.randomUUID().toString(), System.currentTimeMillis());
                Variable v;
                try {
                    final byte[] bytes = inputVariableContent.getBytes();
                    try(InputStream is = new ByteArrayInputStream(bytes)) {
                        v = variableService.createInitialized(is, bytes.length, variableName, variableName, execContextId, currTaskContextId);
                    }
                } catch (IOException e) {
                    throw new BreakFromLambdaException(e);
                }
                SimpleVariable sv = new SimpleVariable(v.id, v.name, v.params, v.filename, v.inited, v.nullified, v.taskContextId);
                VariableUtils.VariableHolder variableHolder = new VariableUtils.VariableHolder(sv);
                variableHolders.add(variableHolder);
            }

            VariableArrayParamsYaml vapy = VariableUtils.toVariableArrayParamsYaml(variableHolders);
            String yaml = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.toString(vapy);
            byte[] bytes = yaml.getBytes();
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            Variable v = variableService.createInitialized(bais, bytes.length, inputVariableName, null, execContextId, currTaskContextId);

            int i=0;
        }
    }

    public void processResendTaskOutputResourceResult(@Nullable String processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
        switch(status) {
            case SEND_SCHEDULED:
                log.info("#317.010 Processor #{} scheduled sending of output variables of task #{} for sending. This is normal operation of Processor", processorId, taskId);
                break;
            case VARIABLE_NOT_FOUND:
            case TASK_IS_BROKEN:
            case TASK_PARAM_FILE_NOT_FOUND:
                TaskImpl task = taskRepository.findById(taskId).orElse(null);
                if (task==null) {
                    log.warn("#317.020 Task obsolete and was already deleted");
                    return;
                }
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                finishWithError(taskId, task.execContextId, tpy.task.taskContextId, task.params);
                break;
            case OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE:
                Enums.UploadResourceStatus uploadResourceStatus = taskPersistencer.setResultReceived(taskId, variableId);
                if (uploadResourceStatus==Enums.UploadResourceStatus.OK) {
                    log.info("#317.040 the output resource of task #{} is stored on external storage which was defined by disk://. This is normal operation of sourceCode", taskId);
                }
                else {
                    log.info("#317.050 can't update isCompleted field for task #{}", taskId);
                }
                break;
        }
    }

    public void markAsFinishedWithError(TaskImpl task, ExecContextImpl execContext, TaskParamsYaml taskParamsYaml, InternalFunctionData.InternalFunctionProcessingResult result) {
        log.error("#707.050 error type: {}, message: {}\n\tsourceCodeId: {}, execContextId: {}", result.processing, result.error, execContext.sourceCodeId, execContext.id);
        final Long taskId = task.id;
        final String console = "#707.060 Task #" + taskId + " was finished with status '" + result.processing + "', text of error: " + result.error;

        final Long execContextId = task.execContextId;
        final String taskContextId = taskParamsYaml.task.taskContextId;
        final String params = task.params;

        finishWithError(taskId, console, execContextId, taskContextId, params);
    }

    public void finishWithError(Long taskId, Long execContextId, @Nullable String taskContextId, @Nullable String params) {
        finishWithError(taskId, "#303.080 Task was finished with an unknown error, can't process it", execContextId, taskContextId, params);
    }

    @SuppressWarnings("unused")
    public void finishWithError(Long taskId, String console, Long execContextId, @Nullable String taskContextId, @Nullable String params) {
        taskExecStateService.finishTaskAsError(taskId, EnumsApi.TaskExecState.ERROR, -10001, console);
        ExecContextOperationStatusWithTaskList s = execContextGraphTopLevelService.updateTaskExecStates(
                execContextId, Map.of(taskId, new TaskData.TaskState(taskId, EnumsApi.TaskExecState.ERROR.value, System.currentTimeMillis(), params)));

        if (taskContextId!=null) {
            ExecContextOperationStatusWithTaskList status =
                    execContextGraphTopLevelService.updateGraphWithSettingAllChildrenTasksAsSkipped(execContextId, taskContextId, taskId);
        }
    }

    public ExecContextOperationStatusWithTaskList updateTaskExecStates(Long execContextId, Map<Long, TaskData.TaskState> taskStates) {
        return execContextGraphTopLevelService.updateTaskExecStates(execContextId, taskStates);
    }

    public ExecContextOperationStatusWithTaskList updateTaskExecStateByExecContextId(Long execContextId, Long taskId, int execState, @Nullable String taskContextId) {
        return execContextSyncService.getWithSync(execContextId, execContext -> {
            final ExecContextOperationStatusWithTaskList status = execContextGraphTopLevelService.updateTaskExecStateWithoutSync(execContext, taskId, execState, taskContextId);
            return status;
        });
    }

    public void persistOutputVariables(ExecContextData.AssignedTaskComplex assignedTaskComplex, TaskParamsYaml taskParams, ExecContextImpl execContext, ExecContextParamsYaml.Process p) {
        variableService.initOutputVariables(taskParams, execContext, p);
        taskPersistencer.setParams(assignedTaskComplex.task.getId(), taskParams);
    }

    private final Map<Long, AtomicLong> bannedSince = new HashMap<>();

    @Nullable
    public ExecContextData.AssignedTaskComplex findUnassignedTaskAndAssign(Long execContextId, Processor processor, ProcessorStatusYaml ss, boolean isAcceptOnlySigned) {

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
        final ProcessorStatusYaml processorStatus = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);

        int page = 0;
        TaskImpl resultTask = null;
        String resultTaskContextId = null;
        String resultTaskParams = null;
        List<TaskImpl> tasks;
        while ((tasks = getAllByProcessorIdIsNullAndExecContextIdAndIdIn(execContextId, vertices, page++)).size()>0) {
            for (TaskImpl task : tasks) {
                final TaskParamsYaml taskParamYaml;
                try {
                    taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                }
                catch (YAMLException e) {
                    log.error("#705.420 Task #{} has broken params yaml and will be skipped, error: {}, params:\n{}", task.getId(), e.toString(),task.getParams());
                    finishWithError(task.getId(), task.execContextId, null, null);
                    continue;
                }
                if (task.execState==EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    // may be happened because of multi-threaded processing of internal function
                    continue;
                }
                if (task.execState!=EnumsApi.TaskExecState.NONE.value) {
                    log.warn("#705.440 Task #{} with function '{}' was already processed with status {}",
                            task.getId(), taskParamYaml.task.function.code, EnumsApi.TaskExecState.from(task.execState));
                    continue;
                }
                // all tasks with internal function will be processed in a different thread
                if (taskParamYaml.task.context== EnumsApi.FunctionExecContext.internal) {
                    // Do Not set EnumsApi.TaskExecState.IN_PROGRESS here.
                    // it'll be set in ai.metaheuristic.ai.dispatcher.event.TaskWithInternalContextEventService.handleAsync
                    task.setAssignedOn(System.currentTimeMillis());
                    task.setResultResourceScheduledOn(0);
                    taskRepository.save(task);

                    eventPublisher.publishEvent(new TaskWithInternalContextEvent(task.getId()));
                    continue;
                }

                if (gitUnavailable(taskParamYaml.task, processorStatus.gitStatusInfo.status!= Enums.GitStatus.installed) ) {
                    log.warn("#705.460 Can't assign task #{} to processor #{} because this processor doesn't correctly installed git, git status info: {}",
                            processor.getId(), task.getId(), processorStatus.gitStatusInfo
                    );
                    longHolder.set(System.currentTimeMillis());
                    continue;
                }

                if (!S.b(taskParamYaml.task.function.env)) {
                    String interpreter = processorStatus.env.getEnvs().get(taskParamYaml.task.function.env);
                    if (interpreter == null) {
                        log.warn("#705.480 Can't assign task #{} to processor #{} because this processor doesn't have defined interpreter for function's env {}",
                                processor.getId(), task.getId(), taskParamYaml.task.function.env
                        );
                        longHolder.set(System.currentTimeMillis());
                        continue;
                    }
                }

                final List<EnumsApi.OS> supportedOS = FunctionCoreUtils.getSupportedOS(taskParamYaml.task.function.metas);
                if (processorStatus.os!=null && !supportedOS.isEmpty() && !supportedOS.contains(processorStatus.os)) {
                    log.info("#705.500 Can't assign task #{} to processor #{}, " +
                                    "because this processor doesn't support required OS version. processor: {}, function: {}",
                            processor.getId(), task.getId(), processorStatus.os, supportedOS
                    );
                    longHolder.set(System.currentTimeMillis());
                    continue;
                }

                if (isAcceptOnlySigned) {
                    if (taskParamYaml.task.function.checksumMap==null || taskParamYaml.task.function.checksumMap.keySet().stream().noneMatch(o->o.isSigned)) {
                        log.warn("#705.520 Function with code {} wasn't signed", taskParamYaml.task.function.getCode());
                        continue;
                    }
                }
                resultTask = task;
                try {
                    TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
                    resultTaskContextId = tpy.task.taskContextId;
                    resultTaskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(tpy, ss.taskParamsVersion);
                } catch (DowngradeNotSupportedException e) {
                    log.warn("#705.540 Task #{} can't be assigned to processor #{} because it's too old, downgrade to required taskParams level {} isn't supported",
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
        taskRepository.save(resultTask);

        updateTaskExecStateByExecContextId(execContextId, resultTask.getId(), EnumsApi.TaskExecState.IN_PROGRESS.value, null);
        dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ASSIGNED, processor.getId(), resultTask.getId(), execContextId);

        return assignedTaskComplex;
    }

    public static List<Long> getIdsForSearch(List<ExecContextData.TaskVertex> vertices, int page, int pageSize) {
        final int fromIndex = page * pageSize;
        if (vertices.size()<=fromIndex) {
            return List.of();
        }
        int toIndex = fromIndex + (vertices.size()-pageSize>=fromIndex ? pageSize : vertices.size() - fromIndex);
        return vertices.subList(fromIndex, toIndex).stream()
                .map(v -> v.taskId)
                .collect(Collectors.toList());
    }

    private List<TaskImpl> getAllByProcessorIdIsNullAndExecContextIdAndIdIn(Long execContextId, List<ExecContextData.TaskVertex> vertices, int page) {
        final List<Long> idsForSearch = getIdsForSearch(vertices, page, 20);
        if (idsForSearch.isEmpty()) {
            return List.of();
        }
        return taskRepository.findForAssigning(execContextId, idsForSearch);
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


}
