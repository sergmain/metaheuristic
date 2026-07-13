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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.commons.graph.ExecContextProcessGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import ai.metaheuristic.commons.utils.ContextUtils;
import java.util.ArrayList;
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
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class InternalFunctionService {

    private final SourceCodeCache sourceCodeCache;
    private final ExecContextGraphService execContextGraphService;

    public InternalFunctionData.ExecutionContextData getSubProcesses(ExecContextApiData.SimpleExecContext simpleExecContext, TaskParamsYaml taskParamsYaml, Long taskId) {
        SourceCodeImpl sourceCode = sourceCodeCache.findById(simpleExecContext.sourceCodeId);
        if (sourceCode==null) {
            return new InternalFunctionData.ExecutionContextData(
                    new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error,
                    "994.200 sourceCode wasn't found, sourceCodeId: " + simpleExecContext.sourceCodeId));
        }
        Set<TaskVertex> descendants = execContextGraphService.findDirectDescendants(simpleExecContext.execContextGraphId, taskId);
        if (descendants.isEmpty()) {
            return new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.broken_graph_error,
                    "994.240 Graph for ExecContext #"+simpleExecContext.execContextId+" is broken"));
        }

        final ExecContextParamsYaml.Process process = simpleExecContext.paramsYaml.findProcess(taskParamsYaml.task.processCode);
        if (process==null) {
            return new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.source_code_is_broken,
                    "994.260 Process '"+taskParamsYaml.task.processCode+"'not found"));
        }

        DirectedAcyclicGraph<ExecContextApiData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(simpleExecContext.paramsYaml);
        List<ExecContextApiData.ProcessVertex> subProcesses = ExecContextProcessGraphService.findSubProcesses(processGraph, process.processCode);

        // Phase 6a option 2 (B): a grafted group-body process is NOT in the main process graph, so
        // findSubProcesses returns empty for it. Resolve its DIRECT children from the group body instead,
        // rebasing each child's context under this task's ACTUAL (rebased) context so the child nests
        // under it at runtime (deriveParentTaskContextId walks back). For a plain main-pipeline leaf this
        // is a no-op (no group body has a child of a main process's ctx; group root ctxs are unique).
        if (subProcesses.isEmpty()) {
            subProcesses = groupBodySubProcesses(simpleExecContext.paramsYaml, process, taskParamsYaml.task.taskContextId);
        }

        return new InternalFunctionData.ExecutionContextData(
                new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.ok),
                subProcesses, process, simpleExecContext.paramsYaml, descendants);
    }


    /** Phase 6a (B): the DIRECT children of a group-body process, taken from group.body and rebased under
     *  the running task's process context. Direct child = a body process whose internalContextId extends
     *  the parent's by exactly one segment. Rebase: runtimeProcessCtx + (childCtx stripped of the parent's
     *  compiled ctx prefix), e.g. parent compiled '3' running at '1,3' + child '3,7' -> '1,3,7'. */
    static List<ExecContextApiData.ProcessVertex> groupBodySubProcesses(
            ExecContextParamsYaml py, ExecContextParamsYaml.Process process, String runningTaskContextId) {
        final String parentCompiledCtx = process.internalContextId;
        final String runtimeProcessCtx = ContextUtils.getProcessContextId(ContextUtils.getLevel(runningTaskContextId));
        final String childPrefix = parentCompiledCtx + ContextUtils.CONTEXT_DIGIT_SEPARATOR;
        final List<ExecContextApiData.ProcessVertex> result = new ArrayList<>();
        long vid = 0;
        for (ExecContextParamsYaml.Group g : py.groups) {
            // Locate THIS process in the group body by processCode. The body is stored in DFS pre-order
            // (compilation order - a process is appended before its own sub-block is compiled), so a
            // process's whole subtree is the CONTIGUOUS run of entries that immediately follows it and
            // stays strictly under its ctx. Scoping to that subtree is what keeps a sequential wrapper
            // from ALSO picking up its sequential SIBLINGS' children: the compiler lays sequential
            // siblings at one and the same internalContextId, so their respective children would all
            // match the same 'parentCtx,' prefix - a plain prefix scan cannot tell them apart.
            int idx = -1;
            for (int i = 0; i < g.body.size(); i++) {
                if (g.body.get(i).processCode.equals(process.processCode)) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) {
                continue;
            }
            for (int i = idx + 1; i < g.body.size(); i++) {
                final String qCtx = g.body.get(i).internalContextId;
                if (!qCtx.startsWith(childPrefix)) {
                    // left THIS process's subtree (pre-order): a sibling (same ctx) or a shallower ctx
                    // ends the scan. Deeper descendants of a child keep the prefix and do not break here.
                    break;
                }
                // direct child only: the remainder after the parent prefix has no further separator
                if (qCtx.indexOf(ContextUtils.CONTEXT_DIGIT_SEPARATOR, childPrefix.length()) != -1) {
                    continue;
                }
                final String rebasedCtx = runtimeProcessCtx + qCtx.substring(parentCompiledCtx.length());
                result.add(new ExecContextApiData.ProcessVertex(vid++, g.body.get(i).processCode, rebasedCtx));
            }
            return result;
        }
        return result;
    }

}
