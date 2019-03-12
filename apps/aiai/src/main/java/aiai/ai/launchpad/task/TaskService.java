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

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.beans.Station;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.experiment.task.SimpleTaskExecResult;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.launchpad.repositories.StationsRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.utils.holders.LongHolder;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
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

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("launchpad")
public class TaskService {

    private static final TasksAndAssignToStationResult EMPTY_RESULT = new TasksAndAssignToStationResult(null);

    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final FlowInstanceRepository flowInstanceRepository;
    private final SnippetRepository snippetRepository;
    private final StationsRepository stationsRepository;

    public List<Long> resourceReceivingChecker(long stationId) {
        List<Task> tasks = taskRepository.findForMissingResultResources(stationId, System.currentTimeMillis(), Enums.TaskExecState.OK.value);
        return tasks.stream().map(Task::getId).collect(Collectors.toList());
    }

    public void processResendTaskOutputResourceResult(String stationId, Enums.ResendTaskOutputResourceStatus status, long taskId) {
        switch(status) {
            case SEND_SCHEDULED:
                log.info("#317.01 Station #{} scheduled the output resource of task #{} for sending. This is normal operation of flow", stationId, taskId);
                break;
            case RESOURCE_NOT_FOUND:
            case TASK_IS_BROKEN:
            case TASK_PARAM_FILE_NOT_FOUND:
                Task task = taskRepository.findById(taskId).orElse(null);
                if (task==null) {
                    log.warn("#317.05 Task obsolete and was already deleted");
                    return;
                }
                FlowInstance flowInstance = flowInstanceRepository.findById(task.flowInstanceId).orElse(null);
                if (flowInstance==null) {
                    log.warn("#317.11 FlowInstance for this task was already deleted");
                    return;
                }

                log.info("#317.17 Task #{} has to be reset, ResendTaskOutputResourceStatus: {}", task.getId(), status );
                Task result = taskPersistencer.resetTask(task.getId());
                if (result==null) {
                    log.error("#317.22 Reset of task {} was failed. See log for more info.", task.getId());
                    break;
                }

                if (task.order<flowInstance.producingOrder) {
                    flowInstance.setProducingOrder(task.order);
                    flowInstanceRepository.save(flowInstance);
                }
                break;
            case OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE:
                Enums.UploadResourceStatus uploadResourceStatus = taskPersistencer.setResultReceived(taskId, true);
                if (uploadResourceStatus==Enums.UploadResourceStatus.OK) {
                    log.info("#317.28 the output resource of task #{} is stored on external storage which was defined by disk://. This is normal operation of flow", taskId);
                }
                else {
                    log.info("#317.30 can't update isCompleted field for task #{}", taskId);
                }
                break;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TasksAndAssignToStationResult {
        Protocol.AssignedTask.Task simpleTask;
    }

    public TaskService(TaskRepository taskRepository, TaskPersistencer taskPersistencer, FlowInstanceRepository flowInstanceRepository, SnippetRepository snippetRepository, StationsRepository stationsRepository) {
        this.taskRepository = taskRepository;
        this.taskPersistencer = taskPersistencer;
        this.flowInstanceRepository = flowInstanceRepository;
        this.snippetRepository = snippetRepository;
        this.stationsRepository = stationsRepository;
    }

    public List<Long> storeAllConsoleResults(List<SimpleTaskExecResult> results) {
        List<Long> ids = new ArrayList<>();
        for (SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            taskPersistencer.storeExecResult(result);
        }
        return ids;
    }

    public synchronized TasksAndAssignToStationResult getTaskAndAssignToStation(long stationId, boolean isAcceptOnlySigned, Long flowInstanceId) {

        List<Long> anyTaskId = taskRepository.findAnyActiveForStationId(Consts.PAGE_REQUEST_1_REC, stationId);
        if (!anyTaskId.isEmpty()) {
            // this station already has active task
            log.info("#317.34 can't assign any new task to station #{} because this station has active task #{}", stationId, anyTaskId);
            return EMPTY_RESULT;
        }

        List<FlowInstance> flowInstances;
        if (flowInstanceId==null) {
            flowInstances = flowInstanceRepository.findByExecStateOrderByCreatedOnAsc(
                    Enums.FlowInstanceExecState.STARTED.code);
        }
        else {
            FlowInstance flowInstance = flowInstanceRepository.findById(flowInstanceId).orElse(null);
            if (flowInstance==null) {
                log.warn("#317.39 Flow instance wasn't found for id: {}", flowInstanceId);
                return EMPTY_RESULT;
            }
            if (flowInstance.execState!=Enums.FlowInstanceExecState.STARTED.code) {
                log.warn("#317.42 Flow instance wasn't started. Current exec state: {}", Enums.FlowInstanceExecState.toState(flowInstance.execState));
                return EMPTY_RESULT;
            }
            flowInstances = Collections.singletonList(flowInstance);
        }

        Station station = stationsRepository.findById(stationId).orElse(null);
        if (station==null) {
            log.error("#317.47 Station wasn't found for id: {}", stationId);
            return EMPTY_RESULT;
        }

        for (FlowInstance flowInstance : flowInstances) {
            TasksAndAssignToStationResult result = findUnassignedTaskAndAssign(flowInstance, station, isAcceptOnlySigned);
            if (!result.equals(EMPTY_RESULT)) {
                return result;
            }
        }
        return EMPTY_RESULT;
    }

    private final Map<Long, LongHolder> bannedSince = new HashMap<>();

    private TasksAndAssignToStationResult findUnassignedTaskAndAssign(FlowInstance flowInstance, Station station, boolean isAcceptOnlySigned) {

        LongHolder longHolder = bannedSince.computeIfAbsent(station.getId(), o -> new LongHolder(0));
        if (longHolder.value!=0 && System.currentTimeMillis() - longHolder.value < TimeUnit.MINUTES.toMillis(30)) {
            return EMPTY_RESULT;
        }

        int page = 0;
        Task resultTask = null;
        Slice<Task> tasks;
        while ((tasks=taskRepository.findForAssigning(PageRequest.of(page++, 20), flowInstance.getId(), flowInstance.producingOrder)).hasContent()) {
            for (Task task : tasks) {
                final TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());

                SnippetVersion version = SnippetVersion.from(taskParamYaml.snippet.getCode());
                if (version == null) {
                    log.warn("#317.53 Can't find snippet for code: {}, SnippetVersion: {}", taskParamYaml.snippet.getCode(), version);
                    continue;
                }
                Snippet snippet = snippetRepository.findByNameAndSnippetVersion(version.name, version.version);
                if (snippet == null) {
                    log.warn("#317.59 Can't find snippet for code: {}, snippetVersion: {}", taskParamYaml.snippet.getCode(), version);
                    continue;
                }

                EnvYaml envYaml = EnvYamlUtils.to(station.getEnv());
                String interpreter = envYaml.getEnvs().get(snippet.env);
                if (interpreter==null) {
                    log.warn("#317.64 Can't assign task #{} to station #{} because this station doesn't have defined interpreter for snippet's env {}",
                            station.getId(), task.getId(), snippet.env
                    );
                    longHolder.value = System.currentTimeMillis();
                    continue;
                }

                if (isAcceptOnlySigned) {
                    if (!snippet.isSigned()) {
                        log.warn("#317.69 Snippet with code {} wasn't signed", taskParamYaml.snippet.getCode());
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

        // normal way of operation
        longHolder.value = 0;

        Protocol.AssignedTask.Task assignedTask = new Protocol.AssignedTask.Task();
        assignedTask.setTaskId(resultTask.getId());
        assignedTask.setFlowInstanceId(flowInstance.getId());
        assignedTask.setParams(resultTask.getParams());

        resultTask.setAssignedOn(System.currentTimeMillis());
        resultTask.setStationId(station.getId());
        resultTask.setExecState(Enums.TaskExecState.IN_PROGRESS.value);
        resultTask.resultResourceScheduledOn = 0;

        taskRepository.save(resultTask);

        return new TasksAndAssignToStationResult(assignedTask);
    }

}
