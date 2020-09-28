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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static ai.metaheuristic.api.EnumsApi.FunctionExecContext;
import static ai.metaheuristic.api.EnumsApi.TaskExecState;

/**
 * @author Serge
 * Date: 3/15/2020
 * Time: 10:58 PM
 */
@SuppressWarnings("unused")
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class TaskWithInternalContextEventService {

    private final TaskPersistencer taskPersistencer;
    private final TaskSyncService taskSyncService;
    private final InternalFunctionProcessor internalFunctionProcessor;
    private final ExecContextCache execContextCache;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final VariableService variableService;
    private final TaskTransactionalService taskTransactionalService;
    private final TaskExecStateService taskExecStateService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskRepository taskRepository;
    private final ExecContextFSM execContextFSM;
    ;

    private static Long lastTaskId=null;
    // this code is only for testing
    public static boolean taskFinished(Long id) {
        return id.equals(lastTaskId);
    }

    @Async
    @EventListener
    @Transactional
    public void processInternalFunction(final TaskWithInternalContextEvent event) {
        execContextSyncService.getWithSyncNullable(event.execContextId, () -> {
            TaskImpl task;
            try {
                task = taskRepository.findById(event.taskId).orElse(null);
                if (task==null) {
                    log.warn("Task #{} with internal context doesn't exist", event.taskId);
                    return null;
                }
            } catch (Throwable th) {
                log.error("Error", th);
                return null;
            }
            if (!event.execContextId.equals(task.execContextId)) {
                log.error("The task #{} has different execContextId, expected: {}, actual: {}",
                        event.taskId, event.execContextId, task.execContextId);
                execContextFSM.toError(event.execContextId);
                return null;
            }

            try {
                ExecContextImpl execContext = execContextCache.findById(task.execContextId);
                if (execContext == null) {
                    taskExecStateService.finishTaskAsError(event.taskId, TaskExecState.ERROR, -10000,
                            "#707.030 Task #" + event.taskId + " is broken, execContext #" + task.execContextId + " wasn't found.");
                    return null;
                }
                try {
                    if (task.execState == TaskExecState.IN_PROGRESS.value) {
                        log.error("#707.012 Task #"+event.taskId+" already in progress. mustn't happened. it's, actually, illegal state");
                        return null;
                    }
                    if (task.execState!=TaskExecState.NONE.value) {
                        log.info("#707.011 Task #"+event.taskId+" was already processed with state " + TaskExecState.from(task.execState));
                        return null;
                    }
                    if (TaskExecState.isFinishedState(task.execState)) {
                        log.error("#707.015 Task #"+event.taskId+" already was finished");
                        return null;
                    }
                    execContextFSM.updateTaskExecStates(
                            execContextCache.findById(task.execContextId), task.id, TaskExecState.IN_PROGRESS.value, null);

                    TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                    ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
                    ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParamsYaml.task.processCode);
                    if (p == null) {
                        if (Consts.MH_FINISH_FUNCTION.equals(taskParamsYaml.task.processCode)) {
                            ExecContextParamsYaml.FunctionDefinition function = new ExecContextParamsYaml.FunctionDefinition(Consts.MH_FINISH_FUNCTION, "", FunctionExecContext.internal);
                            p = new ExecContextParamsYaml.Process(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION, Consts.TOP_LEVEL_CONTEXT_ID, function);
                        }
                        else {
                            log.warn("#707.040 can't find process '" + taskParamsYaml.task.processCode + "' in execContext with Id #" + execContext.id);
                            return null;
                        }
                    }

                    // ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService.prepareVariables
                    // won't be called for initializing output variables in internal function.
                    // the code which skips initializing is here - ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService.getTaskAndAssignToProcessor
                    variableService.initOutputVariables(taskParamsYaml, execContext, p);
                    taskTransactionalService.setParams(event.taskId, taskParamsYaml);

                    InternalFunctionData.InternalFunctionProcessingResult result = internalFunctionProcessor.process(
                            execContext.id, event.taskId, p.internalContextId, taskParamsYaml);

                    if (result.processing != Enums.InternalFunctionProcessing.ok) {
                        execContextFSM.markAsFinishedWithError(task, execContext, taskParamsYaml, result);
                        return null;
                    }
                    taskTransactionalService.setResultReceivedForInternalFunction(event.taskId);

                    ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r = new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
                    r.taskId = event.taskId;
                    FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
                    functionExec.exec = new FunctionApiData.SystemExecResult(taskParamsYaml.task.function.code, true, 0, "");
                    r.result = FunctionExecUtils.toString(functionExec);

                    execContextFSM.storeExecResult(r);
                    return null;
                } catch (CommonErrorWithDataException th) {
                    String es = "#707.067 Task #" + event.taskId + " and "+th.getAdditionalInfo()+" was processed with error: " + th.getMessage();
                    taskExecStateService.finishTaskAsError(event.taskId, TaskExecState.ERROR, -10002, es);
                    log.error(es);
                } catch (Throwable th) {
                    String es = "#707.070 Task #" + event.taskId + " was processed with error: " + th.getMessage();
                    taskExecStateService.finishTaskAsError(event.taskId, TaskExecState.ERROR, -10003, es);
                    log.error(es, th);
                }
                return null;
            } catch (Throwable th) {
                String es = "#707.080 Task #" + event.taskId + " was processed with error: " + th.getMessage();
                taskExecStateService.finishTaskAsError(event.taskId, TaskExecState.ERROR, -10004, es);
                log.error(es, th);
            }
            finally {
                lastTaskId = event.taskId;
            }
            return null;
        });
    }

}
