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
import ai.metaheuristic.ai.dispatcher.beans.GlobalVariable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.ai.dispatcher.repositories.GlobalVariableRepository;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariableAndStorageUrl;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskProducingService {

    private final VariableService variableService;
    private final TaskProducingCoreService taskProducingCoreService;
    private final GlobalVariableRepository globalVariableRepository;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final ExecContextProcessGraphService execContextProcessGraphService;
    private final InternalFunctionProcessor internalFunctionProcessor;

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
            if (internalFunctionProcessor.isRegistered(p.function.code) && p.function.context!=EnumsApi.FunctionExecContext.internal) {
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
            Task t = taskProducingCoreService.createTaskInternal(execContextId, execContextParamsYaml, process);
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

    public TaskParamsYaml.InputVariable toInputVariable(ExecContextParamsYaml.Variable v, String internalContextId, Long execContextId) {
        TaskParamsYaml.InputVariable iv = new TaskParamsYaml.InputVariable();
        if (v.context== EnumsApi.VariableContext.local) {
            String contextId = Boolean.TRUE.equals(v.parentContextId) ? VariableService.getParentContext(internalContextId) : internalContextId;
            if (S.b(contextId)) {
                throw new IllegalStateException(
                        S.f("(S.b(contextId)), name: %s, variableContext: %s, internalContextId: %s, execContextId: %s",
                                v.name, v.context, internalContextId, execContextId));
            }
            SimpleVariableAndStorageUrl variable = variableService.findVariableInAllInternalContexts(v.name, contextId, execContextId);
            if (variable==null) {
                throw new IllegalStateException(
                        S.f("(variable==null), name: %s, variableContext: %s, internalContextId: %s, execContextId: %s",
                                v.name, v.context, internalContextId, execContextId));
            }
            iv.id = variable.id;
            iv.realName = variable.originalFilename;
        }
        else {
            GlobalVariable variable = globalVariableRepository.findIdByName(v.name);
            if (variable==null) {
                throw new IllegalStateException(
                        S.f("(variable==null), name: %s, variableContext: %s, internalContextId: %s, execContextId: %s",
                                v.name, v.context, internalContextId, execContextId));
            }
            iv.id = variable.id.toString();
        }
        iv.context = v.context;
        iv.name = v.name;
        iv.sourcing = v.sourcing;
        iv.disk = v.disk;
        iv.git = v.git;
        return iv;
    }

}
