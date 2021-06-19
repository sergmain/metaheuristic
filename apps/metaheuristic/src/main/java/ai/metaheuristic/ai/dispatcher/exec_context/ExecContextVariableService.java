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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.ExecContextCommonException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
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

    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final ExecContextService execContextService;
    private final VariableService variableService;

    /**
     * this method is expecting only one input variable in execContext
     * for initializing more that one input variable the method initInputVariables has to be used
     */
    @Transactional
    public void initInputVariable(InputStream is, long size, String originFilename, Long execContextId, ExecContextParamsYaml execContextParamsYaml) {
        if (execContextParamsYaml.variables.inputs.size()!=1) {
            throw new ExecContextCommonException("#697.020 expected only one input variable in execContext but actual count: " +execContextParamsYaml.variables.inputs.size());
        }
        String inputVariable = execContextParamsYaml.variables.inputs.get(0).name;
        if (S.b(inputVariable)) {
            throw new ExecContextCommonException("##697.040 Wrong format of sourceCode, input variable for source code isn't specified");
        }
        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            log.warn("#697.060 ExecContext #{} wasn't found", execContextId);
        }
        variableService.createInitialized(is, size, inputVariable, originFilename, execContextId, Consts.TOP_LEVEL_CONTEXT_ID );
    }

    @Transactional
    public void initInputVariables(ExecContextData.VariableInitializeList list) {
        if (list.execContextParamsYaml.variables.inputs.size()!=list.vars.size()) {
            throw new ExecContextCommonException(
                    S.f("#697.080 the number of input streams and variables for initing are different. is count: %d, vars count: %d",
                            list.vars.size(), list.execContextParamsYaml.variables.inputs.size()));
        }
        ExecContextImpl execContext = execContextService.findById(list.execContextId);
        if (execContext==null) {
            throw new ExecContextCommonException(
                    S.f("#697.100 ExecContext #{} wasn't found", list.execContextId));
        }
        for (int i = 0; i < list.vars.size(); i++) {
            ExecContextData.VariableInitialize var = list.vars.get(i);
            String inputVariable = list.execContextParamsYaml.variables.inputs.get(i).name;
            if (S.b(inputVariable)) {
                throw new ExecContextCommonException("##697.120 Wrong format of sourceCode, input variable for source code isn't specified");
            }
            variableService.createInitialized(var.is, var.size, inputVariable, var.originFilename, execContext.id, Consts.TOP_LEVEL_CONTEXT_ID );
        }
    }

    public Enums.UploadVariableStatus setResultReceivedForInternalFunction(TaskImpl task) {
        TxUtils.checkTxExists();

/*
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#441.200.020 Task #{} is obsolete and was already deleted", taskId);
            return Enums.UploadVariableStatus.TASK_NOT_FOUND;
        }
*/

        if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
            log.warn("#441.220 Task {} was reset, can't set new value to field resultReceived", task.id);
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
