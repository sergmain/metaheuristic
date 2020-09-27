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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 9/23/2020
 * Time: 12:55 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskExecStateService {

    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final TaskSyncService taskSyncService;
    private final DispatcherEventService dispatcherEventService;
    private final TaskTransactionalService taskTransactionalService;

    @Nullable
    public Task resetTask(final Long taskId) {
        return taskSyncService.getWithSync(taskId, (task) -> {
            log.info("#305.010 Start re-setting task #{}", taskId);
            if (task==null) {
                log.error("#305.020 task is null");
                return null;
            }
            task.setFunctionExecResults(null);
            task.setProcessorId(null);
            task.setAssignedOn(null);
            task.setCompleted(false);
            task.setCompletedOn(null);
            task.setExecState(EnumsApi.TaskExecState.NONE.value);
            task.setResultReceived(false);
            task.setResultResourceScheduledOn(0);
            task = taskTransactionalService.save(task);

            log.info("#305.030 task #{} was re-setted to initial state", taskId);
            return task;
        });
    }
    public TaskImpl toInProgressSimpleLambda(TaskImpl task) {
        task.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        return taskTransactionalService.save(task);
    }

    private TaskImpl toSkippedSimpleLambda(TaskImpl task) {
        task.setExecState(EnumsApi.TaskExecState.SKIPPED.value);
        return taskTransactionalService.save(task);
    }

    public void toOkSimple(Long taskId) {
        taskSyncService.getWithSync(taskId, (task) -> {
            if (task==null) {
                log.warn("#305.040 Can't find Task for Id: {}", taskId);
                return null;
            }
            if (task.execState==EnumsApi.TaskExecState.OK.value) {
                return task;
            }
            task.setExecState(EnumsApi.TaskExecState.OK.value);
            return taskTransactionalService.save(task);
        });
    }

    public void toNoneSimple(Long taskId) {
        taskSyncService.getWithSync(taskId, (task) -> {
            if (task==null) {
                log.warn("#305.040 Can't find Task for Id: {}", taskId);
                return null;
            }
            if (task.execState==EnumsApi.TaskExecState.NONE.value) {
                return task;
            }
            task.setExecState(EnumsApi.TaskExecState.NONE.value);
            return taskTransactionalService.save(task);
        });
    }

    public void finishTaskAsError(Long taskId, EnumsApi.TaskExecState state, int exitCode, String console) {
        if (state!=EnumsApi.TaskExecState.ERROR) {
            throw new IllegalStateException("#305.060 state must be EnumsApi.TaskExecState.ERROR, actual: " +state);
        }
        taskSyncService.getWithSync(taskId, (task) -> {
            if (task==null) {
                log.warn("#305.080 Can't find Task for Id: {}", taskId);
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

            task = taskTransactionalService.save(task);
            dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR,null, task.id, task.getExecContextId());

            return task;
        });
    }

    private void toInProgressSimple(Long taskId) {
        taskSyncService.getWithSync(taskId, this::toInProgressSimpleLambda);
    }

    private void toSkippedSimple(Long taskId) {
        taskSyncService.getWithSync(taskId, this::toSkippedSimpleLambda);
    }

    public void changeTaskState(Long taskId, EnumsApi.TaskExecState state){
        switch (state) {
            case NONE:
                toNoneSimple(taskId);
                break;
            case ERROR:
                throw new IllegalStateException("Must be set via ExecContextFSM.finishWithError()");
//                finishTaskAsError(taskId, state, -997, "#305.100 Task was finished with an unknown error, can't process it");
//                break;
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
                throw new IllegalStateException("307.120 Right now it must be initialized somewhere else. state: " + state);
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
                log.error("307.140 Graph state is compromised, found task in graph but it doesn't exist in db");
            }
        });
    }


}
