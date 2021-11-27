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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.SetVariableReceivedTxEvent;
import ai.metaheuristic.ai.dispatcher.event.VariableUploadedTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.southbridge.UploadResult;
import ai.metaheuristic.ai.exceptions.VariableCommonException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 12/17/2020
 * Time: 8:37 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TaskVariableService {

    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final VariableRepository variableRepository;
    private final EventPublisherService eventPublisherService;

    public static class UpdateStatusOfVariableException extends RuntimeException {
        public final UploadResult uploadResult;

        public UpdateStatusOfVariableException(UploadResult uploadResult) {
            this.uploadResult = uploadResult;
        }
    }

    @Transactional
    public void setVariableAsNull(Long taskId, Long variableId) {
        Variable variable = variableRepository.findById(variableId).orElseThrow(()->new VariableCommonException("#441.120 Variable #"+variableId+" wasn't found", variableId));
        variable.inited = true;
        variable.nullified = true;
        variable.setData(null);

        eventPublisherService.publishSetVariableReceivedTxEvent(new SetVariableReceivedTxEvent(taskId, variableId, true));

        variableRepository.save(variable);
    }

    @Transactional
    public void updateStatusOfVariable(Long taskId, Long variableId, boolean nullified) {
        TaskSyncService.checkWriteLockPresent(taskId);

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            final String es = "#441.020 Task "+taskId+" is obsolete and was already deleted";
            log.warn(es);
            throw new UpdateStatusOfVariableException(new UploadResult(Enums.UploadVariableStatus.TASK_NOT_FOUND, es));
        }

        eventPublisherService.publishVariableUploadedTxEvent(new VariableUploadedTxEvent(task.execContextId, task.id, variableId, nullified));

        Enums.UploadVariableStatus status = setVariableReceived(task, variableId);

        if (status != Enums.UploadVariableStatus.OK) {
            throw new UpdateStatusOfVariableException(
                    new UploadResult(status, "#441.080 can't update resultReceived field for task #"+ taskId+", variable #" +variableId));
        }
    }

    private Enums.UploadVariableStatus setVariableReceived(TaskImpl task, Long variableId) {
        TxUtils.checkTxExists();

        if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
            log.warn("#441.180 Task {} was reset, can't set new value to field resultReceived", task.id);
            return Enums.UploadVariableStatus.TASK_WAS_RESET;
        }
        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
        TaskParamsYaml.OutputVariable output = tpy.task.outputs.stream().filter(o->o.id.equals(variableId)).findFirst().orElse(null);
        if (output==null) {
            return Enums.UploadVariableStatus.UNRECOVERABLE_ERROR;
        }
        output.uploaded = true;
        task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);
        TaskImpl t = taskService.save(task);

        return Enums.UploadVariableStatus.OK;
    }

}
