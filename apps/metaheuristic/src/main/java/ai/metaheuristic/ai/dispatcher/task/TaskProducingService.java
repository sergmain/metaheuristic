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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.ai.exceptions.TaskCreationException;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.dispatcher.Task;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;

import static ai.metaheuristic.api.EnumsApi.FunctionExecContext;
import static ai.metaheuristic.api.EnumsApi.TaskProducingStatus;

@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskProducingService {

    private final TaskProducingCoreService taskProducingCoreService;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;
    private final InternalFunctionProcessor internalFunctionProcessor;

    public TaskData.ProduceTaskResult produceTasks(boolean isPersist, Long sourceCodeId, Long execContextId, ExecContextParamsYaml execContextParamsYaml) {
        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(execContextParamsYaml);

        TaskData.ProduceTaskResult okResult = new TaskData.ProduceTaskResult(TaskProducingStatus.OK, null);
        Map<String, List<Long>> parentProcesses = new HashMap<>();
        for (ExecContextData.ProcessVertex processVertex : processGraph) {
            String processCode = processVertex.process;
            ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(processCode);
            if (p == null) {
                // mh.finish can be omitted in sourceCode
                if (processCode.equals(Consts.MH_FINISH_FUNCTION)) {
                    p = new ExecContextParamsYaml.Process(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION, "1",
                            Consts.MH_FINISH_FUNCTION_INSTANCE);
                }
                else {
                    return new TaskData.ProduceTaskResult(TaskProducingStatus.PROCESS_NOT_FOUND_ERROR, "#375.020 Process '"+processCode+"' wasn't found");
                }
            }
            if (internalFunctionProcessor.isRegistered(p.function.code) && p.function.context!= FunctionExecContext.internal) {
                return new TaskData.ProduceTaskResult(TaskProducingStatus.INTERNAL_FUNCTION_DECLARED_AS_EXTERNAL_ERROR,
                        "#375.040 Process '"+processCode+"' must be internal");
            }
            Set<ExecContextData.ProcessVertex> ancestors = ExecContextProcessGraphService.findAncestors(processGraph, processVertex);
            ExecContextParamsYaml.Process internalFuncProcess = checkForInternalFunctions(execContextParamsYaml, ancestors, p);

            if (internalFuncProcess!=null) {
                log.info(S.f("There is ancestor which is internal function: %s, process: %s", internalFuncProcess.function.code, internalFuncProcess.processCode));
                continue;
            }


            List<Long> parentTaskIds = new ArrayList<>();
            processGraph.incomingEdgesOf(processVertex).stream()
                    .map(processGraph::getEdgeSource)
                    .map(ancestor -> parentProcesses.get(ancestor.process))
                    .filter(Objects::nonNull)
                    .forEach(parentTaskIds::addAll);

            TaskData.ProduceTaskResult result = produceTaskForProcess(isPersist, sourceCodeId, p, execContextParamsYaml, execContextId, parentTaskIds);
            if (result.status!= TaskProducingStatus.OK) {
                return result;
            }
            parentProcesses.computeIfAbsent(p.processCode, o->new ArrayList<>()).add(result.taskId);
            okResult.numberOfTasks += result.numberOfTasks;
        }
        return okResult;
    }

    private @Nullable ExecContextParamsYaml.Process checkForInternalFunctions(ExecContextParamsYaml execContextParamsYaml, Set<ExecContextData.ProcessVertex> ancestors, ExecContextParamsYaml.Process currProcess) {
        for (ExecContextData.ProcessVertex ancestor : ancestors) {
            ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(ancestor.process);
            if (p==null) {
                log.warn("#375.060 Unusual state, need to investigate");
                continue;
            }
            if (!currProcess.internalContextId.startsWith(p.internalContextId)) {
                continue;
            }
            if (p.internalContextId.equals(currProcess.internalContextId)) {
                continue;
            }
            if (p.function.context== FunctionExecContext.internal) {
                return p;
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private TaskData.ProduceTaskResult produceTaskForProcess(
            boolean isPersist, Long sourceCodeId, ExecContextParamsYaml.Process process,
            ExecContextParamsYaml execContextParamsYaml, Long execContextId,
            List<Long> parentTaskIds) {

        TaskData.ProduceTaskResult result = new TaskData.ProduceTaskResult();

        if (isPersist) {
            // for external Functions internalContextId==process.internalContextId
            try {
                Task t = taskProducingCoreService.createTaskInternal(execContextId, execContextParamsYaml, process, process.internalContextId,
                        execContextParamsYaml.variables.inline);
                if (t == null) {
                    result.status = TaskProducingStatus.TASK_PRODUCING_ERROR;
                    result.error = "#375.080 Unknown reason of error while task creation";
                    return result;
                }
                result.taskId = t.getId();
                execContextGraphTopLevelService.addNewTasksToGraph(execContextId, parentTaskIds, List.of(t.getId()));
            } catch (TaskCreationException e) {
                result.status = TaskProducingStatus.TASK_PRODUCING_ERROR;
                result.error = "#375.100 task creation error " + e.getMessage();
                log.error(result.error);
                return result;
            }
        }
        result.numberOfTasks=1;
        result.status = TaskProducingStatus.OK;
        return result;
    }


}
