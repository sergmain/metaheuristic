package aiai.ai.launchpad.task;

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.experiment.task.SimpleTaskExecResult;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.yaml.snippet_exec.SnippetExec;
import aiai.ai.yaml.snippet_exec.SnippetExecUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Profile("launchpad")
public class TaskPersistencer {

    private static final int NUMBER_OF_TRY = 2;
    private final TaskRepository taskRepository;

    private final Object syncObj = new Object();

    public TaskPersistencer(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Task setParams(long taskId, String taskParams) {
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    Task task = taskRepository.findById(taskId).orElse(null);
                    if (task == null) {
                        log.warn("#307.01 Task with taskId {} wasn't found", taskParams, taskId);
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

    @Transactional
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
                    task.setResultReceived(value);
                    taskRepository.save(task);
                    return Enums.UploadResourceStatus.OK;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error("#307.18 Error set resultReceived to {}, taskId: {}, error: {}", value, taskId, e.toString());
                }
            }
        }
        return Enums.UploadResourceStatus.PROBLEM_WITH_OPTIMISTIC_LOCKING;
    }

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
                    log.error("#307.21 Error assign task {}, taskId: {}, error: {}", task.toString(), e.toString());
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
                    return task;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error("#307.25 Error while reseting task, taskId: {}, error: {}",  taskId, e.toString());
                }
            }
        }
        return null;
    }

    public Task markAsCompleted(SimpleTaskExecResult result) {
        synchronized (syncObj) {
            for (int i = 0; i < NUMBER_OF_TRY; i++) {
                try {
                    SnippetExec snippetExec = SnippetExecUtils.to(result.getResult());
                    Task t = prepareTask(result, snippetExec.exec.isOk ? Enums.TaskExecState.OK : Enums.TaskExecState.ERROR);
                    if (t!=null) {
                        taskRepository.save(t);
                    }
                    return t;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.error("#307.29 Error while storing result of execution of task, taskId: {}, error: {}", result.taskId, e.toString());
                }
            }
        }
        return null;
    }

    private Task prepareTask(SimpleTaskExecResult result, Enums.TaskExecState state) {
        Task task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("#307.33 Can't find Task for Id: {}", result.taskId);
            return null;
        }
        task.setExecState(state.value);
        task.setSnippetExecResults(result.getResult());
        task.setMetrics(result.getMetrics());
        task.setCompleted(true);
        task.setCompletedOn(System.currentTimeMillis());
        task.resultResourceScheduledOn = System.currentTimeMillis();
        return task;
    }

}
