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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.api.v1.data.TaskApiData;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.experiment.task.SimpleTaskExecResult;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.repositories.StationsRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.holders.LongHolder;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import ai.metaheuristic.ai.yaml.task.TaskParamYamlUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.launchpad.Task;
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
    private final WorkbookRepository workbookRepository;
    private final StationsRepository stationsRepository;

    public List<Long> resourceReceivingChecker(long stationId) {
        List<Task> tasks = taskRepository.findForMissingResultResources(stationId, System.currentTimeMillis(), EnumsApi.TaskExecState.OK.value);
        return tasks.stream().map(Task::getId).collect(Collectors.toList());
    }

    public void processResendTaskOutputResourceResult(String stationId, Enums.ResendTaskOutputResourceStatus status, long taskId) {
        switch(status) {
            case SEND_SCHEDULED:
                log.info("#317.01 Station #{} scheduled the output resource of task #{} for sending. This is normal operation of plan", stationId, taskId);
                break;
            case RESOURCE_NOT_FOUND:
            case TASK_IS_BROKEN:
            case TASK_PARAM_FILE_NOT_FOUND:
                TaskImpl task = taskRepository.findById(taskId).orElse(null);
                if (task==null) {
                    log.warn("#317.05 Task obsolete and was already deleted");
                    return;
                }
                WorkbookImpl workbook = workbookRepository.findById(task.workbookId).orElse(null);
                if (workbook==null) {
                    log.warn("#317.11 Workbook for this task was already deleted");
                    return;
                }

                log.info("#317.17 Task #{} has to be reset, ResendTaskOutputResourceStatus: {}", task.getId(), status );
                Task result = taskPersistencer.resetTask(task.getId());
                if (result==null) {
                    log.error("#317.22 Reset of task {} was failed. See log for more info.", task.getId());
                    break;
                }

                if (task.order<workbook.getProducingOrder()) {
                    workbook.setProducingOrder(task.order);
                    workbookRepository.save(workbook);
                }
                break;
            case OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE:
                Enums.UploadResourceStatus uploadResourceStatus = taskPersistencer.setResultReceived(taskId, true);
                if (uploadResourceStatus==Enums.UploadResourceStatus.OK) {
                    log.info("#317.28 the output resource of task #{} is stored on external storage which was defined by disk://. This is normal operation of plan", taskId);
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

    public TaskService(TaskRepository taskRepository, TaskPersistencer taskPersistencer, WorkbookRepository workbookRepository, StationsRepository stationsRepository) {
        this.taskRepository = taskRepository;
        this.taskPersistencer = taskPersistencer;
        this.workbookRepository = workbookRepository;
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

    public synchronized TasksAndAssignToStationResult getTaskAndAssignToStation(long stationId, boolean isAcceptOnlySigned, Long workbookId) {

        List<Long> anyTaskId = taskRepository.findAnyActiveForStationId(Consts.PAGE_REQUEST_1_REC, stationId);
        if (!anyTaskId.isEmpty()) {
            // this station already has active task
            log.info("#317.34 can't assign any new task to station #{} because this station has active task #{}", stationId, anyTaskId);
            return EMPTY_RESULT;
        }

        List<Workbook> workbooks;
        if (workbookId==null) {
            workbooks = workbookRepository.findByExecStateOrderByCreatedOnAsc(
                    EnumsApi.WorkbookExecState.STARTED.code);
        }
        else {
            Workbook workbook = workbookRepository.findById(workbookId).orElse(null);
            if (workbook==null) {
                log.warn("#317.39 Workbook wasn't found for id: {}", workbookId);
                return EMPTY_RESULT;
            }
            if (workbook.getExecState()!= EnumsApi.WorkbookExecState.STARTED.code) {
                log.warn("#317.42 Workbook wasn't started. Current exec state: {}", EnumsApi.WorkbookExecState.toState(workbook.getExecState()));
                return EMPTY_RESULT;
            }
            workbooks = Collections.singletonList(workbook);
        }

        Station station = stationsRepository.findById(stationId).orElse(null);
        if (station==null) {
            log.error("#317.47 Station wasn't found for id: {}", stationId);
            return EMPTY_RESULT;
        }

        for (Workbook workbook : workbooks) {
            TasksAndAssignToStationResult result = findUnassignedTaskAndAssign(workbook, station, isAcceptOnlySigned);
            if (!result.equals(EMPTY_RESULT)) {
                return result;
            }
        }
        return EMPTY_RESULT;
    }

    private final Map<Long, LongHolder> bannedSince = new HashMap<>();

    private TasksAndAssignToStationResult findUnassignedTaskAndAssign(Workbook workbook, Station station, boolean isAcceptOnlySigned) {

        LongHolder longHolder = bannedSince.computeIfAbsent(station.getId(), o -> new LongHolder(0));
        if (longHolder.value!=0 && System.currentTimeMillis() - longHolder.value < TimeUnit.MINUTES.toMillis(30)) {
            return EMPTY_RESULT;
        }

        int page = 0;
        Task resultTask = null;
        Slice<Task> tasks;
        while ((tasks=taskRepository.findForAssigning(PageRequest.of(page++, 20), workbook.getId(), workbook.getProducingOrder())).hasContent()) {
            for (Task task : tasks) {
                final TaskApiData.TaskParamYaml taskParamYaml;
                try {
                    taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());
                } catch (Exception e) {
                    throw new RuntimeException("#317.59 Error", e);
                }

                StationStatus stationStatus = StationStatusUtils.to(station.status);
                if (taskParamYaml.snippet.sourcing== EnumsApi.SnippetSourcing.git &&
                        stationStatus.gitStatusInfo.status!= Enums.GitStatus.installed) {
                    log.warn("#317.62 Can't assign task #{} to station #{} because this station doesn't correctly installed git, git status info: {}",
                            station.getId(), task.getId(), stationStatus.gitStatusInfo
                    );
                    longHolder.value = System.currentTimeMillis();
                    continue;
                }

                if (taskParamYaml.snippet.env!=null) {
                    String interpreter = stationStatus.env.getEnvs().get(taskParamYaml.snippet.env);
                    if (interpreter == null) {
                        log.warn("#317.64 Can't assign task #{} to station #{} because this station doesn't have defined interpreter for snippet's env {}",
                                station.getId(), task.getId(), taskParamYaml.snippet.env
                        );
                        longHolder.value = System.currentTimeMillis();
                        continue;
                    }
                }

                if (isAcceptOnlySigned) {
                    if (!taskParamYaml.snippet.info.isSigned()) {
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
        assignedTask.setWorkbookId(workbook.getId());
        assignedTask.setParams(resultTask.getParams());

        resultTask.setAssignedOn(System.currentTimeMillis());
        resultTask.setStationId(station.getId());
        resultTask.setExecState(EnumsApi.TaskExecState.IN_PROGRESS.value);
        resultTask.setResultResourceScheduledOn(0);

        taskRepository.save((TaskImpl)resultTask);

        return new TasksAndAssignToStationResult(assignedTask);
    }

}
