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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.commons.ArtifactCleanerAtDispatcher;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.el.EvaluateExpressionLanguage;
import ai.metaheuristic.ai.dispatcher.event.events.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingTxService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.task.TaskTxService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableTxService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.threads.MultiTenantedQueue;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * @author Serge
 * Date: 3/15/2020
 * Time: 10:58 PM
 */
@SuppressWarnings("unused")
@Slf4j
@Service
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskWithInternalContextEventService {

    private final TaskWithInternalContextService taskWithInternalContextService;
    private final TaskWithInternalContextTopLevelService taskWithInternalContextTopLevelService;
    private final ExecContextCache execContextCache;
    private final ExecContextFSM execContextFSM;
    private final TaskRepository taskRepository;

    private final TaskTxService taskService;
    private final VariableTxService variableTxService;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final SourceCodeCache sourceCodeCache;
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;
    private final SourceCodeRepository sourceCodeRepository;
    private final GlobalVariableTxService globalVariableService;
    private final VariableRepository variableRepository;
    private final TaskFinishingTxService taskFinishingTxService;
    private final ApplicationEventPublisher eventPublisher;
    private final VariableService variableTopLevelService;
    private final ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;
    private final InternalFunctionProcessor internalFunctionProcessor;

    private static final MultiTenantedQueue<Long, TaskWithInternalContextEvent> MULTI_TENANTED_QUEUE =
        new MultiTenantedQueue<>(100, Duration.ofSeconds(0), true, null);

    @SuppressWarnings("MethodMayBeStatic")
    @PreDestroy
    public void onExit() {
        MULTI_TENANTED_QUEUE.clearQueue();
    }

    public static void clearQueue() {
        MULTI_TENANTED_QUEUE.clearQueue();
    }

    public void putToQueue(final TaskWithInternalContextEvent event) {
        MULTI_TENANTED_QUEUE.putToQueue(event, this::process);
    }

    private void process(final TaskWithInternalContextEvent event) {
        TxUtils.checkTxNotExists();

        ExecContextImpl execContext = execContextCache.findById(event.execContextId, true);
        if (execContext==null) {
            return;
        }
        final ExecContextData.SimpleExecContext simpleExecContext = execContext.asSimple();
        ArtifactCleanerAtDispatcher.setBusy();
        try {
            TaskSyncService.getWithSyncVoid(event.taskId,
                    () -> {
                        taskWithInternalContextService.preProcessing(simpleExecContext, event.taskId);
                        processInternalFunction(simpleExecContext, event.taskId);
                    });
        }
        catch (InternalFunctionException e) {
            if (e.result.processing != Enums.InternalFunctionProcessing.ok) {
                log.error("#706.100 error type: {}, message: {}\n\tsourceCodeId: {}, execContextId: {}",
                        e.result.processing, e.result.error, event.sourceCodeId, event.execContextId);
                final String console = "#706.130 Task #" + event.taskId + " was finished with status '" + e.result.processing + "', text of error: " + e.result.error;
                ExecContextSyncService.getWithSyncVoid(event.execContextId,
                        () -> TaskSyncService.getWithSyncVoid(event.taskId,
                                () -> taskFinishingTxService.finishWithErrorWithTx(event.taskId, console)));
            }
        }
        catch (Throwable th) {
            final String es = "#706.150 Error while processing the task #"+event.taskId+" with internal function. Error: " + th.getMessage() +
                    ". Cause error: " + (th.getCause()!=null ? th.getCause().getMessage() : " is null.");

            log.error(es, th);
            ExecContextSyncService.getWithSyncVoid(event.execContextId,
                    () -> TaskSyncService.getWithSyncVoid(event.taskId,
                            () -> taskFinishingTxService.finishWithErrorWithTx(event.taskId, es)));
        }
        finally {
            ArtifactCleanerAtDispatcher.notBusy();
        }
    }

    private void processInternalFunction(ExecContextData.SimpleExecContext simpleExecContext, Long taskId) {
        TxUtils.checkTxNotExists();
        TaskLastProcessingHelper.lastTaskId = null;
        try {
            TaskImpl task = taskRepository.findByIdReadOnly(taskId);
            if (task==null) {
                log.warn("#706.180 Task #{} with internal context doesn't exist", taskId);
                return;
            }

            if (task.execState != EnumsApi.TaskExecState.IN_PROGRESS.value) {
                log.error("#706.210 Task #"+task.id+" already in progress.");
                return;
            }

            TaskParamsYaml taskParamsYaml = task.getTaskParamsYaml();
            ExecContextParamsYaml.Process p = simpleExecContext.paramsYaml.findProcess(taskParamsYaml.task.processCode);
            if (p == null) {
                if (Consts.MH_FINISH_FUNCTION.equals(taskParamsYaml.task.processCode)) {
                    ExecContextParamsYaml.FunctionDefinition function =
                            new ExecContextParamsYaml.FunctionDefinition(Consts.MH_FINISH_FUNCTION, "", EnumsApi.FunctionExecContext.internal, EnumsApi.FunctionRefType.code);
                    p = new ExecContextParamsYaml.Process(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION, Consts.TOP_LEVEL_CONTEXT_ID, function);
                }
                else {
                    final String msg = "#706.240 can't find process '" + taskParamsYaml.task.processCode + "' in execContext with Id #" + simpleExecContext.execContextId;
                    log.warn(msg);
                    throw new InternalFunctionException(new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.process_not_found, msg));
                }
            }

            boolean notSkip = true;
            if (!S.b(p.condition)) {
                // in EvaluateExpressionLanguage.evaluate() we need only to use variableService.setVariableAsNull(v.id)
                // because mh.evaluate doesn't have any output variables
                Object obj = EvaluateExpressionLanguage.evaluate(
                        taskParamsYaml.task.taskContextId, p.condition, simpleExecContext.execContextId,
                        internalFunctionVariableService, globalVariableService, variableTxService, variableRepository,
                        (v) -> VariableSyncService.getWithSyncVoidForCreation(v.id,
                                ()-> variableTxService.setVariableAsNull(v.id)));
                if (obj!=null && !(obj instanceof Boolean)) {
                    final String es = "#706.300 condition '" + p.condition + " has returned not boolean value but " + obj.getClass().getSimpleName();
                    log.error(es);
                    throw new InternalFunctionException(Enums.InternalFunctionProcessing.source_code_is_broken, es);
                }
                notSkip = Boolean.TRUE.equals(obj);
                int i=0;
            }
            if (notSkip) {
                boolean isLongRunning = internalFunctionProcessor.process(simpleExecContext, taskId, taskParamsYaml.task.taskContextId, taskParamsYaml);
                if (!isLongRunning) {
                    taskWithInternalContextService.storeResult(taskId, taskParamsYaml.task.function.code);
                }
            }
            else {
                taskWithInternalContextService.skipTask(taskId);
            }
        }
        finally {
            TaskLastProcessingHelper.lastTaskId = taskId;
        }
    }

}
