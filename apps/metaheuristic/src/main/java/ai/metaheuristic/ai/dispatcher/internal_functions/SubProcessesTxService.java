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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.commons.utils.ContextUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/24/2021
 * Time: 11:36 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SubProcessesTxService {

    private final InternalFunctionService internalFunctionService;
    private final TaskProducingService taskProducingService;
    private final ExecContextGraphService execContextGraphService;

    @Transactional
    public Void processSubProcesses(ExecContextApiData.SimpleExecContext simpleExecContext, Long taskId, TaskParamsYaml taskParamsYaml) {
        InternalFunctionData.ExecutionContextData executionContextData = internalFunctionService.getSubProcesses(simpleExecContext, taskParamsYaml, taskId);
        if (executionContextData.internalFunctionProcessingResult.processing!= Enums.InternalFunctionProcessing.ok) {
            throw new InternalFunctionException(executionContextData.internalFunctionProcessingResult);
        }

        if (executionContextData.subProcesses.isEmpty()) {
            return null;
        }

        final List<Long> lastIds = new ArrayList<>();
        String subProcessContextId = ContextUtils.getCurrTaskContextIdForSubProcesses(
                taskParamsYaml.task.taskContextId, executionContextData.subProcesses.get(0).processContextId);

        String currTaskContextId = ContextUtils.buildTaskContextId(subProcessContextId, "0");

        // Detect old dynamically-created children from a previous execution of this task.
        // When a task with subProcesses is reset and re-executed, findDirectDescendants returns
        // both old children (from the prior run) and real downstream tasks. Old children have
        // a taskContextId that matches the subProcess context pattern. We must remove them and
        // their entire sub-layer subtree from the graph, then reconnect the new subProcess chain
        // to the real downstream tasks.
        //
        // For sequential logic, all children share the same subProcess context prefix (e.g. "1,2,5|1#").
        // For parallel (and) logic, each child gets the raw processContextId (e.g. "1,2,5", "1,2,13"),
        // so we must check against each subProcess's expected taskContextId.
        Set<ExecContextData.TaskVertex> oldChildren;
        if (executionContextData.process.logic == ai.metaheuristic.api.EnumsApi.SourceCodeSubProcessLogic.and) {
            // Parallel: each subProcess child gets processContextId directly as taskContextId
            Set<String> expectedChildCtxIds = executionContextData.subProcesses.stream()
                    .map(sp -> sp.processContextId)
                    .collect(Collectors.toSet());
            oldChildren = executionContextData.descendants.stream()
                    .filter(v -> v.taskContextId != null && expectedChildCtxIds.contains(v.taskContextId))
                    .collect(Collectors.toSet());
        }
        else {
            // Sequential: children get derived contexts like "1,2,5|1#0", "1,2,5|1#1"
            String subProcessCtxPrefix = subProcessContextId + ContextUtils.CONTEXT_SEPARATOR;
            oldChildren = executionContextData.descendants.stream()
                    .filter(v -> v.taskContextId != null && v.taskContextId.startsWith(subProcessCtxPrefix))
                    .collect(Collectors.toSet());
        }

        Set<ExecContextData.TaskVertex> filteredDescendants;
        if (!oldChildren.isEmpty()) {
            Set<Long> oldChildIds = oldChildren.stream().map(v -> v.taskId).collect(Collectors.toSet());
            log.info("995.100 Detected {} old subProcess children of task #{}, removing from graph. Old child taskIds: {}",
                    oldChildren.size(), taskId, oldChildIds);
            filteredDescendants = new java.util.LinkedHashSet<>(executionContextData.descendants.stream()
                    .filter(v -> !oldChildIds.contains(v.taskId))
                    .collect(Collectors.toSet()));
        }
        else {
            filteredDescendants = executionContextData.descendants;
        }

        try {
            ExecContextData.GraphAndStates graphAndStates = execContextGraphService.prepareGraphAndStates(simpleExecContext.execContextGraphId, simpleExecContext.execContextTaskStateId);

            // Remove old children and their entire sub-layer subtree from graph before creating new tasks.
            // Uses deriveParentTaskContextId walk to identify all sub-layers belonging to this wrapper.
            // Collects downstream vertices (e.g. mh.finish) that the subtree pointed to — they need to be reconnected.
            if (!oldChildren.isEmpty()) {
                Set<ExecContextData.TaskVertex> removedVertices = new java.util.LinkedHashSet<>();
                Set<ExecContextData.TaskVertex> downstreamOfOldChildren = execContextGraphService.removeOldSubProcessChildren(
                        graphAndStates.graph(), oldChildren, taskParamsYaml.task.taskContextId, removedVertices);
                // Add downstream vertices to filteredDescendants so createEdges reconnects them
                filteredDescendants.addAll(downstreamOfOldChildren);

                // Clean up stale task state entries for all removed vertices (old children + their subtree)
                ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml stateParams =
                        graphAndStates.states().getExecContextTaskStateParamsYaml();
                for (ExecContextData.TaskVertex removed : removedVertices) {
                    stateParams.states.remove(removed.taskId);
                }
                graphAndStates.states().updateParams(stateParams);
            }

            taskProducingService.createTasksForSubProcesses(
                graphAndStates, simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds);

            execContextGraphService.createEdges(graphAndStates.graph(), lastIds, filteredDescendants);

        } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
            throw e;
        } catch (Throwable th) {
            String es = "995.300 An error while saving data to file, " + th.getMessage();
            log.error(es, th);
            throw new BatchResourceProcessingException(es);
        }
        return null;
    }
}
