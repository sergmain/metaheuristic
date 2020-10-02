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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
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
    private final ExecContextCache execContextCache;
    private final ExecContextGraphTopLevelService execContextGraphTopLevelService;

    public InternalFunctionData.ExecutionContextData getSupProcesses(Long sourceCodeId, Long execContextId, TaskParamsYaml taskParamsYaml, Long taskId) {
        SourceCodeImpl sourceCode = sourceCodeCache.findById(sourceCodeId);
        if (sourceCode==null) {
            return new InternalFunctionData.ExecutionContextData(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "#994.200 sourceCode wasn't found, sourceCodeId: " + sourceCodeId));
        }
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            return new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.exec_context_not_found,
                    "#994.220 execContext wasn't found, execContextId: " + execContextId));
        }
        Set<ExecContextData.TaskVertex> descendants = execContextGraphTopLevelService.findDirectDescendants(ec, taskId);
        if (descendants.isEmpty()) {
            return new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.broken_graph_error,
                    "#994.240 Graph for ExecContext #"+execContextId+" is broken"));
        }

        ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(ec.params);
        final ExecContextParamsYaml.Process process = execContextParamsYaml.findProcess(taskParamsYaml.task.processCode);
        if (process==null) {
            return new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                    "#994.260 Process '"+taskParamsYaml.task.processCode+"'not found"));
        }

        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(execContextParamsYaml);
        List<ExecContextData.ProcessVertex> subProcesses = ExecContextProcessGraphService.findSubProcesses(processGraph, process.processCode);

        return new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.ok),
                subProcesses, process, execContextParamsYaml, descendants);
    }

}
