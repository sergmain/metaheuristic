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
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.experiment.task.SimpleTaskExecResult;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.ai.yaml.task.TaskParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("launchpad")
public class TaskPersistencer {

    private static final int NUMBER_OF_TRY = 2;
    private final TaskRepository taskRepository;
    private final WorkbookRepository workbookRepository;

    private final Object syncObj = new Object();

    public TaskPersistencer(TaskRepository taskRepository, WorkbookRepository workbookRepository) {
        this.taskRepository = taskRepository;
        this.workbookRepository = workbookRepository;
    }

    public Task setParams(long taskId, String taskParams) {
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
                    if (task == null) {
                        log.warn("#307.01 Task with taskId {} wasn't found", taskId);
                        return null;
                    }
                    task.setParams(taskParams);
                    taskRepository.saveAndFlush(task);
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
                    taskRepository.saveAndFlush(task);
                    return Enums.UploadResourceStatus.OK;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.warn("#307.18 Error set resultReceived to {} try #{}, taskId: {}, error: {}", resultReceived, i, taskId, e.toString());
                }
            }
        }
        return Enums.UploadResourceStatus.PROBLEM_WITH_LOCKING;
    }

    @SuppressWarnings("unused")
    public Task assignTask(Task task, long stationId) {
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    task.setAssignedOn(System.currentTimeMillis());
                    task.setStationId(stationId);
                    task.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
                    task.setResultResourceScheduledOn(System.currentTimeMillis());

                    taskRepository.saveAndFlush((TaskImpl) task);
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error("#307.21 Error assign task {}, taskId: {}, error: {}", task.toString(), task.getId(), e.toString());
                }
            }
        }
        return null;
    }

    public Task resetTask(TaskImpl task) {
        log.info("Start resetting task #{}", task.getId());

        // TODO 2019-07-04 do we still need this synchronization?
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    task.setSnippetExecResults(null);
                    task.setStationId(null);
                    task.setAssignedOn(null);
                    task.setCompleted(false);
                    task.setCompletedOn(null);
                    task.setMetrics(null);
                    task.setExecState(EnumsApi.TaskExecState.NONE.value);
                    task.setResultReceived(false);
                    task.setResultResourceScheduledOn(0);
                    taskRepository.saveAndFlush(task);

                    return task;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error("#307.25 Error while resetting task, try: {}, taskId: {}, error: {}",  i, task.getId(), e.toString());
                }
            }
        }
        return null;
    }

    public Workbook save(Workbook workbook) {
        if (workbook instanceof WorkbookImpl) {
            return workbookRepository.saveAndFlush((WorkbookImpl)workbook);
        }
        else {
            throw new NotImplementedException("Need to implement");
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public Task storeExecResult(SimpleTaskExecResult result) {
        synchronized (syncObj) {
            SnippetApiData.SnippetExec snippetExec = SnippetExecUtils.to(result.getResult());
            if (!snippetExec.exec.isOk) {
                log.info("#307.27 Task #{} finished with error, console: {}",
                        result.taskId,
                        StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<console output is empty>");
            }
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    //noinspection UnnecessaryLocalVariable
                    Task t = prepareAndSaveTask(result, snippetExec.allSnippetsAreOk() ? EnumsApi.TaskExecState.OK : EnumsApi.TaskExecState.ERROR);
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
            // fake station Id
            task.setStationId(-1L);
            task.setExecState(EnumsApi.TaskExecState.BROKEN.value);
            task.setCompleted(true);
            task.setCompletedOn(System.currentTimeMillis());

            task.setSnippetExecResults("Task is broken, cant' process it");
            task.setResultReceived(true);

            //noinspection UnusedAssignment
            task = taskRepository.saveAndFlush(task);
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
            // TODO 2019.05.02 !!! add here statuses to tasks which are in chain after this one
            // TODO we have to stop processing workbook if there is error in tasks
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
        task = taskRepository.saveAndFlush(task);

        return task;
    }

}
