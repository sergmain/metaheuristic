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
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRED)
public class TaskPersistencer {

    private final TaskRepository taskRepository;
    private final TaskSyncService taskSyncService;

    @Nullable
    public TaskImpl setParams(Long taskId, TaskParamsYaml params) {
        return setParams(taskId, TaskParamsYamlUtils.BASE_YAML_UTILS.toString(params));
    }

    @Nullable
    public TaskImpl setParams(Long taskId, String taskParams) {
        return taskSyncService.getWithSync(taskId, (task) -> {
            try {
                if (task == null) {
                    log.warn("#307.010 Task with taskId {} wasn't found", taskId);
                    return null;
                }
                task.setParams(taskParams);
                taskRepository.save(task);
                return task;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.error("#307.020 !!!NEED TO INVESTIGATE. Error set setParams to {}, taskId: {}, error: {}", taskParams, taskId, e.toString());
            }
            return null;
        });
    }

    public Enums.UploadResourceStatus setResultReceived(Long taskId, Long variableId) {
        Enums.UploadResourceStatus status = taskSyncService.getWithSync(true, taskId, (task) -> {
            try {
                if (task == null) {
                    return Enums.UploadResourceStatus.TASK_NOT_FOUND;
                }
                if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
                    log.warn("#307.030 Task {} was reset, can't set new value to field resultReceived", taskId);
                    return Enums.UploadResourceStatus.TASK_WAS_RESET;
                }
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                TaskParamsYaml.OutputVariable output = tpy.task.outputs.stream().filter(o->o.id.equals(variableId)).findAny().orElse(null);
                if (output==null) {
                    return Enums.UploadResourceStatus.UNRECOVERABLE_ERROR;
                }
                output.uploaded = true;
                task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);

                boolean allUploaded = tpy.task.outputs.isEmpty() || tpy.task.outputs.stream().allMatch(o -> o.uploaded);
                task.setCompleted(allUploaded);
                task.setCompletedOn(System.currentTimeMillis());
                task.setResultReceived(allUploaded);
                taskRepository.save(task);
                return Enums.UploadResourceStatus.OK;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("#307.040 !!!NEED TO INVESTIGATE. Error set resultReceived() for taskId: {}, variableId: {}, error: {}", taskId, variableId, e.toString());
                log.warn("#307.060 ObjectOptimisticLockingFailureException", e);
                return Enums.UploadResourceStatus.PROBLEM_WITH_LOCKING;
            }
        });
        if (status==null) {
            return Enums.UploadResourceStatus.TASK_NOT_FOUND;
        }
        return status;
    }

    public Enums.UploadResourceStatus setResultReceivedForInternalFunction(Long taskId) {
        Enums.UploadResourceStatus status = taskSyncService.getWithSync(taskId, (task) -> {
            try {
                if (task == null) {
                    return Enums.UploadResourceStatus.TASK_NOT_FOUND;
                }
                if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
                    log.warn("#307.080 Task {} was reset, can't set new value to field resultReceived", taskId);
                    return Enums.UploadResourceStatus.TASK_WAS_RESET;
                }
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                tpy.task.outputs.forEach(o->o.uploaded = true);
                task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);
                task.setCompleted(true);
                task.setCompletedOn(System.currentTimeMillis());
                task.setResultReceived(true);
                taskRepository.save(task);
                return Enums.UploadResourceStatus.OK;
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("#307.100 !!!NEED TO INVESTIGATE. Error set setResultReceivedForInternalFunction() for taskId: {}, error: {}", taskId, e.toString());
                log.warn("#307.120 ObjectOptimisticLockingFailureException", e);
                return Enums.UploadResourceStatus.PROBLEM_WITH_LOCKING;
            }
        });
        if (status==null) {
            return Enums.UploadResourceStatus.TASK_NOT_FOUND;
        }
        return status;
    }

}
