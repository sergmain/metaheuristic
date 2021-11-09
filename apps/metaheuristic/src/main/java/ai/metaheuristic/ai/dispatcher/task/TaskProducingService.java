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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.function.FunctionTopLevelService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskProducingService {

    private final VariableService variableService;
    private final ExecContextGraphService execContextGraphService;
    private final FunctionTopLevelService functionTopLevelService;
    private final TaskService taskService;

    public TaskData.ProduceTaskResult produceTaskForProcess(
            ExecContextParamsYaml.Process process,
            ExecContextParamsYaml execContextParamsYaml, Long execContextId, Long execContextGraphId, Long execContextTaskStateId,
            List<Long> parentTaskIds) {
        TxUtils.checkTxExists();
        ExecContextGraphSyncService.checkWriteLockPresent(execContextGraphId);
        ExecContextTaskStateSyncService.checkWriteLockPresent(execContextTaskStateId);

        TaskData.ProduceTaskResult result = new TaskData.ProduceTaskResult();

        // for external Functions internalContextId==process.internalContextId
        TaskImpl t = createTaskInternal(execContextId, execContextParamsYaml, process, process.internalContextId,
                execContextParamsYaml.variables.inline);
        if (t == null) {
            return new TaskData.ProduceTaskResult(
                    EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR, "#375.020 Unknown reason of error while task creation");
        }

        result.taskId = t.getId();
        List<TaskApiData.TaskWithContext> taskWithContexts = List.of(new TaskApiData.TaskWithContext( t.getId(), process.internalContextId));
        final EnumsApi.TaskExecState targetState = EnumsApi.TaskExecState.from(t.execState);
        if (targetState.value!=t.execState) {
            log.info("(targetState.value!=t.execState)");
            throw new IllegalStateException("(targetState.value!=t.execState)");
        }
        execContextGraphService.addNewTasksToGraph(execContextGraphId, execContextTaskStateId, parentTaskIds, taskWithContexts, targetState);

        result.status = EnumsApi.TaskProducingStatus.OK;
        return result;
    }

    /**
     * @param simpleExecContext
     * @param currTaskContextId
     * @param parentTaskId
     * @param lastIds
     */
    public void createTasksForSubProcesses(
            ExecContextData.SimpleExecContext simpleExecContext, InternalFunctionData.ExecutionContextData executionContextData,
            String currTaskContextId, Long parentTaskId, List<Long> lastIds) {
        TxUtils.checkTxExists();
        ExecContextGraphSyncService.checkWriteLockPresent(simpleExecContext.execContextGraphId);
        ExecContextTaskStateSyncService.checkWriteLockPresent(simpleExecContext.execContextTaskStateId);

        ExecContextParamsYaml execContextParamsYaml = executionContextData.execContextParamsYaml;
        List<ExecContextData.ProcessVertex> subProcesses = executionContextData.subProcesses;
        if (subProcesses.isEmpty()) {
            log.info("#375.040 There isn't any subProcess");
            return;
        }

        Map<String, Map<String, String>> inlines = executionContextData.execContextParamsYaml.variables.inline;
        ExecContextParamsYaml.Process process = executionContextData.process;

        if (process.logic!= EnumsApi.SourceCodeSubProcessLogic.sequential && process.logic!= EnumsApi.SourceCodeSubProcessLogic.and) {
            throw new BreakFromLambdaException("#375.060 only the 'sequential' and 'and' logics are supported");
        }

        List<Long> parentTaskIds = List.of(parentTaskId);
        String subProcessContextId = executionContextData.subProcesses.get(0).processContextId;


        TaskImpl t = null;
        for (ExecContextData.ProcessVertex subProcess : subProcesses) {
            final ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(subProcess.process);
            if (p==null) {
                throw new BreakFromLambdaException("#375.080 Process '" + subProcess.process + "' wasn't found");
            }

            String actualProcessContextId;
            switch (process.logic) {
                case and:
                    actualProcessContextId = subProcess.processContextId;
                    break;
                case sequential:
                    // all subProcesses must have the same processContextId
                    if (!subProcessContextId.equals(subProcess.processContextId)) {
                        throw new BreakFromLambdaException("#375.100 Different contextId, prev: "+ subProcessContextId+", next: " +subProcess.processContextId);
                    }
                    actualProcessContextId = currTaskContextId;
                    break;
                default:
                    throw new BreakFromLambdaException("#375.060 only the 'sequential' and 'and' logics are supported");
            }

            t = createTaskInternal(simpleExecContext.execContextId, execContextParamsYaml, p, actualProcessContextId, inlines);

            if (t==null) {
                throw new BreakFromLambdaException("#375.120 Creation of task failed");
            }
            final EnumsApi.TaskExecState targetState = EnumsApi.TaskExecState.from(t.execState);
            if (targetState.value!=t.execState) {
                log.info("(targetState.value!=t.execState)");
                throw new IllegalStateException("(targetState.value!=t.execState)");
            }
            List<TaskApiData.TaskWithContext> currTaskIds = List.of(new TaskApiData.TaskWithContext(t.getId(), actualProcessContextId));
            execContextGraphService.addNewTasksToGraph(simpleExecContext.execContextGraphId, simpleExecContext.execContextTaskStateId, parentTaskIds, currTaskIds, targetState);
            parentTaskIds = List.of(t.getId());
            subProcessContextId = subProcess.processContextId;
        }
        lastIds.add(t.id);
    }

    @Nullable
    private TaskImpl createTaskInternal(
            Long execContextId, ExecContextParamsYaml execContextParamsYaml, ExecContextParamsYaml.Process process,
            String taskContextId, @Nullable Map<String, Map<String, String>> inlines) {

        TaskParamsYaml taskParams = new TaskParamsYaml();
        taskParams.task.execContextId = execContextId;
        taskParams.task.taskContextId = taskContextId;
        taskParams.task.processCode = process.processCode;
        taskParams.task.context = process.function.context;
        taskParams.task.metas.addAll(process.metas);
        taskParams.task.inline = inlines;

        if (taskParams.task.context== EnumsApi.FunctionExecContext.internal) {
            taskParams.task.function = new TaskParamsYaml.FunctionConfig(
                    process.function.code, "internal", null, S.b(process.function.params) ? "" : process.function.params,"internal",
                    EnumsApi.FunctionSourcing.dispatcher, null,
                    null, false, null );
        }
        else {
            TaskParamsYaml.FunctionConfig fConfig = functionTopLevelService.getFunctionConfig(process.function);
            if (fConfig == null) {
                log.error("#375.140 Function '{}' wasn't found", process.function.code);
                return null;
            }
            taskParams.task.function = fConfig;
            if (process.getPreFunctions()!=null) {
                for (ExecContextParamsYaml.FunctionDefinition preFunction : process.getPreFunctions()) {
                    taskParams.task.preFunctions.add(functionTopLevelService.getFunctionConfig(preFunction));
                }
            }
            if (process.getPostFunctions()!=null) {
                for (ExecContextParamsYaml.FunctionDefinition postFunction : process.getPostFunctions()) {
                    taskParams.task.postFunctions.add(functionTopLevelService.getFunctionConfig(postFunction));
                }
            }
        }
        taskParams.task.clean = execContextParamsYaml.clean;
        taskParams.task.timeoutBeforeTerminate = process.timeoutBeforeTerminate;
        if (process.cache!=null) {
            taskParams.task.cache = new TaskParamsYaml.Cache(process.cache.enabled, process.cache.omitInline);
        }

        String params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(taskParams);

        TaskImpl task = new TaskImpl();
        task.execState = process.cache!=null && process.cache.enabled ? EnumsApi.TaskExecState.CHECK_CACHE.value : EnumsApi.TaskExecState.NONE.value;
        task.execContextId = execContextId;
        task.params = params;
        task = taskService.save(task);

        task = variableService.prepareVariables(execContextParamsYaml, task);
        return task;
    }

}
