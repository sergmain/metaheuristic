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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskProducingService {

    private final TaskRepository taskRepository;
    private final FunctionService functionService;
    private final VariableService variableService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final ExecContextProcessGraphService execContextProcessGraphService;

    public TaskData.ProduceTaskResult produceTasks(boolean isPersist, Long sourceCodeId, Long execContextId, ExecContextParamsYaml execContextParamsYaml) {
        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = execContextProcessGraphService.importProcessGraph(execContextParamsYaml);

        TaskData.ProduceTaskResult okResult = new TaskData.ProduceTaskResult(EnumsApi.TaskProducingStatus.OK);
        Map<String, List<Long>> parentProcesses = new HashMap<>();
        for (ExecContextData.ProcessVertex processVertex : processGraph) {
            String processCode = processVertex.process;
            ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(processCode);
            if (p == null) {
                // mh.finish can be not defined in sourceCode
                if (processCode.equals(Consts.MH_FINISH_FUNCTION)) {
                    p = new ExecContextParamsYaml.Process(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION, "1",
                            Consts.MH_FINISH_FUNCTION_INSTANCE);
                }
                else {
                    return new TaskData.ProduceTaskResult(EnumsApi.TaskProducingStatus.PROCESS_NOT_FOUND_ERROR);
                }
            }
            if (Consts.MH_INTERNAL_FUNCTIONS.contains(p.function.code) && p.function.context!=EnumsApi.FunctionExecContext.internal) {
                return new TaskData.ProduceTaskResult(EnumsApi.TaskProducingStatus.INTERNAL_FUNCTION_DECLARED_AS_EXTERNAL_ERROR);
            }

            List<Long> parentTaskIds = new ArrayList<>();
            processGraph.incomingEdgesOf(processVertex).stream()
                    .map(processGraph::getEdgeSource)
                    .map(ancestor -> parentProcesses.get(ancestor.process))
                    .filter(Objects::nonNull)
                    .forEach(parentTaskIds::addAll);

            TaskData.ProduceTaskResult result = produceTaskForProcess(isPersist, sourceCodeId, p, execContextParamsYaml, execContextId, parentTaskIds);
            if (result.status!= EnumsApi.TaskProducingStatus.OK) {
                return result;
            }
            parentProcesses.computeIfAbsent(p.processCode, o->new ArrayList<>()).add(result.taskId);
            okResult.numberOfTasks += result.numberOfTasks;
        }
        return okResult;
    }


    private TaskData.ProduceTaskResult produceTaskForProcess(
            boolean isPersist, Long sourceCodeId, ExecContextParamsYaml.Process process,
            ExecContextParamsYaml execContextParamsYaml, Long execContextId,
            List<Long> parentTaskIds) {

        TaskData.ProduceTaskResult result = new TaskData.ProduceTaskResult();

        if (isPersist) {
            Task t = createTaskInternal(execContextParamsYaml, execContextId, process, process.function);
            if (t == null) {
                result.status = EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR;
                return result;
            }
            result.taskId = t.getId();
            execContextGraphTopLevelService.addNewTasksToGraph(execContextId, parentTaskIds, List.of(t.getId()));
        }
        result.numberOfTasks=1;
        result.status = EnumsApi.TaskProducingStatus.OK;
        return result;
    }

    @Nullable
    private TaskImpl createTaskInternal(
            ExecContextParamsYaml execContextParamsYaml, Long execContextId, ExecContextParamsYaml.Process process,
            ExecContextParamsYaml.FunctionDefinition snDef) {

        TaskParamsYaml taskParams = new TaskParamsYaml();
        taskParams.task.execContextId = execContextId;
        taskParams.task.processCode = process.processCode;
        taskParams.task.context = process.function.context;

        if (taskParams.task.context==EnumsApi.FunctionExecContext.internal) {
            taskParams.task.function = new TaskParamsYaml.FunctionConfig(
                    process.function.code, "internal", null, S.b(process.function.params) ? "" : process.function.params,"internal",
                    EnumsApi.FunctionSourcing.dispatcher, null,
                    new TaskParamsYaml.FunctionInfo(false, 0), null, null, false );
        }
        else {
            TaskParamsYaml.FunctionConfig fConfig = functionService.getFunctionConfig(snDef);
            if (fConfig == null) {
                log.error("#171.07 Function wasn't found for code: {}", snDef.code);
                return null;
            }
            taskParams.task.function = fConfig;
            if (process.getPreFunctions()!=null) {
                for (ExecContextParamsYaml.FunctionDefinition preFunction : process.getPreFunctions()) {
                    taskParams.task.preFunctions.add(functionService.getFunctionConfig(preFunction));
                }
            }
            if (process.getPostFunctions()!=null) {
                for (ExecContextParamsYaml.FunctionDefinition postFunction : process.getPostFunctions()) {
                    taskParams.task.postFunctions.add(functionService.getFunctionConfig(postFunction));
                }
            }
        }
        taskParams.task.clean = execContextParamsYaml.clean;
        taskParams.task.timeoutBeforeTerminate = process.timeoutBeforeTerminate;

        String params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(taskParams);

        TaskImpl task = new TaskImpl();
        task.setExecContextId(execContextId);
        task.setParams(params);
        taskRepository.save(task);

        return task;
    }

}
