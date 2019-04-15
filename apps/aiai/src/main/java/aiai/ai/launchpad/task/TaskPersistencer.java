/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.task;

import aiai.ai.Enums;
import aiai.ai.exceptions.ResourceProviderException;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.experiment.task.SimpleTaskExecResult;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.resource.ResourceUtils;
import aiai.ai.yaml.snippet_exec.SnippetExec;
import aiai.ai.yaml.snippet_exec.SnippetExecUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import lombok.extern.slf4j.Slf4j;
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
    private final FlowInstanceRepository flowInstanceRepository;

    private final Object syncObj = new Object();

    public TaskPersistencer(TaskRepository taskRepository, FlowInstanceRepository flowInstanceRepository) {
        this.taskRepository = taskRepository;
        this.flowInstanceRepository = flowInstanceRepository;
    }

    public Task setParams(long taskId, String taskParams) {
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    Task task = taskRepository.findById(taskId).orElse(null);
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

    public Enums.UploadResourceStatus setResultReceived(long taskId, boolean value) {
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    Task task = taskRepository.findById(taskId).orElse(null);
                    if (task == null) {
                        return Enums.UploadResourceStatus.TASK_NOT_FOUND;
                    }
                    if (task.execState == Enums.TaskExecState.NONE.value) {
                        log.warn("#307.12 Task {} was reset, can't set new value to field resultReceived", taskId);
                        return Enums.UploadResourceStatus.TASK_WAS_RESET;
                    }
                    task.setCompleted(true);
                    task.setCompletedOn(System.currentTimeMillis());
                    task.setResultReceived(value);
                    taskRepository.save(task);
                    return Enums.UploadResourceStatus.OK;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.warn("#307.18 Error set resultReceived to {} try #{}, taskId: {}, error: {}", value, i, taskId, e.toString());
                }
            }
        }
        return Enums.UploadResourceStatus.PROBLEM_WITH_OPTIMISTIC_LOCKING;
    }

    @SuppressWarnings("unused")
    public Task assignTask(Task task, long stationId) {
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    task.setAssignedOn(System.currentTimeMillis());
                    task.setStationId(stationId);
                    task.setExecState(Enums.TaskExecState.IN_PROGRESS.value);
                    task.resultResourceScheduledOn = System.currentTimeMillis();

                    taskRepository.save(task);
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error("#307.21 Error assign task {}, taskId: {}, error: {}", task.toString(), task.id, e.toString());
                }
            }
        }
        return null;
    }

    public Task resetTask(long taskId) {
        log.info("Start resetting task #{}", taskId);
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    Task task = taskRepository.findById(taskId).orElse(null);
                    if (task == null) {
                        return null;
                    }
                    task.snippetExecResults = null;
                    task.stationId = null;
                    task.assignedOn = null;
                    task.isCompleted = false;
                    task.completedOn = null;
                    task.metrics = null;
                    task.execState = Enums.TaskExecState.NONE.value;
                    task.resultReceived = false;
                    task.resultResourceScheduledOn = 0;
                    taskRepository.save(task);

                    FlowInstance flowInstance = flowInstanceRepository.findById(task.flowInstanceId).orElse(null);
                    if (flowInstance != null) {
                        if (task.order < flowInstance.producingOrder ||
                                Enums.FlowInstanceExecState.toState(flowInstance.getExecState()) == Enums.FlowInstanceExecState.FINISHED) {
                            flowInstance.producingOrder = task.order;
                            flowInstance.execState = Enums.FlowInstanceExecState.STARTED.code;
                            flowInstanceRepository.save(flowInstance);
                        }
                    }
                    else {
                        log.warn("#307.24 FlowInstance #{} wasn't found", task.flowInstanceId);
                    }
                    return task;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error("#307.25 Error while resetting task, taskId: {}, error: {}",  taskId, e.toString());
                }
            }
        }
        return null;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Task storeExecResult(SimpleTaskExecResult result) {
        synchronized (syncObj) {
            SnippetExec snippetExec = SnippetExecUtils.to(result.getResult());
            if (!snippetExec.exec.isOk) {
                log.info("#307.27 Task #{} finished with error, console: {}",
                        result.taskId,
                        StringUtils.isNotBlank(snippetExec.exec.console) ? snippetExec.exec.console : "<console output is empty>");
            }
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    //noinspection UnnecessaryLocalVariable
                    Task t = prepareAndSaveTask(result, snippetExec.exec.isOk ? Enums.TaskExecState.OK : Enums.TaskExecState.ERROR);
                    return t;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error("#307.29 Error while storing result of execution of task, taskId: {}, error: {}", result.taskId, e.toString());
                }
            }
        }
        return null;
    }

    private Task prepareAndSaveTask(SimpleTaskExecResult result, Enums.TaskExecState state) {
        Task task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("#307.33 Can't find Task for Id: {}", result.taskId);
            return null;
        }
        task.setExecState(state.value);

        TaskParamYaml yaml = TaskParamYamlUtils.toTaskYaml(task.params);
        final String storageUrl = yaml.resourceStorageUrls.get(yaml.outputResourceCode);
        boolean isError = false;
        Enums.StorageType storageType = null;
        if (storageUrl == null || storageUrl.isBlank()) {
            log.error("#307.42 storageUrl wasn't found for outputResourceCode {}", yaml.outputResourceCode);
            isError = true;
        }
        else {
            try {
                storageType = ResourceUtils.getStorageType(storageUrl);
            } catch (ResourceProviderException e) {
                isError = true;
                log.error("#307.53 storageUrl wasn't found for outputResourceCode {}", yaml.outputResourceCode);
            }
        }
        if (isError || state==Enums.TaskExecState.ERROR) {
            task.setCompleted(true);
            task.setCompletedOn(System.currentTimeMillis());
            // TODO !!! add here statuses to tasks which are in chain after this one
            // TODO we have to stop processing flow if there any error in tasks
        }
        else if (storageType== Enums.StorageType.disk) {
            task.setCompleted(true);
            task.setCompletedOn(System.currentTimeMillis());
        }

        task.setSnippetExecResults(result.getResult());
        task.setMetrics(result.getMetrics());
        task.resultResourceScheduledOn = System.currentTimeMillis();
        task = taskRepository.save(task);

        return task;
    }

}
