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

package ai.metaheuristic.ai.launchpad.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.launchpad.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.launchpad.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

    public List<Long> resourceReceivingChecker(long stationId) {
        List<Task> tasks = taskRepository.findForMissingResultResources(stationId, System.currentTimeMillis(), EnumsApi.TaskExecState.OK.value);
        return tasks.stream().map(Task::getId).collect(Collectors.toList());
    }

    public void processResendTaskOutputResourceResult(String stationId, Enums.ResendTaskOutputResourceStatus status, long taskId) {
        switch(status) {
            case SEND_SCHEDULED:
                log.info("#317.010 Station #{} scheduled the output resource of task #{} for sending. This is normal operation of sourceCode", stationId, taskId);
                break;
            case RESOURCE_NOT_FOUND:
            case TASK_IS_BROKEN:
            case TASK_PARAM_FILE_NOT_FOUND:
                TaskImpl task = taskRepository.findById(taskId).orElse(null);
                if (task==null) {
                    log.warn("#317.020 Task obsolete and was already deleted");
                    return;
                }
                taskPersistencer.finishTaskAsBrokenOrError(task.getId(), EnumsApi.TaskExecState.BROKEN);
                ExecContextOperationStatusWithTaskList execContextOperationStatusWithTaskList =
                        execContextGraphTopLevelService.updateGraphWithSettingAllChildrenTasksAsBroken(task.workbookId, task.id);
                if (execContextOperationStatusWithTaskList ==null) {
                    log.warn("#317.030 ExecContext for this task was already deleted");
                    return;
                }
                break;
            case OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE:
                Enums.UploadResourceStatus uploadResourceStatus = taskPersistencer.setResultReceived(taskId, true);
                if (uploadResourceStatus==Enums.UploadResourceStatus.OK) {
                    log.info("#317.040 the output resource of task #{} is stored on external storage which was defined by disk://. This is normal operation of sourceCode", taskId);
                }
                else {
                    log.info("#317.050 can't update isCompleted field for task #{}", taskId);
                }
                break;
        }
    }
}
