/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.experiment.task.SimpleTaskExecResult;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class TaskPersistencer {

    private static final int NUMBER_OF_TRY = 2;
    private final TaskRepository taskRepository;

    private final Object syncObj = new Object();

    public TaskImpl setParams(long taskId, String taskParams) {
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
                    if (task == null) {
                        log.warn("#307.01 Task with taskId {} wasn't found", taskId);
                        return null;
                    }
                    task.setParams(taskParams);
                    taskRepository.save(task);
                    return task;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error("#307.07 Error set setParams to {}, taskId: {}, error: {}", taskParams, taskId, e.toString());
                }
            }
        }
        return null;
    }

    public Enums.UploadResourceStatus setResultReceived(long taskId, boolean resultReceived) {
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
                    if (task == null) {
                        return Enums.UploadResourceStatus.TASK_NOT_FOUND;
                    }
                    if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
                        log.warn("#307.12 Task {} was reset, can't set new value to field resultReceived", taskId);
                        return Enums.UploadResourceStatus.TASK_WAS_RESET;
                    }
                    task.setCompleted(true);
                    task.setCompletedOn(System.currentTimeMillis());
                    task.setResultReceived(resultReceived);
                    taskRepository.save(task);
                    return Enums.UploadResourceStatus.OK;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.warn("#307.18 Error set resultReceived to {} try #{}, taskId: {}, error: {}", resultReceived, i, taskId, e.toString());
                }
            }
        }
        return Enums.UploadResourceStatus.PROBLEM_WITH_LOCKING;
    }

    public Task resetTask(Long taskId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return null;
        }
        return resetTask(task);
    }

    public Task resetTask(TaskImpl task) {
        log.info("Start resetting task #{}", task.getId());

/*
        // TODO 2019-07-04 do we still need this synchronization?
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
*/
                    task.setSnippetExecResults(null);
                    task.setStationId(null);
                    task.setAssignedOn(null);
                    task.setCompleted(false);
                    task.setCompletedOn(null);
                    task.setMetrics(null);
                    task.setExecState(EnumsApi.TaskExecState.NONE.value);
                    task.setResultReceived(false);
                    task.setResultResourceScheduledOn(0);
                    taskRepository.save(task);

                    return task;
/*
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error("#307.25 Error while resetting task, try: {}, taskId: {}, error: {}",  i, task.getId(), e.toString());
                }
            }
        }
        return null;
*/
    }

    @FunctionalInterface
    public interface PostTaskCreationAction {
        void execute(Task t);
    }

    @SuppressWarnings("UnusedReturnValue")
    public Task storeExecResult(SimpleTaskExecResult result, PostTaskCreationAction action) {
        synchronized (syncObj) {
            SnippetApiData.SnippetExec snippetExec = SnippetExecUtils.to(result.getResult());
            if (!snippetExec.exec.isOk) {
                log.info("#307.27 Task #{} finished with error, console: {}",
                        result.taskId,
                        StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<console output is empty>");
            }
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    Task t = prepareAndSaveTask(result, snippetExec.allSnippetsAreOk() ? EnumsApi.TaskExecState.OK : EnumsApi.TaskExecState.ERROR);
                    action.execute(t);
                    return t;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error("#307.29 Error while storing result of execution of task, taskId: {}, error: {}", result.taskId, e.toString());
                }
            }
        }
        return null;
    }

    @SuppressWarnings("UnusedReturnValue")
    public void finishTaskAsBroken(Long taskId) {
        synchronized (syncObj) {
            TaskImpl task = taskRepository.findById(taskId).orElse(null);
            if (task==null) {
                log.warn("#307.33 Can't find Task for Id: {}", taskId);
                return;
            }
            // TODO 2019-07-16 why we were using fake Id for station. Do we still need it?
            // fake station Id
//            task.setStationId(-1L);
            task.setExecState(EnumsApi.TaskExecState.BROKEN.value);
            task.setCompleted(true);
            task.setCompletedOn(System.currentTimeMillis());

            task.setSnippetExecResults("Task is broken, cant' process it");
            task.setResultReceived(true);

            //noinspection UnusedAssignment
            task = taskRepository.save(task);
        }
    }

    private Task prepareAndSaveTask(SimpleTaskExecResult result, EnumsApi.TaskExecState state) {
        TaskImpl task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("#307.33 Can't find Task for Id: {}", result.taskId);
            return null;
        }
        task.setExecState(state.value);

        if (state== EnumsApi.TaskExecState.ERROR) {
            task.setCompleted(true);
            task.setCompletedOn(System.currentTimeMillis());
        }
        else {
            TaskParamsYaml yaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            final DataStorageParams dataStorageParams = yaml.taskYaml.resourceStorageUrls.get(yaml.taskYaml.outputResourceCode);

            if (dataStorageParams.sourcing == EnumsApi.DataSourcing.disk) {
                task.setCompleted(true);
                task.setCompletedOn(System.currentTimeMillis());
            }
        }

        task.setSnippetExecResults(result.getResult());
        task.setMetrics(result.getMetrics());
        task.setResultResourceScheduledOn(System.currentTimeMillis());
        task = taskRepository.save(task);

        return task;
    }

}
