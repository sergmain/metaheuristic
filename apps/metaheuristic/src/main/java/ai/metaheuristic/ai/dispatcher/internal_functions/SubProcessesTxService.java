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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextSegmentTxService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProducingService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.exceptions.BatchProcessingException;
import ai.metaheuristic.ai.exceptions.BatchResourceProcessingException;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.commons.utils.ContextUtils;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.GraftExpander;
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
    private final GraftExpander graftExpander;
    private final TaskProducingService taskProducingService;
    private final ExecContextSegmentTxService segmentTxService;
    private final ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextSegmentReadService segmentReadService;
    private final TaskRepository taskRepository;
    private final ExecContextCache execContextCache;

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
        // For parallel (and) logic, each branch gets a derived context via
        // getCurrTaskContextIdForSubProcesses(parentTaskCtx, branchProcessContextId) + "#branchIdx",
        // so we compute per-branch prefixes and match against them.
        Set<ExecContextData.TaskVertex> oldChildren;
        if (executionContextData.process.logic == ai.metaheuristic.api.EnumsApi.SourceCodeSubProcessLogic.and) {
            // Parallel: each branch gets getCurrTaskContextIdForSubProcesses(parent, branch) + #idx
            Set<String> expectedPrefixes = executionContextData.subProcesses.stream()
                    .map(sp -> ContextUtils.getCurrTaskContextIdForSubProcesses(
                            taskParamsYaml.task.taskContextId, sp.processContextId) + ContextUtils.CONTEXT_SEPARATOR)
                    .collect(Collectors.toSet());
            oldChildren = executionContextData.descendants.stream()
                    .filter(v -> v.taskContextId != null &&
                            expectedPrefixes.stream().anyMatch(prefix -> v.taskContextId.startsWith(prefix)))
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
            // 041 Phase 21: nothing whole-ExecContext is loaded - the subtree and the new lines live in segments

            // Remove old children and their entire sub-layer subtree from graph before creating new tasks.
            // Uses deriveParentTaskContextId walk to identify all sub-layers belonging to this wrapper.
            // Collects downstream vertices (e.g. mh.finish) that the subtree pointed to — they need to be reconnected.
            if (!oldChildren.isEmpty()) {
                // 041 Phase 11: the subtree comes from the segments and leaves them (removeLines); no edge is reconnected -
                // joins are derived
                final Set<Long> subtree = oldSubtree(simpleExecContext.execContextId, oldChildren, taskParamsYaml.task.taskContextId);
                segmentTxService.removeLines(simpleExecContext.execContextId, subtree);
                Set<ExecContextData.TaskVertex> removedVertices = new java.util.LinkedHashSet<>();
                subtree.forEach(id -> removedVertices.add(new ExecContextData.TaskVertex(id)));
                // Add downstream vertices to filteredDescendants so createEdges reconnects them

                // Clean up stale task state entries for all removed vertices (old children + their subtree)
                for (ExecContextData.TaskVertex removed : removedVertices) {

                    // Mark task as SKIPPED in DB so async internal function processing won't pick it up
                    TaskSyncService.getWithSyncVoid(removed.taskId, () -> {
                        TaskImpl task = taskRepository.findById(removed.taskId).orElse(null);
                        if (task != null) {
                            task.setExecState(EnumsApi.TaskExecState.SKIPPED.value);
                            task.setCompleted(1);
                            task.setCompletedOn(System.currentTimeMillis());
                            taskRepository.save(task);
                            log.info("995.115 Set Task #{} execState to SKIPPED (removed from DAG)", removed.taskId);
                        }
                    });

                    // Deregister from task queue
                    TaskProviderTopLevelService.deregisterTask(simpleExecContext.execContextId, removed.taskId);
                }

                // Remove stale entries from ExecContextVariableState so findVariableInAllInternalContexts
                // won't find variables from removed (orphan) tasks
                // (041: the entries left with their lines)
            }

            taskProducingService.createTasksForSubProcesses(
                    simpleExecContext, executionContextData, currTaskContextId, taskId, lastIds, graftExpander,
                // 041: a static sub-block written in the source stays in the segment of the line it is forked from
                ExecContextSegmentTxService.SegmentStart.ENCLOSING);

            // 041 Phase 7: the join of the new lines is derived from the segments; register them with it
            segmentTxService.registerLines(simpleExecContext.execContextId, lastIds);

        } catch (BatchProcessingException | StoreNewFileWithRedirectException e) {
            throw e;
        } catch (Throwable th) {
            String es = "995.300 An error while saving data to file, " + th.getMessage();
            log.error(es, th);
            throw new BatchResourceProcessingException(es);
        }
        return null;
    }

    /**
     * 041 Phase 11: the Tasks of the old sub-block children's subtree - their lines and every line forked, at any
     * depth, from those lines' Tasks whose ctx lies under the wrapper's ctx (the same bound the whole-graph removal used:
     * {@code deriveParentTaskContextId} reaches {@code wrapperCtx}). Downstream Tasks (the join, mh.finish) are outside.
     */
    private Set<Long> oldSubtree(Long execContextId, Set<ExecContextData.TaskVertex> oldChildren, String wrapperCtx) {
        final ai.metaheuristic.ai.dispatcher.exec_context_segment.SegmentLineView view = segmentReadService.lineView(execContextId);
        final Set<Long> out = new java.util.LinkedHashSet<>();
        final Set<String> seenLines = new java.util.HashSet<>();
        final java.util.Deque<ai.metaheuristic.ai.dispatcher.exec_context_segment.SegmentData.Line> queue = new java.util.ArrayDeque<>();
        oldChildren.forEach(v -> queue.add(view.lineOf(v.taskId)));
        while (!queue.isEmpty()) {
            final ai.metaheuristic.ai.dispatcher.exec_context_segment.SegmentData.Line line = queue.poll();
            if (!seenLines.add(line.ctx()) || !isUnderCtx(line.ctx(), wrapperCtx)) {
                continue;
            }
            for (ai.metaheuristic.ai.dispatcher.exec_context_segment.SegmentData.Vertex v : line.tasks()) {
                out.add(v.taskId());
                queue.addAll(view.linesForkedFrom(v.taskId()));
            }
        }
        return out;
    }

    private static boolean isUnderCtx(String taskContextId, String ancestorCtx) {
        String current = taskContextId;
        for (int i = 0; i < 100; i++) {
            final String parent = ContextUtils.deriveParentTaskContextId(current);
            if (parent == null) {
                return false;
            }
            if (parent.equals(ancestorCtx)) {
                return true;
            }
            current = parent;
        }
        return false;
    }
}
