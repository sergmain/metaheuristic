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
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextGraphTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingCoreService;
import ai.metaheuristic.ai.dispatcher.variable.SimpleVariable;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.dispatcher.variable.VariableUtils;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        Set<ExecContextData.TaskVertex> descendants = execContextGraphTopLevelService.findDescendants(ec, taskId);
        if (descendants.isEmpty()) {
            return new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.broken_graph_error,
                    "#994.240 Graph for ExecContext #"+execContextId+" is broken"));
        }

        ExecContextParamsYaml execContextParamsYaml = ec.getExecContextParamsYaml();
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
