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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.el.EvaluateExpressionLanguage;
import ai.metaheuristic.ai.dispatcher.event.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

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

    private final TaskWithInternalContextService taskWithInternalContextService;
    private final TaskWithInternalContextTopLevelService taskWithInternalContextTopLevelService;
    private final ExecContextCache execContextCache;
    private final ExecContextFSM execContextFSM;
    private final TaskRepository taskRepository;

    private final TaskService taskService;
    private final ExecContextVariableService execContextVariableService;
    private final VariableService variableService;
    public final InternalFunctionVariableService internalFunctionVariableService;
    public final SourceCodeCache sourceCodeCache;
    public final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;
    public final SourceCodeRepository sourceCodeRepository;
    public final GlobalVariableService globalVariableService;
    public final VariableRepository variableRepository;
    private final TaskFinishingService taskFinishingService;
    private final ApplicationEventPublisher eventPublisher;

    public static final int MAX_ACTIVE_THREAD = 1;
    // number of active executers with different execContextId
    private static final int MAX_NUMBER_EXECUTORS = 2;

    public static class ExecutorForExecContext {
        public Long execContextId;
        public final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_ACTIVE_THREAD);

        public ExecutorForExecContext(Long execContextId) {
            this.execContextId = execContextId;
        }
    }

    public static final LinkedHashMap<Long, LinkedList<TaskWithInternalContextEvent>> QUEUE = new LinkedHashMap<>();
    public static final ExecutorForExecContext[] POOL_OF_EXECUTORS = new ExecutorForExecContext[MAX_NUMBER_EXECUTORS];

    public void putToQueue(final TaskWithInternalContextEvent event) {
        putToQueueInternal(event);
        processPoolOfExecutors(event.execContextId, this::process);
    }

    public static void processPoolOfExecutors(Long execContextId, Consumer<TaskWithInternalContextEvent> taskProcessor) {
        synchronized (POOL_OF_EXECUTORS) {
            for (int i = 0; i< POOL_OF_EXECUTORS.length; i++) {
                if (POOL_OF_EXECUTORS[i] == null) {
                    continue;
                }
                if (POOL_OF_EXECUTORS[i].executor.getQueue().isEmpty() && POOL_OF_EXECUTORS[i].executor.getActiveCount()==0) {
                    POOL_OF_EXECUTORS[i].executor.shutdown();
                    POOL_OF_EXECUTORS[i] = null;
                }
            }
            final LinkedHashSet<Long> execContextIds;
            synchronized (QUEUE) {
                execContextIds = new LinkedHashSet<>(QUEUE.keySet());
            }
            for (Long id : execContextIds) {
                if (pokeExecutor(id, taskProcessor)) {
                    break;
                }
            }
        }
    }

    /**
     *
     * @param execContextId
     * @return boolean is POOL_OF_EXECUTORS already full and execContextId wasn't allocated
     */
    public static boolean pokeExecutor(Long execContextId, Consumer<TaskWithInternalContextEvent> taskProcessor) {
        synchronized (POOL_OF_EXECUTORS) {
            for (ExecutorForExecContext poolExecutor : POOL_OF_EXECUTORS) {
                if (poolExecutor==null) {
                    continue;
                }
                if (poolExecutor.execContextId.equals(execContextId)) {
                    return false;
                }
            }
/*
            for (ExecutorForExecContext poolExecutor : POOL_OF_EXECUTORS) {
                if (poolExecutor==null) {
                    continue;
                }
                if (poolExecutor.execContextId.equals(execContextId)) {
                    if (poolExecutor.executor.getActiveCount() == 0) {
                        poolExecutor.executor.submit(() -> processTask(execContextId, taskProcessor, poolExecutor));
                    }
                    return false;
                }
            }
*/
            for (int i = 0; i < POOL_OF_EXECUTORS.length; i++) {
                if (POOL_OF_EXECUTORS[i]==null) {
                    POOL_OF_EXECUTORS[i] = new ExecutorForExecContext(execContextId);

                    final int idx = i;
                    POOL_OF_EXECUTORS[i].executor.submit(() -> processTask(execContextId, taskProcessor, POOL_OF_EXECUTORS[idx]));
                    return false;
                }
            }
            return true;
        }
    }

    private static void processTask(Long execContextId, Consumer<TaskWithInternalContextEvent> taskProcessor, ExecutorForExecContext poolExecutor) {
        TaskWithInternalContextEvent e;
        while ((e = pullFromQueue(poolExecutor.execContextId)) != null) {
            taskProcessor.accept(e);
        }
    }

    public static void putToQueueInternal(final TaskWithInternalContextEvent event) {
        synchronized (QUEUE) {
            LinkedList<TaskWithInternalContextEvent> q = QUEUE.computeIfAbsent(event.execContextId, (o)->new LinkedList<>());

            if (q.contains(event)) {
                return;
            }
            q.add(event);
        }
    }

    @Nullable
    private static TaskWithInternalContextEvent pullFromQueue(Long execContextId) {
        synchronized (QUEUE) {
            final LinkedList<TaskWithInternalContextEvent> events = QUEUE.get(execContextId);
            if (events==null) {
                return null;
            }
            if (events.isEmpty()) {
                QUEUE.remove(execContextId);
                return null;
            }
            return events.pollFirst();
        }
    }

    private void process(final TaskWithInternalContextEvent event) {
        TxUtils.checkTxNotExists();

        ExecContextImpl execContext = execContextCache.findById(event.execContextId);
        if (execContext==null) {
            return;
        }
        final ExecContextData.SimpleExecContext simpleExecContext = execContext.asSimple();
        try {
            TaskSyncService.getWithSyncVoid(event.taskId,
                    () -> {
                        taskWithInternalContextService.preProcessing(simpleExecContext, event.taskId);
                        processInternalFunction(simpleExecContext, event.taskId);
                    });
        }
        catch (InternalFunctionException e) {
            if (e.result.processing != Enums.InternalFunctionProcessing.ok) {
                log.error("#707.160 error type: {}, message: {}\n\tsourceCodeId: {}, execContextId: {}",
                        e.result.processing, e.result.error, event.sourceCodeId, event.execContextId);
                final String console = "#707.180 Task #" + event.taskId + " was finished with status '" + e.result.processing + "', text of error: " + e.result.error;
                ExecContextSyncService.getWithSyncVoid(event.execContextId,
                        () -> TaskSyncService.getWithSyncNullable(event.taskId,
                                () -> taskFinishingService.finishWithErrorWithTx(event.taskId, console)));
            }
        }
        catch (Throwable th) {
            final String es = "#989.020 Error while processing the task #"+event.taskId+" with internal function. Error: " + th.getMessage() +
                    ". Cause error: " + (th.getCause()!=null ? th.getCause().getMessage() : " is null.");

            log.error(es, th);
            ExecContextSyncService.getWithSyncVoid(event.execContextId,
                    () -> TaskSyncService.getWithSyncNullable(event.taskId,
                            () -> taskFinishingService.finishWithErrorWithTx(event.taskId, es)));
        }
    }

    private Void processInternalFunction(ExecContextData.SimpleExecContext simpleExecContext, Long taskId) {
        TxUtils.checkTxNotExists();
        TaskLastProcessingHelper.lastTaskId = null;
        try {
            TaskImpl task = taskRepository.findById(taskId).orElse(null);
            if (task==null) {
                log.warn("#707.040 Task #{} with internal context doesn't exist", taskId);
                return null;
            }

            if (task.execState != EnumsApi.TaskExecState.IN_PROGRESS.value) {
                log.error("#707.080 Task #"+task.id+" already in progress.");
                return null;
            }

            TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
            ExecContextParamsYaml.Process p = simpleExecContext.paramsYaml.findProcess(taskParamsYaml.task.processCode);
            if (p == null) {
                if (Consts.MH_FINISH_FUNCTION.equals(taskParamsYaml.task.processCode)) {
                    ExecContextParamsYaml.FunctionDefinition function = new ExecContextParamsYaml.FunctionDefinition(Consts.MH_FINISH_FUNCTION, "", EnumsApi.FunctionExecContext.internal);
                    p = new ExecContextParamsYaml.Process(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION, Consts.TOP_LEVEL_CONTEXT_ID, function);
                }
                else {
                    final String msg = "#707.140 can't find process '" + taskParamsYaml.task.processCode + "' in execContext with Id #" + simpleExecContext.execContextId;
                    log.warn(msg);
                    throw new InternalFunctionException(new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.process_not_found, msg));
                }
            }

            boolean notSkip = true;
            if (!S.b(p.condition)) {
                Object obj = EvaluateExpressionLanguage.evaluate(
                        taskParamsYaml.task.taskContextId, p.condition, simpleExecContext.execContextId,
                        internalFunctionVariableService, globalVariableService, variableService, this.execContextVariableService, variableRepository);
                notSkip = Boolean.TRUE.equals(obj);
                int i=0;
            }
            if (notSkip) {
                boolean isLongRunning = InternalFunctionProcessor.process(simpleExecContext, taskId, taskParamsYaml.task.taskContextId, taskParamsYaml);
                if (!isLongRunning) {
                    taskWithInternalContextService.storeResult(taskId, taskParamsYaml);
                }
            }
            else {
                taskWithInternalContextService.skipTask(taskId);
            }
        }
        finally {
            TaskLastProcessingHelper.lastTaskId = taskId;
        }
        return null;
    }

}
