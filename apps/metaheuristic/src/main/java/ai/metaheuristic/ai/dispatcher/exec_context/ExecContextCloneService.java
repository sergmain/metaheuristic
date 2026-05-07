/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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
package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextGraphRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextVariableStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYamlUtils;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Phase 02 — MH-side ExecContext clone service.
 *
 * Two-stage clone (no thread-local state, no extra schema columns):
 *
 * Stage 1 — copy rows. Insert a new ExecContext (state=CLONING), a new
 * ExecContextGraph / ExecContextTaskState / ExecContextVariableState (params
 * copied verbatim), then for every Task in the source ExecContext insert a
 * new Task and record (oldTaskId -&gt; newTaskId) in
 * {@code ConcurrentHashMap<Long,Long>}. After that, the variable IDs
 * referenced by the source's ExecContextVariableState are cloned in parallel
 * on a fixed thread pool — each variable in its own transaction — and the
 * (oldVarId -&gt; newVarId) pairs are written into a second
 * {@code ConcurrentHashMap<Long,Long>}.
 *
 * Stage 2 — rewrite references. Using the Stage 1 maps, rewrite:
 *   - the DOT graph in ExecContextGraph.params (vertex IDs);
 *   - the Long keys of ExecContextTaskStateParamsYaml.states / triesWasMade;
 *   - the JSON inside ExecContextVariableState.params (taskId per state row,
 *     id per VariableInfo).
 * Then flip the new ExecContext from CLONING to FINISHED.
 *
 * MH has no awareness of snapshots; the primitive is generic. Callers
 * (e.g. RG) compose this against their own STAGE/COMMITTED semantics.
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextCloneService {

    private final ExecContextRepository execContextRepository;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextVariableStateRepository execContextVariableStateRepository;
    private final TaskRepository taskRepository;
    private final ExecContextCloneTxService cloneTxService;

    public record CloneOptions(int variableThreads, @Nullable Set<Long> taskIdAllowList) {
        public static final CloneOptions DEFAULTS = new CloneOptions(4, null);

        public CloneOptions(int variableThreads) {
            this(variableThreads, null);
        }
    }

    public record CloneResult(
            Long clonedExecContextId,
            long taskCount,
            long variableCount,
            long elapsedMillis,
            java.util.Map<Long, Long> taskIdMap,
            java.util.Map<Long, Long> variableIdMap) {
    }

    public CloneResult cloneExecContext(Long sourceExecContextId) {
        return cloneExecContext(sourceExecContextId, CloneOptions.DEFAULTS);
    }

    public CloneResult cloneExecContext(Long sourceExecContextId, CloneOptions options) {
        long start = System.currentTimeMillis();

        ExecContextImpl source = execContextRepository.findByIdNullable(sourceExecContextId);
        if (source == null) {
            throw new IllegalArgumentException(
                    "Source ExecContext not found: " + sourceExecContextId);
        }
        ExecContextGraph sourceGraph = execContextGraphRepository
                .findById(source.execContextGraphId).orElseThrow();
        ExecContextTaskState sourceTaskState = execContextTaskStateRepository
                .findById(source.execContextTaskStateId).orElseThrow();
        ExecContextVariableState sourceVarState = execContextVariableStateRepository
                .findById(source.execContextVariableStateId).orElseThrow();

        // Stage 1a — copy ExecContext envelope and child rows
        ExecContextImpl newEc = cloneTxService.insertNewExecContext(source);
        Long newEcId = newEc.id;

        ExecContextGraph newGraph = cloneTxService.insertNewGraph(sourceGraph, newEcId);
        ExecContextTaskState newTaskState = cloneTxService.insertNewTaskState(sourceTaskState, newEcId);
        ExecContextVariableState newVarState = cloneTxService.insertNewVariableState(sourceVarState, newEcId);
        cloneTxService.updateExecContextChildPointers(newEcId, newGraph.id, newTaskState.id, newVarState.id);

        // Stage 1b — clone Variable rows in parallel FIRST so the variableIdMap is
        // available when cloning Tasks (TaskParamsYaml carries variable IDs that
        // must be rewritten source-EC -> clone-EC). Phase 13.G.5.
        ConcurrentHashMap<Long, Long> variableIdMap = new ConcurrentHashMap<>();
        Set<Long> sourceVarIds = collectVariableIds(sourceVarState.getParams());
        cloneVariablesInParallel(sourceVarIds, newEcId, options.variableThreads(), variableIdMap);

        // Stage 1c — clone Task rows, build oldTaskId -> newTaskId map. Pass the
        // variableIdMap so TaskParamsYaml.inputs/outputs variable IDs are rewritten
        // into clone-EC space.
        ConcurrentHashMap<Long, Long> taskIdMap = new ConcurrentHashMap<>();
        List<TaskImpl> allSourceTasks = taskRepository.findByExecContextIdReadOnly(sourceExecContextId);
        List<TaskImpl> sourceTasks;
        Set<Long> allowList = options.taskIdAllowList();
        if (allowList == null) {
            sourceTasks = allSourceTasks;
        } else {
            sourceTasks = new ArrayList<>(allSourceTasks.size());
            for (TaskImpl t : allSourceTasks) {
                if (allowList.contains(t.id)) {
                    sourceTasks.add(t);
                }
            }
        }
        // Two-pass clone with PRE-ALLOCATED IDs (Phase 13.G.5):
        //   Pass A — pre-allocate clone-EC task IDs by inserting placeholder rows;
        //            build the complete src→clone taskIdMap.
        //   Pass B — fill each cloned row's params with rewritten variable AND
        //            parent task IDs (using the now-complete maps) and final state.
        // Doing pre-allocation first avoids the chicken-and-egg of "need taskIdMap
        // to write parentTaskIds, but taskIdMap is built BY writing rows".
        // Doing both passes in REQUIRES_NEW transactions ensures rows committed in
        // pass A are visible to pass B.
        for (TaskImpl t : sourceTasks) {
            Long preAllocatedId = cloneTxService.preAllocateClonedTask(newEcId);
            taskIdMap.put(t.id, preAllocatedId);
        }
        // Pass B — fill each pre-allocated row with cloned content + rewritten IDs.
        for (TaskImpl t : sourceTasks) {
            Long clonedTaskId = taskIdMap.get(t.id);
            cloneTxService.fillClonedTask(t.id, clonedTaskId, newEcId, variableIdMap, taskIdMap);
        }

        // PHASE13G5 DIAG — invariant probe: for each source task, dump
        //   (a) source taskId -> set of source parentTaskIds from TaskParamsYaml.task.init.parentTaskIds,
        //   (b) whether each such parent is in (1) source-task-row set, (2) source graph vertex set.
        // This pinpoints whether the bug is "source TaskParamsYaml references parents that aren't
        // in the source graph" — which the clone faithfully preserves.
        try {
            String sourceGraphDot = newGraph.getExecContextGraphParamsYaml().graph;
            org.jgrapht.graph.DirectedAcyclicGraph<
                    ai.metaheuristic.ai.dispatcher.data.ExecContextData.TaskVertex,
                    org.jgrapht.graph.DefaultEdge> dag =
                    ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService
                            .importExecContextGraph(sourceGraphDot);
            java.util.Set<Long> srcGraphVertexIds = new java.util.HashSet<>();
            for (var v : dag.vertexSet()) srcGraphVertexIds.add(v.taskId);
            java.util.Set<Long> srcTaskRowIds = new java.util.HashSet<>(taskIdMap.keySet());

            for (TaskImpl t : sourceTasks) {
                ai.metaheuristic.commons.yaml.task.TaskParamsYaml tpy = t.getTaskParamsYaml();
                if (tpy.task == null || tpy.task.init == null || tpy.task.init.parentTaskIds == null
                        || tpy.task.init.parentTaskIds.isEmpty()) {
                    continue;
                }
                for (Long pid : tpy.task.init.parentTaskIds) {
                    boolean inRows = srcTaskRowIds.contains(pid);
                    boolean inGraph = srcGraphVertexIds.contains(pid);
                    if (!inRows || !inGraph) {
                        log.warn("Phase13.G.5 DIAG INVARIANT srcTask#{} parentTaskId={} inSrcRows={} inSrcGraph={}",
                                t.id, pid, inRows, inGraph);
                    }
                }
            }
        } catch (Throwable th) {
            log.warn("Phase13.G.5 DIAG: failed source-invariant probe", th);
        }

        // Stage 2 — rewrite references (graph DOT inside YAML envelope, task-state
        // map keys, variable-state JSON). Pass the parsed YAML object to rewriteGraph
        // so we don't feed the whole YAML string to the DOT importer.
        ExecContextGraphParamsYaml rewrittenGraphYaml = ExecContextCloneRewriteUtils
                .rewriteGraph(newGraph.getExecContextGraphParamsYaml(), taskIdMap);
        String rewrittenGraph = ExecContextGraphParamsYamlUtils.BASE_YAML_UTILS
                .toString(rewrittenGraphYaml);
        String rewrittenTaskState = serializeTaskState(
                ExecContextCloneRewriteUtils.rewriteTaskState(
                        sourceTaskState.getExecContextTaskStateParamsYaml(), taskIdMap));
        String rewrittenVarState = ExecContextCloneRewriteUtils
                .rewriteVariableStateJson(newVarState.getParams(), taskIdMap, variableIdMap);

        cloneTxService.writeRewrittenChildren(
                newEcId,
                newGraph.id, rewrittenGraph,
                newTaskState.id, rewrittenTaskState,
                newVarState.id, rewrittenVarState);

        // Stage 3 — flip CLONING -> FINISHED
        cloneTxService.finalizeCloneState(newEcId);

        long elapsed = System.currentTimeMillis() - start;
        log.info("ExecContext clone complete: source={}, target={}, tasks={}, variables={}, elapsedMs={}",
                sourceExecContextId, newEcId, sourceTasks.size(), sourceVarIds.size(), elapsed);

        return new CloneResult(newEcId, sourceTasks.size(), sourceVarIds.size(), elapsed,
                java.util.Map.copyOf(taskIdMap), java.util.Map.copyOf(variableIdMap));
    }

    private void cloneVariablesInParallel(Set<Long> sourceVarIds, Long newEcId,
                                          int nThreads, ConcurrentHashMap<Long, Long> variableIdMap) {
        if (sourceVarIds.isEmpty()) {
            return;
        }
        int threads = Math.max(1, Math.min(nThreads, sourceVarIds.size()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>(sourceVarIds.size());
            for (Long varId : sourceVarIds) {
                futures.add(CompletableFuture.runAsync(() -> {
                    ExecContextCloneTxService.VariableClonedIds pair =
                            cloneTxService.insertNewVariable(varId, newEcId);
                    variableIdMap.put(pair.oldVariableId(), pair.newVariableId());
                }, executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }
    }

    @SneakyThrows
    static Set<Long> collectVariableIds(@Nullable String variableStateJson) {
        Set<Long> ids = new HashSet<>();
        if (S.b(variableStateJson)) {
            return ids;
        }
        ExecContextApiData.ExecContextVariableStates parsed = JsonUtils.getMapper()
                .readValue(variableStateJson, ExecContextApiData.ExecContextVariableStates.class);
        for (ExecContextApiData.VariableState st : parsed.states) {
            collectIds(st.inputs, ids);
            collectIds(st.outputs, ids);
        }
        return ids;
    }

    private static void collectIds(@Nullable List<ExecContextApiData.VariableInfo> list, Set<Long> ids) {
        if (list == null) {
            return;
        }
        for (ExecContextApiData.VariableInfo vi : list) {
            if (vi.id != null) {
                ids.add(vi.id);
            }
        }
    }

    @SneakyThrows
    private static String serializeTaskState(
            ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml params) {
        return ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYamlUtils
                .BASE_YAML_UTILS.toString(params);
    }
}
