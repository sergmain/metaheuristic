/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.events.InitVariablesTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionVariableService;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.dispatcher.variable_global.GlobalVariableTxService;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.commons.utils.ContextUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class TaskProducingService {

    private final ExecContextGraphService execContextGraphService;
    private final FunctionService functionTopLevelService;
    private final TaskTxService taskTxService;
    private final Globals globals;
    private final ApplicationEventPublisher eventPublisher;
    private final InternalFunctionVariableService internalFunctionVariableService;
    private final GlobalVariableTxService globalVariableService;
    private final VariableTxService variableTxService;
    private final VariableRepository variableRepository;


    public TaskData.ProduceTaskResult produceTaskForProcess(
            ExecContextParamsYaml.Process process,
            ExecContextParamsYaml execContextParamsYaml, Long execContextId, ExecContextData.GraphAndStates graphAndStates,
            List<Long> parentTaskIds, EnumsApi.TaskExecState taskExecState) {
        // Default: taskContextId is the Process's static internalContextId.
        return produceTaskForProcess(process, execContextParamsYaml, execContextId, graphAndStates,
                parentTaskIds, taskExecState, p -> p.internalContextId);
    }

    /**
     * Variant accepting a custom taskContextId resolver. Allows callers (e.g. manual
     * requirement injection into a finished ExecContext) to mint a task at a sibling
     * taskContextId computed via {@link ContextUtils#nextSiblingTaskContextId(String, java.util.Collection)}
     * rather than the Process's static internalContextId.
     *
     * The primary overload above delegates here with resolver = p -> p.internalContextId.
     */
    public TaskData.ProduceTaskResult produceTaskForProcess(
            ExecContextParamsYaml.Process process,
            ExecContextParamsYaml execContextParamsYaml, Long execContextId, ExecContextData.GraphAndStates graphAndStates,
            List<Long> parentTaskIds, EnumsApi.TaskExecState taskExecState,
            java.util.function.Function<ExecContextParamsYaml.Process, String> taskContextIdResolver) {
        TxUtils.checkTxExists();
        ExecContextGraphSyncService.checkWriteLockPresent(graphAndStates.graph().id);
        ExecContextTaskStateSyncService.checkWriteLockPresent(graphAndStates.states().id);

        TaskData.ProduceTaskResult result = new TaskData.ProduceTaskResult();

        final String taskContextId = taskContextIdResolver.apply(process);

        // for external Functions internalContextId==process.internalContextId
        TaskImpl t = createTaskHelper(execContextId, execContextParamsYaml, process, taskContextId,
                execContextParamsYaml.variables.inline, parentTaskIds, taskExecState);
//        if (t == null) {
//            return new TaskData.ProduceTaskResult(
//                    EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR, "375.020 Unknown reason of error while task creation");
//        }

        result.taskId = t.getId();
        List<TaskApiData.TaskWithContext> taskWithContexts = List.of(new TaskApiData.TaskWithContext( t.getId(), taskContextId));
        final EnumsApi.TaskExecState targetState = EnumsApi.TaskExecState.from(t.execState);
        if (targetState.value!=t.execState) {
            log.info("(targetState.value!=t.execState)");
            throw new IllegalStateException("(targetState.value!=t.execState)");
        }
        execContextGraphService.addNewTasksToGraph(graphAndStates, parentTaskIds, taskWithContexts, targetState);

        result.status = EnumsApi.TaskProducingStatus.OK;
        return result;
    }

    /**
     */
    public void createTasksForSubProcesses(
            ExecContextData.GraphAndStates graphAndStates,
            ExecContextApiData.SimpleExecContext simpleExecContext, InternalFunctionData.ExecutionContextData executionContextData,
            String currTaskContextId, Long parentTaskId, List<Long> lastIds) {
        TxUtils.checkTxExists();
        ExecContextGraphSyncService.checkWriteLockPresent(simpleExecContext.execContextGraphId);
        ExecContextTaskStateSyncService.checkWriteLockPresent(simpleExecContext.execContextTaskStateId);

        ExecContextParamsYaml execContextParamsYaml = executionContextData.execContextParamsYaml;
        List<ExecContextApiData.ProcessVertex> subProcesses = executionContextData.subProcesses;
        if (subProcesses.isEmpty()) {
            log.info("375.040 There isn't any subProcess");
            return;
        }

        Map<String, Map<String, String>> inlines = executionContextData.execContextParamsYaml.variables.inline;
        ExecContextParamsYaml.Process process = executionContextData.process;

        if (process.logic!= EnumsApi.SourceCodeSubProcessLogic.sequential && process.logic!= EnumsApi.SourceCodeSubProcessLogic.and) {
            throw new BreakFromLambdaException("375.060 only the 'sequential' and 'and' logics are supported");
        }

        List<Long> parentTaskIds = List.of(parentTaskId);
        String subProcessContextId = executionContextData.subProcesses.get(0).processContextId;

        // For "and" logic: derive the parent's taskContextId from currTaskContextId
        // so each parallel branch gets a unique context encoding the parent's instance.
        // deriveParentTaskContextId reverses the buildTaskContextId+getCurrTaskContextIdForSubProcesses
        // that the caller applied.
        final String parentTaskContextId = ContextUtils.deriveParentTaskContextId(currTaskContextId);
        int andBranchIndex = 0;

        TaskImpl t = null;
        for (ExecContextApiData.ProcessVertex subProcess : subProcesses) {
            final ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(subProcess.process);
            if (p==null) {
                throw new BreakFromLambdaException("375.080 Process '" + subProcess.process + "' wasn't found");
            }

            String actualProcessContextId = switch (process.logic) {
                case and -> {
                    String andSubProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                            parentTaskContextId, subProcess.processContextId);
                    yield ContextUtils.buildTaskContextId(andSubProcessContextId, Integer.toString(andBranchIndex++));
                }
                case sequential -> {
                    // all subProcesses must have the same processContextId
                    if (!subProcessContextId.equals(subProcess.processContextId)) {
                        throw new BreakFromLambdaException("375.100 Different contextId, prev: " + subProcessContextId + ", next: " + subProcess.processContextId);
                    }
                    yield currTaskContextId;
                }
                default ->
                    throw new BreakFromLambdaException("375.060 only the 'sequential' and 'and' logics are supported");
            };

            t = createTaskHelper(simpleExecContext.execContextId, execContextParamsYaml, p, actualProcessContextId, inlines, List.of(parentTaskId), EnumsApi.TaskExecState.PRE_INIT);
//            if (t==null) {
//                throw new BreakFromLambdaException("375.120 Creation of task failed");
//            }

            final EnumsApi.TaskExecState targetState = EnumsApi.TaskExecState.from(t.execState);
            if (targetState.value!=t.execState) {
                log.info("(targetState.value!=t.execState)");
                throw new IllegalStateException("(targetState.value!=t.execState)");
            }
            List<TaskApiData.TaskWithContext> currTaskIds = List.of(new TaskApiData.TaskWithContext(t.getId(), actualProcessContextId));
            execContextGraphService.addNewTasksToGraph(graphAndStates, parentTaskIds, currTaskIds, targetState);
            if (process.logic == EnumsApi.SourceCodeSubProcessLogic.and) {
                // Parallel: each subprocess branches from the original parent, collect ALL for downstream linking
                lastIds.add(t.id);
                // parentTaskIds stays as original [parentTaskId] — do NOT update
            }
            else {
                // Sequential: chain each subprocess to the previous one
                parentTaskIds = List.of(t.getId());
            }
            subProcessContextId = subProcess.processContextId;
        }
        if (process.logic != EnumsApi.SourceCodeSubProcessLogic.and) {
            // Sequential: only the last task connects downstream
            lastIds.add(t.id);
        }
    }

//    @Nullable
    private TaskImpl createTaskHelper(
        Long execContextId, ExecContextParamsYaml execContextParamsYaml, ExecContextParamsYaml.Process process,
        String taskContextId, @Nullable Map<String, Map<String, String>> inlines, List<Long> parentTaskIds, EnumsApi.TaskExecState taskExecState) {

        TxUtils.checkTxExists();

        TaskParamsYaml taskParams = new TaskParamsYaml();
        taskParams.task.execContextId = execContextId;
        taskParams.task.taskContextId = taskContextId;
        taskParams.task.processCode = process.processCode;
        taskParams.task.context = process.function.context;
        taskParams.task.inline = inlines;

        taskParams.task.metas.addAll(process.metas);

        if (taskParams.task.context== EnumsApi.FunctionExecContext.internal) {
            taskParams.task.function = new TaskParamsYaml.FunctionConfig(
                    process.function.code, "internal", null, S.b(process.function.params) ? "" : process.function.params, "internal",
                    EnumsApi.FunctionSourcing.dispatcher, null,
                    null, CommonConsts.DEFAULT_FUNCTION_SRC_DIR, null );
        }
        else {
            TaskParamsYaml.FunctionConfig fConfig = functionTopLevelService.getFunctionConfig(process.function);
            if (fConfig == null) {
//                log.error("375.140 Function '{}' wasn't found", process.function.code);
//                return null;
                String es = S.f("375.140 Function '%s' wasn't found", process.function.code);
                throw new CommonRollbackException(es, EnumsApi.OperationStatus.ERROR);
            }
            taskParams.task.function = fConfig;
            if (process.getPreFunctions()!=null) {
                for (ExecContextParamsYaml.FunctionDefinition preFunction : process.getPreFunctions()) {
                    TaskParamsYaml.FunctionConfig functionConfig = functionTopLevelService.getFunctionConfig(preFunction);
                    if (functionConfig==null) {
//                        log.error("375.145 Pre-function '{}' wasn't found", preFunction.code);
//                        continue;
                        String es = S.f("375.145 Pre-function '%s' wasn't found", preFunction.code);
                        throw new CommonRollbackException(es, EnumsApi.OperationStatus.ERROR);
                    }
                    taskParams.task.preFunctions.add(functionConfig);
                }
            }
            if (process.getPostFunctions()!=null) {
                for (ExecContextParamsYaml.FunctionDefinition postFunction : process.getPostFunctions()) {
                    TaskParamsYaml.FunctionConfig functionConfig = functionTopLevelService.getFunctionConfig(postFunction);
                    if (functionConfig==null) {
//                        log.error("375.150 Post-function '{}' wasn't found", postFunction.code);
//                        continue;
                        String es = S.f("375.150 Post-function '%s' wasn't found", postFunction.code);
                        throw new CommonRollbackException(es, EnumsApi.OperationStatus.ERROR);
                    }
                    taskParams.task.postFunctions.add(functionConfig);
                }
            }
        }
        taskParams.task.clean = execContextParamsYaml.clean;
        taskParams.task.timeoutBeforeTerminate = process.timeoutBeforeTerminate;
        taskParams.task.triesAfterError = process.triesAfterError==null ? null : Math.min(globals.dispatcher.getMaxTriesAfterError(), Math.max(0, process.triesAfterError));
        if (process.cache!=null) {
            taskParams.task.cache = new TaskParamsYaml.Cache(process.cache.enabled, process.cache.omitInline, process.cache.cacheMeta);
        }
        taskParams.task.init = new TaskParamsYaml.Init(parentTaskIds, process.cache!=null && process.cache.enabled ? EnumsApi.TaskExecState.CHECK_CACHE : EnumsApi.TaskExecState.NONE);


        TaskImpl task = new TaskImpl();
        task.execState = taskExecState.value;
        task.execContextId = execContextId;
        task.updateParams(taskParams);

        task = taskTxService.save(task);

        // event will land at ai.metaheuristic.ai.dispatcher.task.TaskVariableInitService.handleEvent
        eventPublisher.publishEvent(new InitVariablesTxEvent(task.id));

        return task;
    }

}
