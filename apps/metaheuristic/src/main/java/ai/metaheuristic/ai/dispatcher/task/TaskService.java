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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskPersistencer taskPersistencer;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

    public DispatcherCommParamsYaml.ResendTaskOutputs resourceReceivingChecker(long processorId) {
        List<Task> tasks = taskRepository.findForMissingResultResources(processorId, System.currentTimeMillis(), EnumsApi.TaskExecState.OK.value);
        DispatcherCommParamsYaml.ResendTaskOutputs result = new DispatcherCommParamsYaml.ResendTaskOutputs();
        for (Task task : tasks) {
            TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                result.resends.add( new DispatcherCommParamsYaml.ResendTaskOutput(task.getId(), output.id));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<TaskData.SimpleTaskInfo> getSimpleTaskInfos(Long execContextId) {
        try (Stream<TaskImpl> stream = taskRepository.findAllByExecContextIdAsStream(execContextId)) {
            return stream.map(o-> {
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(o.params);
                return new TaskData.SimpleTaskInfo(o.id,  EnumsApi.TaskExecState.from(o.execState).toString(), tpy.task.taskContextId, tpy.task.processCode, tpy.task.function.code);
            }).collect(Collectors.toList());
        }
    }

    public void processResendTaskOutputResourceResult(@Nullable String processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
        switch(status) {
            case SEND_SCHEDULED:
                log.info("#317.010 Processor #{} scheduled sending of output variables of task #{} for sending. This is normal operation of Processor", processorId, taskId);
                break;
            case RESOURCE_NOT_FOUND:
            case TASK_IS_BROKEN:
            case TASK_PARAM_FILE_NOT_FOUND:
                TaskImpl task = taskRepository.findById(taskId).orElse(null);
                if (task==null) {
                    log.warn("#317.020 Task obsolete and was already deleted");
                    return;
                }
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

                taskPersistencer.finishTaskAsError(task.getId(), EnumsApi.TaskExecState.ERROR);
                ExecContextOperationStatusWithTaskList execContextOperationStatusWithTaskList =
                        execContextGraphTopLevelService.updateGraphWithSettingAllChildrenTasksAsSkipped(task.execContextId, tpy.task.taskContextId, task.id);
                if (execContextOperationStatusWithTaskList ==null) {
                    log.warn("#317.030 ExecContext for this task was already deleted");
                    return;
                }
                break;
            case OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE:
                Enums.UploadResourceStatus uploadResourceStatus = taskPersistencer.setResultReceived(taskId, variableId);
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
