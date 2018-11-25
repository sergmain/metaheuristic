package aiai.ai.launchpad.task;

import aiai.ai.Enums;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.experiment.SimpleTaskExecResult;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskService {

    private static final TasksAndAssignToStationResult EMPTY_RESULT = new TasksAndAssignToStationResult(null);

    private final TaskRepository taskRepository;
    private final FlowInstanceRepository flowInstanceRepository;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final SnippetCache snippetCache;

    public List<Long> resourceReceivingChecker(long stationId) {
        List<Task> tasks = taskRepository.findForMissingResultResources(stationId, System.currentTimeMillis(), Enums.TaskExecState.OK.value);
        return tasks.stream().map(Task::getId).collect(Collectors.toList());
    }

    public void processResendTaskOutputResourceResult(Enums.ResendTaskOutputResourceStatus status, long taskId) {
        switch(status) {
            case SEND_SCHEDULED:
                log.info("output resource was scheduled for re-sending");
                break;
            case RESOURCE_NOT_FOUND:
            case TASK_IS_BROKEN:
            case TASK_PARAM_FILE_NOT_FOUND:
                Task task = taskRepository.findById(taskId).orElse(null);
                if (task==null) {
                    log.warn("Task obsolete and was already deleted");
                    return;
                }
                FlowInstance flowInstance = flowInstanceRepository.findById(task.flowInstanceId).orElse(null);
                if (flowInstance==null) {
                    log.warn("FlowInstance for this task was already deleted");
                    return;
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

                flowInstance.setProducingOrder(task.order - 1);
                flowInstanceRepository.save(flowInstance);
                break;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TasksAndAssignToStationResult {
        Protocol.AssignedTask.Task simpleTask;
    }

    public TaskService(TaskRepository taskRepository, FlowInstanceRepository flowInstanceRepository, TaskParamYamlUtils taskParamYamlUtils, SnippetCache snippetCache) {
        this.taskRepository = taskRepository;
        this.flowInstanceRepository = flowInstanceRepository;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.snippetCache = snippetCache;
    }

    public List<Long> storeAllResults(List<SimpleTaskExecResult> results) {
        List<Task> list = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(result.getResult());
            Task t = prepareTask(result, snippetExec.exec.isOk ? Enums.TaskExecState.OK : Enums.TaskExecState.ERROR);
            if (t!=null) {
                list.add(t);
            }
        }
        taskRepository.saveAll(list);
        return ids;
    }

    public void markAsCompleted(SimpleTaskExecResult result) {
        SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(result.getResult());
        Task t = prepareTask(result, snippetExec.exec.isOk ? Enums.TaskExecState.OK : Enums.TaskExecState.ERROR);
        if (t!=null) {
            taskRepository.save(t);
        }
    }

    private Task prepareTask(SimpleTaskExecResult result, Enums.TaskExecState state) {
        Task task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("Can't find Task for Id: {}", result.taskId);
            return null;
        }
        task.setExecState(state.value);
        task.setSnippetExecResults(result.getResult());
        task.setMetrics(result.getMetrics());
        task.setCompleted(true);
        task.setCompletedOn(System.currentTimeMillis());
        task.resultResourceScheduledOn = (state== Enums.TaskExecState.OK ? System.currentTimeMillis() : 0);
        return task;
    }

    public synchronized TasksAndAssignToStationResult getTaskAndAssignToStation(long stationId, boolean isAcceptOnlySigned, Long flowInstanceId) {

        List<FlowInstance> flowInstances;
        if (flowInstanceId==null) {
            flowInstances = flowInstanceRepository.findByExecStateOrderByCreatedOnAsc(
                    Enums.FlowInstanceExecState.STARTED.code);
        }
        else {
            FlowInstance flowInstance = flowInstanceRepository.findById(flowInstanceId).orElse(null);
            if (flowInstance==null) {
                log.warn("Flow instance wasn't found for id: {}", flowInstanceId);
                return EMPTY_RESULT;
            }
            if (flowInstance.execState!=Enums.FlowInstanceExecState.STARTED.code) {
                log.warn("Flow instance wasn't started.Current exec state: {}", Enums.FlowInstanceExecState.toState(flowInstance.execState));
                return EMPTY_RESULT;
            }
            flowInstances = Collections.singletonList(flowInstance);
        }

        for (FlowInstance flowInstance : flowInstances) {
            TasksAndAssignToStationResult result = findUnassignedTaskAndAssign(flowInstance, stationId, isAcceptOnlySigned);
            if (!result.equals(EMPTY_RESULT)) {
                return result;
            }
        }
        return EMPTY_RESULT;
    }

    private TasksAndAssignToStationResult findUnassignedTaskAndAssign(FlowInstance flowInstance, long stationId, boolean isAcceptOnlySigned) {

        List<Task> tasks = taskRepository.findForAssigning(flowInstance.getId(), flowInstance.producingOrder);
        if (currentLevelIsntFinished(tasks, flowInstance.producingOrder)) {
            return EMPTY_RESULT;
        }
        Task resultTask = null;
        for (Task task : tasks) {
            if (!task.isCompleted) {
                if (isAcceptOnlySigned) {
                    final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());

                    SnippetVersion version = SnippetVersion.from(taskParamYaml.snippet.getCode());
                    Snippet snippet = snippetCache.findByNameAndSnippetVersion(version.name, version.version);
                    if (snippet==null) {
                        log.warn("Can't find snippet for code: {}, SnippetVersion: {}", taskParamYaml.snippet.getCode(), version);
                        continue;
                    }

                    if (!snippet.isSigned()) {
                        continue;
                    }
                    resultTask = task;
                    break;
                }
                else {
                    resultTask = task;
                    break;
                }
            }
        }
        if (resultTask==null) {
            return EMPTY_RESULT;
        }
        Protocol.AssignedTask.Task assignedTask = new Protocol.AssignedTask.Task();
        assignedTask.setTaskId(resultTask.getId());
        assignedTask.setFlowInstanceId(flowInstance.getId());
        assignedTask.setParams(resultTask.getParams());

        resultTask.setAssignedOn(System.currentTimeMillis());
        resultTask.setStationId(stationId);
        resultTask.setExecState(Enums.TaskExecState.IN_PROGRESS.value);
        resultTask.resultResourceScheduledOn = 0;

        taskRepository.save(resultTask);

        return new TasksAndAssignToStationResult(assignedTask);
    }

    private boolean currentLevelIsntFinished(List<Task> tasks, int completedOrder) {
        for (Task task : tasks) {
            if (task.getOrder()==completedOrder && !task.isCompleted) {
                log.error("!!!!!!!!! Not completed task was found {}", task);
                return true;
            }
        }
        return false;
    }



}
