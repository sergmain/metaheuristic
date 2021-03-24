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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static ai.metaheuristic.ai.dispatcher.data.ExecContextData.*;

/**
 * @author Serge
 * Date: 9/12/2020
 * Time: 3:26 AM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class InternalFunctionService {

    private final SourceCodeCache sourceCodeCache;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

    public InternalFunctionData.ExecutionContextData getSubProcesses(SimpleExecContext simpleExecContext, TaskParamsYaml taskParamsYaml, Long taskId) {
        SourceCodeImpl sourceCode = sourceCodeCache.findById(simpleExecContext.sourceCodeId);
        if (sourceCode==null) {
            return new InternalFunctionData.ExecutionContextData(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "#994.200 sourceCode wasn't found, sourceCodeId: " + simpleExecContext.sourceCodeId));
        }
        Set<TaskVertex> descendants = execContextGraphTopLevelService.findDirectDescendants(simpleExecContext.execContextGraphId, taskId);
        if (descendants.isEmpty()) {
            return new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.broken_graph_error,
                    "#994.240 Graph for ExecContext #"+simpleExecContext.execContextId+" is broken"));
        }

        final ExecContextParamsYaml.Process process = simpleExecContext.paramsYaml.findProcess(taskParamsYaml.task.processCode);
        if (process==null) {
            return new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                    "#994.260 Process '"+taskParamsYaml.task.processCode+"'not found"));
        }

        DirectedAcyclicGraph<ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(simpleExecContext.paramsYaml);
        List<ProcessVertex> subProcesses = ExecContextProcessGraphService.findSubProcesses(processGraph, process.processCode);

        return new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.ok),
                subProcesses, process, simpleExecContext.paramsYaml, descendants);
    }

}
