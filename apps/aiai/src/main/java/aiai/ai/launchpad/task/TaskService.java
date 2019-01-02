package aiai.ai.launchpad.task;

import aiai.ai.Enums;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.experiment.task.SimpleTaskExecResult;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("launchpad")
public class TaskService {

    private static final TasksAndAssignToStationResult EMPTY_RESULT = new TasksAndAssignToStationResult(null);

    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final FlowInstanceRepository flowInstanceRepository;
    private final TaskParamYamlUtils taskParamYamlUtils;
    private final SnippetRepository snippetRepository;

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
                    log.warn("#317.01 Task obsolete and was already deleted");
                    return;
                }
                FlowInstance flowInstance = flowInstanceRepository.findById(task.flowInstanceId).orElse(null);
                if (flowInstance==null) {
                    log.warn("#317.04 FlowInstance for this task was already deleted");
                    return;
                }

                Task result = taskPersistencer.resetTask(task.getId());
                if (result==null) {
                    log.error("#317.07 Reset of task {} was failed. See log for more info.", task.getId());
                    break;
                }

                flowInstance.setProducingOrder(task.order);
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

    public TaskService(TaskRepository taskRepository, TaskPersistencer taskPersistencer, FlowInstanceRepository flowInstanceRepository, TaskParamYamlUtils taskParamYamlUtils, SnippetRepository snippetRepository) {
        this.taskRepository = taskRepository;
        this.taskPersistencer = taskPersistencer;
        this.flowInstanceRepository = flowInstanceRepository;
        this.taskParamYamlUtils = taskParamYamlUtils;
        this.snippetRepository = snippetRepository;
    }

    public List<Long> storeAllConsoleResults(List<SimpleTaskExecResult> results) {
        List<Long> ids = new ArrayList<>();
        for (SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            taskPersistencer.markAsCompleted(result);
        }
        return ids;
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
                log.warn("#317.11 Flow instance wasn't found for id: {}", flowInstanceId);
                return EMPTY_RESULT;
            }
            if (flowInstance.execState!=Enums.FlowInstanceExecState.STARTED.code) {
                log.warn("#317.16 Flow instance wasn't started.Current exec state: {}", Enums.FlowInstanceExecState.toState(flowInstance.execState));
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

        int page = 0;
        Task resultTask = null;
        Slice<Task> tasks;
        while ((tasks=taskRepository.findForAssigning(PageRequest.of(page++, 20), flowInstance.getId(), flowInstance.producingOrder)).hasContent()) {
            for (Task task : tasks) {
                if (isAcceptOnlySigned) {
                    final TaskParamYaml taskParamYaml = taskParamYamlUtils.toTaskYaml(task.getParams());

                    SnippetVersion version = SnippetVersion.from(taskParamYaml.snippet.getCode());
                    if (version == null) {
                        log.warn("#317.23 Can't find snippet for code: {}, SnippetVersion: {}", taskParamYaml.snippet.getCode(), version);
                        continue;
                    }
                    Snippet snippet = snippetRepository.findByNameAndSnippetVersion(version.name, version.version);
                    if (snippet == null) {
                        log.warn("#317.26 Can't find snippet for code: {}, SnippetVersion: {}", taskParamYaml.snippet.getCode(), version);
                        continue;
                    }

                    if (!snippet.isSigned()) {
                        log.warn("#317.31 Snippet with code {} wasn't signed", taskParamYaml.snippet.getCode());
                        continue;
                    }
                    resultTask = task;
                    break;
                } else {
                    resultTask = task;
                    break;
                }
            }
            if (resultTask!=null) {
                break;
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


}
