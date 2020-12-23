/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.task.TaskStateService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 9/28/2020
 * Time: 10:18 PM
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class TaskWithInternalContextService {

    private final InternalFunctionProcessor internalFunctionProcessor;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextFSM execContextFSM;
    private final TaskService taskService;
    private final TaskStateService taskStateService;
    private final ExecContextVariableService execContextVariableService;
    private final VariableService variableService;
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Nullable
    private static Long lastTaskId=null;

    // this code is only for testing
    public static boolean taskProcessed(Long id) {
        return id.equals(lastTaskId);
    }

    // this code is only for testing
    public static void resetLastTask() {
        lastTaskId=null;
    }

    @Transactional
    public Void processInternalFunctionWithTx(Long execContextId, Long taskId) {
        execContextSyncService.checkWriteLockPresent(execContextId);
        lastTaskId = null;

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            log.warn("#707.020 ExecContext #{} doesn't exist", execContextId);
            return null;
        }
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#707.040 Task #{} with internal context doesn't exist", taskId);
            return null;
        }
        if (!execContextId.equals(task.execContextId)) {
            log.error("#707.060 The task #{} has different execContextId, expected: {}, actual: {}",
                    taskId, execContextId, task.execContextId);
            return null;
        }
        processInternalFunction(execContext, task);

        eventPublisher.publishEvent(new LockByExecContextIdTxEvent(execContextId));

        return null;
    }

    private void processInternalFunction(ExecContextImpl execContext, TaskImpl task) {
        try {
            task.setAssignedOn(System.currentTimeMillis());
            task.setResultResourceScheduledOn(0);
            task = taskService.save(task);

            try {
                if (task.execState == EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    log.error("#707.080 Task #"+task.id+" already in progress.");
                    return;
                }
                if (task.execState!= EnumsApi.TaskExecState.NONE.value) {
                    log.info("#707.100 Task #"+task.id+" was already processed with state " + EnumsApi.TaskExecState.from(task.execState));
                    return;
                }
                if (EnumsApi.TaskExecState.isFinishedState(task.execState)) {
                    log.error("#707.120 Task #"+task.id+" already was finished");
                    return;
                }
                taskStateService.updateTaskExecStates(
                        task, EnumsApi.TaskExecState.IN_PROGRESS, null);

                TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
                ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParamsYaml.task.processCode);
                if (p == null) {
                    if (Consts.MH_FINISH_FUNCTION.equals(taskParamsYaml.task.processCode)) {
                        ExecContextParamsYaml.FunctionDefinition function = new ExecContextParamsYaml.FunctionDefinition(Consts.MH_FINISH_FUNCTION, "", EnumsApi.FunctionExecContext.internal);
                        p = new ExecContextParamsYaml.Process(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION, Consts.TOP_LEVEL_CONTEXT_ID, function);
                    }
                    else {
                        log.warn("#707.140 can't find process '" + taskParamsYaml.task.processCode + "' in execContext with Id #" + execContext.id);
                        return;
                    }
                }

                variableService.initOutputVariables(execContext.id, task, p, taskParamsYaml);

                InternalFunctionData.InternalFunctionProcessingResult result = internalFunctionProcessor.process(
                        execContext, task, p.internalContextId, taskParamsYaml);

                eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphTxEvent(task.execContextId, task.id));

                if (result.processing != Enums.InternalFunctionProcessing.ok) {
                    log.error("#707.160 error type: {}, message: {}\n\tsourceCodeId: {}, execContextId: {}",
                            result.processing, result.error, execContext.sourceCodeId, execContext.id);
                    final String console = "#707.180 Task #" + task.id + " was finished with status '" + result.processing + "', text of error: " + result.error;
                    final String taskContextId = taskParamsYaml.task.taskContextId;
                    taskStateService.finishWithError(task, console, taskContextId);
                    return;
                }

                execContextVariableService.setResultReceivedForInternalFunction(task.id);

                ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r = new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
                r.taskId = task.id;
                FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
                functionExec.exec = new FunctionApiData.SystemExecResult(taskParamsYaml.task.function.code, true, 0, "");
                r.result = FunctionExecUtils.toString(functionExec);

                execContextFSM.storeExecResult(task, r);

            } catch (CommonErrorWithDataException th) {
                String es = "#707.200 Task #" + task.id + " and "+th.getAdditionalInfo()+" was processed with error: " + th.getMessage();
                taskStateService.finishWithError(task, es, null, -10002);
                log.error(es);
            } catch (Throwable th) {
                String es = "#707.220 Task #" + task.id + " was processed with error: " + th.getMessage();
                taskStateService.finishWithError(task, es, null, -10003);
                log.error(es, th);
            }
        } catch (Throwable th) {
            String es = "#707.240 Task #" + task.id + " was processed with error: " + th.getMessage();
            taskStateService.finishWithError(task, es, null, -10004);
            log.error(es, th);
        }
        finally {
            lastTaskId = task.id;
        }
    }

}
