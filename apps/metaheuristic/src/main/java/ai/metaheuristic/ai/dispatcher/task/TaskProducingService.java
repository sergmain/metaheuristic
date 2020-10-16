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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.function.FunctionTopLevelService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.utils.ContextUtils;
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
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskProducingService {

    private final VariableService variableService;
    private final ExecContextGraphService execContextGraphService;
    private final FunctionTopLevelService functionTopLevelService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskService taskService;

    public TaskData.ProduceTaskResult produceTaskForProcess(
            ExecContextParamsYaml.Process process,
            ExecContextParamsYaml execContextParamsYaml, ExecContextImpl execContext,
            List<Long> parentTaskIds) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContext.id);

        TaskData.ProduceTaskResult result = new TaskData.ProduceTaskResult();

        // for external Functions internalContextId==process.internalContextId
        TaskImpl t = createTaskInternal(execContext.id, execContextParamsYaml, process, process.internalContextId,
                execContextParamsYaml.variables.inline);
        if (t == null) {
            return new TaskData.ProduceTaskResult(
                    EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR, "#375.080 Unknown reason of error while task creation");
        }

        TaskImpl task = variableService.prepareVariables(execContextParamsYaml, t);
        if (task == null) {
            return new TaskData.ProduceTaskResult(
                    EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR, "#303.640 The task is null after prepareVariables(task)");
        }

        result.taskId = t.getId();
        List<TaskApiData.TaskWithContext> taskWithContexts = List.of(new TaskApiData.TaskWithContext( t.getId(), process.internalContextId));
        execContextGraphService.addNewTasksToGraph(execContext, parentTaskIds, taskWithContexts);

        result.status = EnumsApi.TaskProducingStatus.OK;
        return result;
    }

    /**
     * @param execContext
     * @param currTaskNumber
     * @param parentTaskId
     * @param lastIds
     */
    public void createTasksForSubProcesses(
            ExecContextImpl execContext, InternalFunctionData.ExecutionContextData executionContextData,
            AtomicInteger currTaskNumber, Long parentTaskId, List<Long> lastIds) {
        TxUtils.checkTxExists();

        ExecContextParamsYaml execContextParamsYaml = executionContextData.execContextParamsYaml;
        List<ExecContextData.ProcessVertex> subProcesses = executionContextData.subProcesses;
        if (subProcesses.isEmpty()) {
            log.info("#995.330 There isn't any subProcess");
            return;
        }

        Map<String, Map<String, String>> inlines = executionContextData.execContextParamsYaml.variables.inline;
        ExecContextParamsYaml.Process process = executionContextData.process;

        if (process.logic!= EnumsApi.SourceCodeSubProcessLogic.sequential) {
            throw new BreakFromLambdaException("#995.340 only the 'sequential' logic is supported");
        }

        List<Long> parentTaskIds = List.of(parentTaskId);
        String subProcessContextId = executionContextData.subProcesses.get(0).processContextId;

        TaskImpl t = null;
        for (ExecContextData.ProcessVertex subProcess : subProcesses) {
            final ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(subProcess.process);
            if (p==null) {
                throw new BreakFromLambdaException("#995.320 Process '" + subProcess.process + "' wasn't found");
            }

            // all subProcesses must have the same processContextId
            if (!subProcessContextId.equals(subProcess.processContextId)) {
                throw new BreakFromLambdaException("#995.360 Different contextId, prev: "+ subProcessContextId+", next: " +subProcess.processContextId);
            }

            String currTaskContextId = ContextUtils.getTaskContextId(subProcess.processContextId, Integer.toString(currTaskNumber.get()));

            t = createTaskInternal(execContext.id, execContextParamsYaml, p, currTaskContextId, inlines);

            if (t==null) {
                throw new BreakFromLambdaException("#995.380 Creation of task failed");
            }
            List<TaskApiData.TaskWithContext> currTaskIds = List.of(new TaskApiData.TaskWithContext(t.getId(), currTaskContextId));
            execContextGraphService.addNewTasksToGraph(execContext, parentTaskIds, currTaskIds);
            parentTaskIds = List.of(t.getId());
            subProcessContextId = subProcess.processContextId;
        }
        lastIds.add(t.id);
    }

    @Nullable
    public TaskImpl createTaskInternal(
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
                    null, false );
        }
        else {
            TaskParamsYaml.FunctionConfig fConfig = functionTopLevelService.getFunctionConfig(process.function);
            if (fConfig == null) {
                log.error("#171.020 Function '{}' wasn't found", process.function.code);
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

        String params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(taskParams);

        TaskImpl task = new TaskImpl();
        task.execContextId = execContextId;
        task.params = params;
        task = taskService.save(task);

        task = variableService.prepareVariables(execContextParamsYaml, task);
        return task;
    }

}
