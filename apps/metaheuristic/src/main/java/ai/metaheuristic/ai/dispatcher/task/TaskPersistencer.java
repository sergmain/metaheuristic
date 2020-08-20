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
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskPersistencer {

    private final TaskRepository taskRepository;
    private final TaskSyncService taskSyncService;
    private final DispatcherEventService dispatcherEventService;

    @Nullable
    public TaskImpl setParams(Long taskId, TaskParamsYaml params) {
        return setParams(taskId, TaskParamsYamlUtils.BASE_YAML_UTILS.toString(params));
    }

    @Nullable
    public TaskImpl setParams(Long taskId, String taskParams) {
        return taskSyncService.getWithSync(taskId, (task) -> {
            try {
                if (task == null) {
                    log.warn("#307.010 Task with taskId {} wasn't found", taskId);
                    return null;
                }
                task.setParams(taskParams);
                taskRepository.save(task);
                return task;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.error("#307.020 !!!NEED TO INVESTIGATE. Error set setParams to {}, taskId: {}, error: {}", taskParams, taskId, e.toString());
            }
            return null;
        });
    }

    public Enums.UploadResourceStatus setResultReceived(Long taskId, Long variableId) {
        Enums.UploadResourceStatus status = taskSyncService.getWithSync(taskId, (task) -> {
            try {
                if (task == null) {
                    return Enums.UploadResourceStatus.TASK_NOT_FOUND;
                }
                if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
                    log.warn("#307.030 Task {} was reset, can't set new value to field resultReceived", taskId);
                    return Enums.UploadResourceStatus.TASK_WAS_RESET;
                }
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                TaskParamsYaml.OutputVariable output = tpy.task.outputs.stream().filter(o->o.id.equals(variableId)).findAny().orElse(null);
                if (output==null) {
                    return Enums.UploadResourceStatus.UNRECOVERABLE_ERROR;
                }
                output.uploaded = true;
                task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);

                boolean allUploaded = tpy.task.outputs.isEmpty() || tpy.task.outputs.stream().allMatch(o -> o.uploaded);
                task.setCompleted(allUploaded);
                task.setCompletedOn(System.currentTimeMillis());
                task.setResultReceived(allUploaded);
                taskRepository.save(task);
                return Enums.UploadResourceStatus.OK;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("#307.040 !!!NEED TO INVESTIGATE. Error set resultReceived for taskId: {}, variableId: {}, error: {}", taskId, variableId, e.toString());
                log.warn("#307.041 ObjectOptimisticLockingFailureException", e);
                return Enums.UploadResourceStatus.PROBLEM_WITH_LOCKING;
            }
        });
        if (status==null) {
            return Enums.UploadResourceStatus.TASK_NOT_FOUND;
        }
        return status;
    }

    public Enums.UploadResourceStatus setResultReceivedForInternalFunction(Long taskId) {
        Enums.UploadResourceStatus status = taskSyncService.getWithSync(taskId, (task) -> {
            try {
                if (task == null) {
                    return Enums.UploadResourceStatus.TASK_NOT_FOUND;
                }
                if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
                    log.warn("#307.030 Task {} was reset, can't set new value to field resultReceived", taskId);
                    return Enums.UploadResourceStatus.TASK_WAS_RESET;
                }
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                tpy.task.outputs.forEach(o->o.uploaded = true);
                task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);
                task.setCompleted(true);
                task.setCompletedOn(System.currentTimeMillis());
                task.setResultReceived(true);
                taskRepository.save(task);
                return Enums.UploadResourceStatus.OK;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("#307.040 !!!NEED TO INVESTIGATE. Error set resultReceived for taskId: {}, error: {}", taskId, e.toString());
                log.warn("#307.041 ObjectOptimisticLockingFailureException", e);
                return Enums.UploadResourceStatus.PROBLEM_WITH_LOCKING;
            }
        });
        if (status==null) {
            return Enums.UploadResourceStatus.TASK_NOT_FOUND;
        }
        return status;
    }

    @Nullable
    public Task resetTask(final Long taskId) {
        return taskSyncService.getWithSync(taskId, (task) -> {
            log.info("Start resetting task #{}", taskId);
            if (task==null) {
                log.error("#307.045 task is null");
                return null;
            }
            if (task.execState==EnumsApi.TaskExecState.NONE.value) {
                return task;
            }

            task.setFunctionExecResults(null);
            task.setProcessorId(null);
            task.setAssignedOn(null);
            task.setCompleted(false);
            task.setCompletedOn(null);
            task.setExecState(EnumsApi.TaskExecState.NONE.value);
            task.setResultReceived(false);
            task.setResultResourceScheduledOn(0);
            task = taskRepository.save(task);

            return task;
        });
    }

    public void storeExecResult(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result, Consumer<Task> action) {
        FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(result.getResult());
        if (functionExec==null) {
            String es = "#307.045 Task #" + result.taskId + " has empty execResult";
            log.info(es);
            functionExec = new FunctionApiData.FunctionExec();
        }
        FunctionApiData.SystemExecResult systemExecResult = functionExec.generalExec!=null ? functionExec.generalExec : functionExec.exec;
        if (!systemExecResult.isOk) {
            log.warn("#307.050 Task #{} finished with error, functionCode: {}, console: {}",
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
            log.error("#307.060 !!!NEED TO INVESTIGATE. Error while storing result of execution of task, taskId: {}, error: {}", result.taskId, e.toString());
            log.error("#307.061 ObjectOptimisticLockingFailureException", e);
        }
    }

    public void finishTaskAsBrokenOrError(Long taskId, EnumsApi.TaskExecState state) {
        finishTaskAsBrokenOrError(taskId, state, -999, "#307.080 Task is broken, error is unknown, cant' process it");
    }

    public void finishTaskAsBrokenOrError(Long taskId, EnumsApi.TaskExecState state, int exitCode, String console) {
        if (state!=EnumsApi.TaskExecState.BROKEN && state!=EnumsApi.TaskExecState.ERROR) {
            throw new IllegalStateException("#307.070 state must be EnumsApi.TaskExecState.BROKEN or EnumsApi.TaskExecState.ERROR, actual: " +state);
        }
        taskSyncService.getWithSync(taskId, (task) -> {
            if (task==null) {
                log.warn("#307.080 Can't find Task for Id: {}", taskId);
                return null;
            }
            if (task.execState==state.value && task.isCompleted && task.resultReceived && !S.b(task.functionExecResults)) {
                return task;
            }
            task.setExecState(state.value);
            task.setCompleted(true);
            task.setCompletedOn(System.currentTimeMillis());

            if (task.functionExecResults ==null || task.functionExecResults.isBlank()) {
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
                functionExec.exec = new FunctionApiData.SystemExecResult(tpy.task.function.code, false, exitCode, console);
                task.setFunctionExecResults(FunctionExecUtils.toString(functionExec));
            }
            task.setResultReceived(true);

            task = taskRepository.save(task);
            dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR,null, task.id, task.getExecContextId());

            return task;
        });
    }

    public void toOkSimple(Long taskId) {
        taskSyncService.getWithSync(taskId, (task) -> {
            if (task==null) {
                log.warn("#307.090 Can't find Task for Id: {}", taskId);
                return null;
            }
            task.setExecState(EnumsApi.TaskExecState.OK.value);
            return taskRepository.save(task);
        });
    }

    @Nullable
    public TaskImpl toInProgressSimple(Long taskId) {
        return taskSyncService.getWithSync(taskId, (task) -> toInProgressSimpleLambda(taskId, task));
    }

    @Nullable
    public TaskImpl toSkippedSimple(Long taskId) {
        return taskSyncService.getWithSync(taskId, (task) -> toSkippedSimpleLambda(taskId, task));
    }

    @Nullable
    public TaskImpl toInProgressSimpleLambda(Long taskId, TaskImpl task) {
        if (task==null) {
            log.warn("#307.100 Can't find Task for Id: {}", taskId);
            return null;
        }
        task.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        return taskRepository.save(task);
    }

    @Nullable
    public TaskImpl toSkippedSimpleLambda(Long taskId, TaskImpl task) {
        if (task==null) {
            log.warn("#307.100 Can't find Task for Id: {}", taskId);
            return null;
        }
        task.setExecState(EnumsApi.TaskExecState.SKIPPED.value);
        return taskRepository.save(task);
    }

    @Nullable
    private Task prepareAndSaveTask(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result, EnumsApi.TaskExecState state) {
        return taskSyncService.getWithSync(result.taskId, (task) -> {
            if (task==null) {
                log.warn("#307.110 Can't find Task for Id: {}", result.taskId);
                return null;
            }
            task.setExecState(state.value);

            if (state==EnumsApi.TaskExecState.ERROR || state==EnumsApi.TaskExecState.BROKEN ) {
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

    public void changeTaskState(Long taskId, EnumsApi.TaskExecState state){
        switch (state) {
            case NONE:
                resetTask(taskId);
                break;
            case BROKEN:
            case ERROR:
                finishTaskAsBrokenOrError(taskId, state);
                break;
            case OK:
                toOkSimple(taskId);
                break;
            case IN_PROGRESS:
                toInProgressSimple(taskId);
                break;
            case SKIPPED:
                toSkippedSimple(taskId);
                break;
            default:
                throw new IllegalStateException("Right now it must be initialized somewhere else. state: " + state);
        }
    }

    public void updateTasksStateInDb(ExecContextOperationStatusWithTaskList status) {
        status.childrenTasks.forEach(t -> {
            TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
            if (task != null) {
                if (task.execState != t.execState.value) {
                    changeTaskState(task.id, t.execState);
                }
            } else {
                log.error("Graph state is compromised, found task in graph but it doesn't exist in db");
            }
        });
    }

}
