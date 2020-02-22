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
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task_extended_result.TaskExtendedResultYaml;
import ai.metaheuristic.commons.yaml.task_extended_result.TaskExtendedResultYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.TaskMachineLearningYaml;
import ai.metaheuristic.commons.yaml.task_ml.TaskMachineLearningYamlUtils;
import ai.metaheuristic.commons.yaml.task_ml.metrics.Metrics;
import ai.metaheuristic.commons.yaml.task_ml.metrics.MetricsUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskPersistencer {

    private static final int NUMBER_OF_TRY = 2;
    private final TaskRepository taskRepository;
    private final DispatcherEventService dispatcherEventService;

    public TaskImpl setParams(Long taskId, String taskParams) {
        return TaskFunctions.getWithSync(taskId, () -> {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
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
            }
            return null;
        });
    }

    public Enums.UploadResourceStatus setResultReceived(long taskId, boolean resultReceived) {
        return TaskFunctions.getWithSync(taskId, () -> {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    TaskImpl task = taskRepository.findByIdForUpdate(taskId);
                    if (task == null) {
                        return Enums.UploadResourceStatus.TASK_NOT_FOUND;
                    }
                    if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
                        log.warn("#307.030 Task {} was reset, can't set new value to field resultReceived", taskId);
                        return Enums.UploadResourceStatus.TASK_WAS_RESET;
                    }
                    task.setCompleted(true);
                    task.setCompletedOn(System.currentTimeMillis());
                    task.setResultReceived(resultReceived);
                    taskRepository.save(task);
                    return Enums.UploadResourceStatus.OK;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.warn("#307.040 !!!NEED TO INVESTIGATE. Error set resultReceived to {} try #{}, taskId: {}, error: {}", resultReceived, i, taskId, e.toString());
                }
            }
            return Enums.UploadResourceStatus.PROBLEM_WITH_LOCKING;
        });
    }

    public Task resetTask(final Long taskId) {
        return TaskFunctions.getWithSync(taskId, () -> {
            log.info("Start resetting task #{}", taskId);
            TaskImpl task = taskRepository.findByIdForUpdate(taskId);
            if (task ==null) {
                log.error("#307.045 task is null");
                return null;
            }
            if (task.execState==EnumsApi.TaskExecState.NONE.value) {
                return task;
            }

            task.setFunctionExecResults(null);
            task.setStationId(null);
            task.setAssignedOn(null);
            task.setCompleted(false);
            task.setCompletedOn(null);
            task.setMetrics(null);
            task.setExecState(EnumsApi.TaskExecState.NONE.value);
            task.setResultReceived(false);
            task.setResultResourceScheduledOn(0);
            task = taskRepository.save(task);

            return task;
        });
    }

    @SuppressWarnings("UnusedReturnValue")
    public Task storeExecResult(StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result, Consumer<Task> action) {
        FunctionApiData.FunctionExec functionExec = FunctionExecUtils.to(result.getResult());
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
            dispatcherEventService.publishTaskEvent(
                    state==EnumsApi.TaskExecState.OK ? EnumsApi.DispatcherEventType.TASK_FINISHED : EnumsApi.DispatcherEventType.TASK_ERROR,
                    null, result.taskId, t.getExecContextId());
            action.accept(t);
            return t;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("#307.060 !!!NEED TO INVESTIGATE. Error while storing result of execution of task, taskId: {}, error: {}", result.taskId, e.toString());
        }
        return null;
    }

    public void finishTaskAsBrokenOrError(Long taskId, EnumsApi.TaskExecState state) {
        if (state!=EnumsApi.TaskExecState.BROKEN && state!=EnumsApi.TaskExecState.ERROR) {
            throw new IllegalStateException("#307.070 state must be EnumsApi.TaskExecState.BROKEN or EnumsApi.TaskExecState.ERROR, actual: " +state);
        }
        TaskFunctions.getWithSync(taskId, () -> {
            TaskImpl task = taskRepository.findByIdForUpdate(taskId);
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
                functionExec.exec = new FunctionApiData.SystemExecResult(
                        tpy.taskYaml.function.code, false, -999, "#307.080 Task is broken, error is unknown, cant' process it"
                );
                task.setFunctionExecResults(FunctionExecUtils.toString(functionExec));
            }
            task.setResultReceived(true);

            task = taskRepository.save(task);
            dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR,null, task.id, task.getExecContextId());

            return task;
        });
    }

    public void toOkSimple(Long taskId) {
        TaskFunctions.getWithSync(taskId, () -> {
            TaskImpl task = taskRepository.findByIdForUpdate(taskId);
            if (task==null) {
                log.warn("#307.090 Can't find Task for Id: {}", taskId);
                return null;
            }
            task.setExecState(EnumsApi.TaskExecState.OK.value);
            return taskRepository.save(task);
        });
    }

    public void toInProgressSimple(Long taskId) {
        TaskFunctions.getWithSync(taskId, () -> {
            TaskImpl task = taskRepository.findByIdForUpdate(taskId);
            if (task==null) {
                log.warn("#307.100 Can't find Task for Id: {}", taskId);
                return null;
            }
            task.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
            return taskRepository.save(task);
        });
    }

    private Task prepareAndSaveTask(StationCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result, EnumsApi.TaskExecState state) {
        return TaskFunctions.getWithSync(result.taskId, () -> {
            TaskImpl task = taskRepository.findByIdForUpdate(result.taskId);
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
                if (true) {
                    throw new NotImplementedException("output resourceIds are a list");
                }
                final SourceCodeParamsYaml.Variable variable = yaml.taskYaml.resourceStorageUrls.get(yaml.taskYaml.outputResourceIds.values().iterator().next());

                if (variable.sourcing == EnumsApi.DataSourcing.disk) {
                    task.setCompleted(true);
                    task.setCompletedOn(System.currentTimeMillis());
                }
            }
            task.setFunctionExecResults(result.getResult());
            if (result.ml!=null) {
                Metrics m = MetricsUtils.to(result.ml.metrics);
                TaskMachineLearningYaml.Metrics metrics = new TaskMachineLearningYaml.Metrics(m.status, m.error, m.metrics);
                TaskMachineLearningYaml tmly = new TaskMachineLearningYaml(metrics, result.ml.fitting);
                String s = TaskMachineLearningYamlUtils.BASE_YAML_UTILS.toString(tmly);
                task.setMetrics(s);

                TaskExtendedResultYaml tery = new TaskExtendedResultYaml(result.ml.predicted);
                String extendedResult = TaskExtendedResultYamlUtils.BASE_YAML_UTILS.toString(tery);
                task.setExtendedResult(extendedResult);
            }
            else {
                task.setMetrics(null);
                task.setExtendedResult(null);
            }
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
