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

    private static final UploadResult OK_UPLOAD_RESULT = new UploadResult(Enums.UploadResourceStatus.OK, null);

    private final TaskRepository taskRepository;
    private final ExecContextSyncService execContextSyncService;
    private final TaskService taskService;
    private final VariableRepository variableRepository;
    private final VariableService variableService;
    private final ExecContextTaskFinishingService execContextTaskFinishingService;

    @Transactional
    public UploadResult storeVariable(InputStream variableIS, long length, Long taskId, Long variableId) {
//        try {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            final String es = "#440.005 Task "+taskId+" is obsolete and was already deleted";
            log.warn(es);
            return new UploadResult(Enums.UploadResourceStatus.TASK_NOT_FOUND, es);
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        Variable variable = variableRepository.findById(variableId).orElse(null);
        if (variable ==null) {
            return new UploadResult(Enums.UploadResourceStatus.VARIABLE_NOT_FOUND,"#440.030 Variable #"+variableId+" wasn't found" );
        }

        variableService.update(variableIS, length, variable);
        Enums.UploadResourceStatus status = setVariableReceived(task, variable.getId());
        execContextTaskFinishingService.checkTaskCanBeFinished(task.id);

        return status== Enums.UploadResourceStatus.OK
                ? OK_UPLOAD_RESULT
                : new UploadResult(status, "#490.020 can't update resultReceived field for task #"+ variable.getId()+"");

//        }
//        catch (PessimisticLockingFailureException th) {
//            final String es = "#490.040 can't store the result, need to try again. Error: " + th.toString();
//            log.error(es, th);
//            return new UploadResult(Enums.UploadResourceStatus.PROBLEM_WITH_LOCKING, es);
//        }
//        catch (Throwable th) {
//            final String error = "#490.060 can't store the result, Error: " + th.toString();
//            log.error(error, th);
//            return new UploadResult(Enums.UploadResourceStatus.GENERAL_ERROR, error);
//        }
    }

    @Transactional
    public Enums.UploadResourceStatus setVariableReceived(TaskImpl task, Long variableId) {
        execContextSyncService.checkWriteLockPresent(task.execContextId);
        if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
            log.warn("#307.030 Task {} was reset, can't set new value to field resultReceived", task.id);
            return Enums.UploadResourceStatus.TASK_WAS_RESET;
        }
        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
        TaskParamsYaml.OutputVariable output = tpy.task.outputs.stream().filter(o->o.id.equals(variableId)).findAny().orElse(null);
        if (output==null) {
            return Enums.UploadResourceStatus.UNRECOVERABLE_ERROR;
        }
        output.uploaded = true;
        task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);
        taskService.save(task);

        return Enums.UploadResourceStatus.OK;
    }

    public Enums.UploadResourceStatus setResultReceivedForInternalFunction(Long taskId) {
        TxUtils.checkTxExists();

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#317.020 Task #{} is obsolete and was already deleted", taskId);
            return Enums.UploadResourceStatus.TASK_NOT_FOUND;
        }
        execContextSyncService.checkWriteLockPresent(task.execContextId);

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
        taskService.save(task);
        return Enums.UploadResourceStatus.OK;
    }


}
