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
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.ai.dispatcher.repositories.IdsRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskProducingService {

    private final TaskRepository taskRepository;
    private final FunctionService functionService;
    private final VariableService variableService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final InternalFunctionProcessor internalFunctionProcessor;
    private final IdsRepository idsRepository;
    private final ExecContextProcessGraphService execContextProcessGraphService;

    public TaskData.ProduceTaskResult produceTasks(boolean isPersist, Long sourceCodeId, Long execContextId, ExecContextParamsYaml execContextParamsYaml) {
        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = execContextProcessGraphService.importProcessGraph(execContextParamsYaml);

        List<Long> parentTaskIds = new ArrayList<>();
        for (ExecContextData.ProcessVertex processVertex : processGraph) {
            String processCode = processVertex.process;
            ExecContextParamsYaml.Process p;

            p = execContextParamsYaml.processes.stream().filter(o -> o.processCode.equals(processCode)).findAny().orElse(null);
            if (p == null) {
                // mh.finish can be not defined in sourceCode
                if (processCode.equals(Consts.MH_FINISH_FUNCTION)) {
                    p = new ExecContextParamsYaml.Process(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION, ""+execContextId, "1",
                            Consts.MH_FINISH_FUNCTION_INSTANCE);
                }
                else {
                    return new TaskData.ProduceTaskResult(EnumsApi.TaskProducingStatus.PROCESS_NOT_FOUND_ERROR);
                }
            }
            TaskData.ProduceTaskResult result = produceTaskForProcess(isPersist, sourceCodeId, p, execContextParamsYaml, execContextId, parentTaskIds);
            if (result.status!= EnumsApi.TaskProducingStatus.OK) {
                return result;
            }
        }
        return new TaskData.ProduceTaskResult(EnumsApi.TaskProducingStatus.OK);
    }


    private TaskData.ProduceTaskResult produceTaskForProcess(
            boolean isPersist, Long sourceCodeId, ExecContextParamsYaml.Process process,
            ExecContextParamsYaml execContextParamsYaml, Long execContextId,
            List<Long> parentTaskIds) {

        TaskData.ProduceTaskResult result = new TaskData.ProduceTaskResult();

        if (isPersist) {
            Task t = createTaskInternal(execContextParamsYaml, execContextId, process, process.function);
            if (t!=null) {
                result.taskId = t.getId();
                execContextGraphTopLevelService.addNewTasksToGraph(execContextId, parentTaskIds, List.of(result.taskId));
            }
            else {
                result.status = EnumsApi.TaskProducingStatus.TASK_PRODUCING_ERROR;
                return result;
            }
        }
        result.numberOfTasks++;
        result.status = EnumsApi.TaskProducingStatus.OK;
        return result;
    }

    private TaskImpl createTaskInternal(
            ExecContextParamsYaml execContextParamsYaml, Long execContextId, ExecContextParamsYaml.Process process,
            ExecContextParamsYaml.FunctionDefinition snDef) {

        TaskParamsYaml taskParams = new TaskParamsYaml();
        taskParams.task.execContextId = execContextId;
        taskParams.task.processCode = process.processCode;
        taskParams.task.context = process.function.context;

        taskParams.task.function = functionService.getFunctionConfig(snDef);
        if (taskParams.task.function ==null) {
            log.error("#171.07 Function wasn't found for code: {}", snDef.code);
            return null;
        }
        taskParams.task.preFunctions = new ArrayList<>();
        if (process.getPreFunctions()!=null) {
            for (ExecContextParamsYaml.FunctionDefinition preFunction : process.getPreFunctions()) {
                taskParams.task.preFunctions.add(functionService.getFunctionConfig(preFunction));
            }
        }
        taskParams.task.postFunctions = new ArrayList<>();
        if (process.getPostFunctions()!=null) {
            for (ExecContextParamsYaml.FunctionDefinition postFunction : process.getPostFunctions()) {
                taskParams.task.postFunctions.add(functionService.getFunctionConfig(postFunction));
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
