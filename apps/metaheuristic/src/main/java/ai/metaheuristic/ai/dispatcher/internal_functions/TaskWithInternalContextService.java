/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 9/28/2020
 * Time: 10:18 PM
 */
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskWithInternalContextService {

    private final TaskTxService taskTxService;
    private final VariableTxService variableTxService;
    private final TaskRepository taskRepository;
    private final ExecContextFSM execContextFSM;
    private final TaskExecStateService taskExecStateService;

    @Transactional
    public void preProcessing(ExecContextData.SimpleExecContext simpleExecContext, Long taskId) {

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("707.030 Task #{} with internal context doesn't exist", taskId);
            return;
        }
        preProcessInternalFunction(simpleExecContext, task);
    }

    @Transactional
    public void skipTask(Long taskId) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("707.060 Task #{} with internal context doesn't exist", taskId);
            return;
        }
        // TODO 2021-10-15 investigate the possibility to mark as completed such tasks
        taskExecStateService.updateTaskExecStates(task, EnumsApi.TaskExecState.SKIPPED, false);
    }

    @Transactional
    public void storeResult(Long taskId, String functionCode) {
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("707.090 Task #{} with internal context doesn't exist", taskId);
            return;
        }

        setResultReceivedForInternalFunction(task);

        ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r = new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
        r.taskId = task.id;
        FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
        functionExec.exec = new FunctionApiData.SystemExecResult(functionCode, true, 0, "");
        r.result = FunctionExecUtils.toString(functionExec);

        execContextFSM.storeExecResult(task, r);

        taskRepository.save(task);
    }

    private Enums.UploadVariableStatus setResultReceivedForInternalFunction(TaskImpl task) {
        TxUtils.checkTxExists();

        if (task.getExecState() == EnumsApi.TaskExecState.NONE.value) {
            log.warn("707.220 Task {} was reset, can't set new value to field resultReceived", task.id);
            return Enums.UploadVariableStatus.TASK_WAS_RESET;
        }
        TaskParamsYaml tpy = task.getTaskParamsYaml();
        tpy.task.outputs.forEach(o->o.uploaded = true);
        task.updateParams(tpy);

        task.setCompleted(1);
        task.setCompletedOn(System.currentTimeMillis());
        task.setResultReceived(1);
        taskTxService.save(task);
        return Enums.UploadVariableStatus.OK;
    }

    private void preProcessInternalFunction(ExecContextData.SimpleExecContext simpleExecContext, TaskImpl task) {
        if (task.execState == EnumsApi.TaskExecState.IN_PROGRESS.value) {
            log.error("707.250 Task #"+task.id+" already in progress.");
            return;
        }
        if (task.execState!= EnumsApi.TaskExecState.NONE.value) {
            log.info("707.280 Task #"+task.id+" was already processed with state " + EnumsApi.TaskExecState.from(task.execState));
            return;
        }
        if (EnumsApi.TaskExecState.isFinishedState(task.execState)) {
            log.error("707.310 Task #"+task.id+" already was finished");
            return;
        }

        task.setAssignedOn(System.currentTimeMillis());
        task.setResultResourceScheduledOn(0);
        task = taskTxService.save(task);

        taskExecStateService.updateTaskExecStates(task, EnumsApi.TaskExecState.IN_PROGRESS, false);

        TaskParamsYaml taskParamsYaml = task.getTaskParamsYaml();
        ExecContextParamsYaml.Process p = simpleExecContext.paramsYaml.findProcess(taskParamsYaml.task.processCode);
        if (p == null) {
            if (Consts.MH_FINISH_FUNCTION.equals(taskParamsYaml.task.processCode)) {
                ExecContextParamsYaml.FunctionDefinition function =
                        new ExecContextParamsYaml.FunctionDefinition(Consts.MH_FINISH_FUNCTION, "", EnumsApi.FunctionExecContext.internal, EnumsApi.FunctionRefType.code);
                p = new ExecContextParamsYaml.Process(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION, Consts.TOP_LEVEL_CONTEXT_ID, function);
            }
            else {
                final String msg = "707.340 can't find process '" + taskParamsYaml.task.processCode + "' in execContext with Id #" + simpleExecContext.execContextId;
                log.warn(msg);
                throw new InternalFunctionException(new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.process_not_found, msg));
            }
        }
        variableTxService.initOutputVariables(simpleExecContext.execContextId, task, p, taskParamsYaml);
    }

}
