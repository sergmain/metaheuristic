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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.event.CheckTaskCanBeFinishedEvent;
import ai.metaheuristic.ai.dispatcher.event.VariableUploadedEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.southbridge.UploadResult;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

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

    private static final UploadResult OK_UPLOAD_RESULT = new UploadResult(Enums.UploadVariableStatus.OK, null);

    private final TaskRepository taskRepository;
    private final ExecContextSyncService execContextSyncService;
    private final TaskService taskService;
    private final VariableRepository variableRepository;
    private final VariableService variableService;

    @Transactional
    public UploadResult storeVariable(InputStream variableIS, long length, Long execContextId, Long taskId, Long variableId) {
        Variable variable = variableRepository.findById(variableId).orElse(null);
        if (variable ==null) {
            return new UploadResult(Enums.UploadVariableStatus.VARIABLE_NOT_FOUND,"#441.040 Variable #"+variableId+" wasn't found" );
        }
        if (!execContextId.equals(variable.execContextId)) {
            final String es = "#441.060 Task #"+taskId+" has the different execContextId than variable #"+variableId+", " +
                    "task execContextId: "+execContextId+", var execContextId: "+variable.execContextId;
            log.warn(es);
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR, es);
        }

        variableService.update(variableIS, length, variable);
        return OK_UPLOAD_RESULT;
    }

    @Transactional
    public UploadResult updateStatusOfVariable(Long taskId, Long variableId, DataHolder holder) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            final String es = "#441.020 Task "+taskId+" is obsolete and was already deleted";
            log.warn(es);
            return new UploadResult(Enums.UploadVariableStatus.TASK_NOT_FOUND, es);
        }
        Enums.UploadVariableStatus status = setVariableReceived(task, variableId);
        if (status==Enums.UploadVariableStatus.OK) {
            holder.events.add(new CheckTaskCanBeFinishedEvent(task.execContextId, task.id, true));
            holder.events.add(new VariableUploadedEvent(task.execContextId, task.id, variableId, false));

            return OK_UPLOAD_RESULT;
        }
        else {
            return new UploadResult(status, "#441.080 can't update resultReceived field for task #"+ variableId+"");
        }
    }

    @Transactional
    public UploadResult setVariableAsNull(Long taskId, Long variableId, DataHolder holder) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            final String es = "#441.100 Task "+taskId+" is obsolete and was already deleted";
            log.warn(es);
            return new UploadResult(Enums.UploadVariableStatus.TASK_NOT_FOUND, es);
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        Variable variable = variableRepository.findById(variableId).orElse(null);
        if (variable ==null) {
            return new UploadResult(Enums.UploadVariableStatus.VARIABLE_NOT_FOUND,"#441.120 Variable #"+variableId+" wasn't found" );
        }
        if (!task.execContextId.equals(variable.execContextId)) {
            final String es = "#441.140 Task #"+taskId+" has the different execContextId than variable #"+task.id+", " +
                    "task execContextId: "+task.execContextId+", var execContextId: "+variable.execContextId;
            log.warn(es);
            return new UploadResult(Enums.UploadVariableStatus.UNRECOVERABLE_ERROR, es);
        }

        variable.inited = true;
        variable.nullified = true;
        variable.setData(null);

        Enums.UploadVariableStatus status = setVariableReceived(task, variable.getId());
        if (status==Enums.UploadVariableStatus.OK) {
            holder.events.add(new CheckTaskCanBeFinishedEvent(task.execContextId, task.id, true));
            holder.events.add(new VariableUploadedEvent(task.execContextId, task.id, variable.getId(), true));
            return OK_UPLOAD_RESULT;
        }
        else {
            return new UploadResult(status, "#441.160 can't update resultReceived field for task #"+ variable.getId()+"");
        }
    }

    public Enums.UploadVariableStatus setVariableReceived(TaskImpl task, Long variableId) {
        TxUtils.checkTxExists();
//        execContextSyncService.checkWriteLockPresent(task.execContextId);
        if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
            log.warn("#441.180 Task {} was reset, can't set new value to field resultReceived", task.id);
            return Enums.UploadVariableStatus.TASK_WAS_RESET;
        }
        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
        TaskParamsYaml.OutputVariable output = tpy.task.outputs.stream().filter(o->o.id.equals(variableId)).findAny().orElse(null);
        if (output==null) {
            return Enums.UploadVariableStatus.UNRECOVERABLE_ERROR;
        }
        output.uploaded = true;
        task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);
        TaskImpl t = taskService.save(task);

        return Enums.UploadVariableStatus.OK;
    }

    public Enums.UploadVariableStatus setResultReceivedForInternalFunction(Long taskId) {
        TxUtils.checkTxExists();

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#441.200.020 Task #{} is obsolete and was already deleted", taskId);
            return Enums.UploadVariableStatus.TASK_NOT_FOUND;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);

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
