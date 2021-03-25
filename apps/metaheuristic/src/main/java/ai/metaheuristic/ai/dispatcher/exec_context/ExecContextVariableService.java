/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 10/3/2020
 * Time: 10:13 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextVariableService {

    private final TaskRepository taskRepository;
    private final ExecContextSyncService execContextSyncService;
    private final TaskService taskService;

    public Enums.UploadVariableStatus setResultReceivedForInternalFunction(Long taskId) {
        TxUtils.checkTxExists();

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#441.200.020 Task #{} is obsolete and was already deleted", taskId);
            return Enums.UploadVariableStatus.TASK_NOT_FOUND;
        }
//        execContextSyncService.checkWriteLockPresent(task.execContextId);

        if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
            log.warn("#441.220 Task {} was reset, can't set new value to field resultReceived", taskId);
            return Enums.UploadVariableStatus.TASK_WAS_RESET;
        }
        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
        tpy.task.outputs.forEach(o->o.uploaded = true);
        task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);
        task.setCompleted(true);
        task.setCompletedOn(System.currentTimeMillis());
        task.setResultReceived(true);
        taskService.save(task);
        return Enums.UploadVariableStatus.OK;
    }


}
