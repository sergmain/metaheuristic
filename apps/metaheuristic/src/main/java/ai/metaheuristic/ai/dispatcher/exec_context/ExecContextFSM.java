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
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.ExecContext;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

    private final ExecContextSyncService execContextSyncService;
    private final ExecContextCache execContextCache;
    private final TaskTransactionalService taskTransactionalService;
    private final TaskRepository taskRepository;
    private final TaskExecStateService taskExecStateService;
    private final ExecContextGraphService execContextGraphService;
    private final TaskPersistencer taskPersistencer;
    private final DispatcherEventService dispatcherEventService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final ApplicationEventPublisher eventPublisher;

    public void toStarted(ExecContext execContext) {
        toStarted(execContext.getId());
    }

    private void toStarted(Long execContextId) {
        toState(execContextId, EnumsApi.ExecContextState.STARTED);
    }

    public void toStopped(Long execContextId) {
        toState(execContextId, EnumsApi.ExecContextState.STOPPED);
    }

    public void toProduced(Long execContextId) {
        toState(execContextId, EnumsApi.ExecContextState.PRODUCED);
    }

    public void toFinished(Long execContextId) {
        toStateWithCompletion(execContextId, EnumsApi.ExecContextState.FINISHED);
    }

    public void toError(Long execContextId) {
        toStateWithCompletion(execContextId, EnumsApi.ExecContextState.ERROR);
    }

    // methods with syncing

    public void toState(Long execContextId, EnumsApi.ExecContextState state) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        if (execContext.state !=state.code) {
            execContext.setState(state.code);
            execContextCache.save(execContext);
        }
    }

    private void toStateWithCompletion(Long execContextId, EnumsApi.ExecContextState state) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        if (execContext.state !=state.code || execContext.completedOn==null) {
            execContext.setCompletedOn(System.currentTimeMillis());
            execContext.setState(state.code);
            execContextCache.save(execContext);
        }
    }

    // methods related to Tasks

    public void storeExecResult(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        TaskImpl task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("Reporting about non-existed task #{}", result.taskId);
            return;
        }
        execContextSyncService.getWithSyncNullable(task.execContextId, () -> {
            taskTransactionalService.storeExecResult(result);
            return null;
        });
    }

    public OperationStatusRest resetTask(Long taskId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#705.080 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }

        return execContextSyncService.getWithSync(task.execContextId, () -> {

            Task t = taskExecStateService.resetTask(task.id);
            if (t == null) {
                ExecContextOperationStatusWithTaskList withTaskList = execContextGraphService.updateGraphWithSettingAllChildrenTasksAsError(
                        execContextCache.findById(task.execContextId), task.id);
                taskExecStateService.updateTasksStateInDb(withTaskList);
                if (withTaskList.status.status == EnumsApi.OperationStatus.ERROR) {
                    return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#705.100 Can't re-run task #" + taskId + ", see log for more information");
                }
            } else {
                ExecContextOperationStatusWithTaskList withTaskList = execContextGraphService.updateGraphWithResettingAllChildrenTasks(
                        execContextCache.findById(task.execContextId), task.id);
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
                    toState(ec.id, EnumsApi.ExecContextState.STARTED);
                }
            }

            return OperationStatusRest.OPERATION_STATUS_OK;
        });
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

    private void finishWithError(Long taskId, Long execContextId, @Nullable String taskContextId, @Nullable String params) {
        finishWithError(taskId, "#303.080 Task was finished with an unknown error, can't process it", execContextId, taskContextId, params);
    }

    private void finishWithError(Long taskId, String console, Long execContextId, @Nullable String taskContextId, @Nullable String params) {
        taskExecStateService.finishTaskAsError(taskId, EnumsApi.TaskExecState.ERROR, -10001, console);
        final ExecContextOperationStatusWithTaskList s = execContextGraphService.updateTaskExecStates(
                execContextCache.findById(execContextId), Map.of(taskId, new TaskData.TaskState(taskId, EnumsApi.TaskExecState.ERROR.value, System.currentTimeMillis(), params)));

        taskExecStateService.updateTasksStateInDb(s);

        if (taskContextId!=null) {
            ExecContextOperationStatusWithTaskList status = execContextGraphService.updateGraphWithSettingAllChildrenTasksAsSkipped(
                    execContextCache.findById(execContextId), taskContextId, taskId);
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

    private final Map<Long, AtomicLong> bannedSince = new HashMap<>();

    private List<TaskImpl> getAllByProcessorIdIsNullAndExecContextIdAndIdIn(Long execContextId, List<ExecContextData.TaskVertex> vertices, int page) {
        final List<Long> idsForSearch = getIdsForSearch(vertices, page, 20);
        if (idsForSearch.isEmpty()) {
            return List.of();
        }
        return taskRepository.findForAssigning(execContextId, idsForSearch);
    }

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

        execContextGraphTopLevelService.updateTaskExecStateWithoutSync(execContextCache.findById(execContextId), resultTask.getId(), EnumsApi.TaskExecState.IN_PROGRESS.value, null);
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

    public EnumsApi.TaskProducingStatus toProducing(Long execContextId, ExecContextService execContextService) {
        return execContextSyncService.getWithSync(execContextId, () -> {
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
        });
    }

}
