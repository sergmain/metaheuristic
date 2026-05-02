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

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.JsonUtils;
import lombok.SneakyThrows;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure functions used during ExecContext clone Stage 2 (reference rewrite).
 * No Spring, no DB. Inputs are immutable strings and maps; outputs are new
 * strings and maps. Tested in isolation.
 *
 * The clone proceeds in two stages:
 * Stage 1 — copy MH_EXEC_CONTEXT, MH_TASK and MH_VARIABLE rows; build
 *           an in-memory Map<oldTaskId, newTaskId>.
 * Stage 2 — using the Stage 1 map, rewrite the task IDs that appear inside
 *           ExecContextGraph.params (DOT graph vertices) and inside
 *           ExecContextTaskStateParamsYaml.states / triesWasMade map keys.
 *
 * The methods here implement Stage 2.
 */
public class ExecContextCloneRewriteUtils {

    private ExecContextCloneRewriteUtils() {
    }

    /**
     * Rewrite the DOT graph held inside an {@link ExecContextGraphParamsYaml}
     * using {@code taskIdMap} (oldTaskId -> newTaskId). Vertices not present
     * in the map are left unchanged. Edge structure and per-vertex
     * {@code taskContextId} attribute are preserved. Returns a new params
     * object — the input is not mutated.
     */
    public static ExecContextGraphParamsYaml rewriteGraph(
            ExecContextGraphParamsYaml source, Map<Long, Long> taskIdMap) {
        ExecContextGraphParamsYaml target = new ExecContextGraphParamsYaml();
        target.graph = rewriteDotGraph(source.graph, taskIdMap);
        return target;
    }

    /**
     * Pure DOT-string -> DOT-string rewrite. Public for unit-testing the
     * vertex/edge rewrite logic without a YAML envelope.
     */
    public static String rewriteDotGraph(String graphAsString, Map<Long, Long> taskIdMap) {
        if (taskIdMap.isEmpty()) {
            return graphAsString;
        }
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> source =
                ExecContextGraphService.importExecContextGraph(graphAsString);

        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> target =
                new DirectedAcyclicGraph<>(
                        ExecContextData.TaskVertex::new,
                        org.jgrapht.util.SupplierUtil.DEFAULT_EDGE_SUPPLIER,
                        false);

        Map<Long, ExecContextData.TaskVertex> targetById = new HashMap<>();
        for (ExecContextData.TaskVertex sv : source.vertexSet()) {
            Long newId = taskIdMap.getOrDefault(sv.taskId, sv.taskId);
            ExecContextData.TaskVertex tv =
                    new ExecContextData.TaskVertex(newId, sv.taskContextId);
            target.addVertex(tv);
            targetById.put(newId, tv);
        }
        for (DefaultEdge e : source.edgeSet()) {
            ExecContextData.TaskVertex sFrom = source.getEdgeSource(e);
            ExecContextData.TaskVertex sTo = source.getEdgeTarget(e);
            Long newFrom = taskIdMap.getOrDefault(sFrom.taskId, sFrom.taskId);
            Long newTo = taskIdMap.getOrDefault(sTo.taskId, sTo.taskId);
            target.addEdge(targetById.get(newFrom), targetById.get(newTo));
        }
        return ExecContextGraphService.asString(target);
    }

    /**
     * Rewrite the Long keys of {@link ExecContextTaskStateParamsYaml#states}
     * and {@code triesWasMade} using {@code taskIdMap}. Returns a new
     * params YAML object — the input is not mutated. Keys not present in
     * the map are left as-is.
     */
    public static ExecContextTaskStateParamsYaml rewriteTaskState(
            ExecContextTaskStateParamsYaml source,
            Map<Long, Long> taskIdMap) {

        ExecContextTaskStateParamsYaml target = new ExecContextTaskStateParamsYaml();
        for (Map.Entry<Long, EnumsApi.TaskExecState> e : source.states.entrySet()) {
            Long newKey = taskIdMap.getOrDefault(e.getKey(), e.getKey());
            target.states.put(newKey, e.getValue());
        }
        for (Map.Entry<Long, Integer> e : source.triesWasMade.entrySet()) {
            Long newKey = taskIdMap.getOrDefault(e.getKey(), e.getKey());
            target.triesWasMade.put(newKey, e.getValue());
        }
        return target;
    }

    /**
     * Rewrite the JSON serialization of {@link ExecContextApiData.ExecContextVariableStates}.
     * Inside each {@link ExecContextApiData.VariableState} the {@code taskId} is rewritten via
     * {@code taskIdMap}; inside each input/output {@link ExecContextApiData.VariableInfo} the
     * {@code id} is rewritten via {@code variableIdMap}. Keys not present in the maps are
     * left as-is. Returns a new JSON string. The input JSON may be empty/null — in which
     * case an empty serialized {@code ExecContextVariableStates} is returned.
     */
    @SneakyThrows
    public static String rewriteVariableStateJson(
            String variableStateJson,
            Map<Long, Long> taskIdMap,
            Map<Long, Long> variableIdMap) {

        ExecContextApiData.ExecContextVariableStates src;
        if (S.b(variableStateJson)) {
            src = new ExecContextApiData.ExecContextVariableStates();
        } else {
            src = JsonUtils.getMapper().readValue(
                    variableStateJson, ExecContextApiData.ExecContextVariableStates.class);
        }

        ExecContextApiData.ExecContextVariableStates dst = new ExecContextApiData.ExecContextVariableStates();
        for (ExecContextApiData.VariableState srcState : src.states) {
            ExecContextApiData.VariableState dstState = new ExecContextApiData.VariableState();
            dstState.taskId = srcState.taskId == null
                    ? null
                    : taskIdMap.getOrDefault(srcState.taskId, srcState.taskId);
            dstState.processorId = srcState.processorId;
            dstState.execContextId = srcState.execContextId;
            dstState.taskContextId = srcState.taskContextId;
            dstState.process = srcState.process;
            dstState.functionCode = srcState.functionCode;
            dstState.inputs = rewriteVariableInfoList(srcState.inputs, variableIdMap);
            dstState.outputs = rewriteVariableInfoList(srcState.outputs, variableIdMap);
            dst.states.add(dstState);
        }
        return JsonUtils.getMapper().writeValueAsString(dst);
    }

    private static List<ExecContextApiData.VariableInfo> rewriteVariableInfoList(
            List<ExecContextApiData.VariableInfo> source, Map<Long, Long> variableIdMap) {
        if (source == null) {
            return null;
        }
        List<ExecContextApiData.VariableInfo> out = new ArrayList<>(source.size());
        for (ExecContextApiData.VariableInfo vi : source) {
            ExecContextApiData.VariableInfo dst = new ExecContextApiData.VariableInfo();
            dst.id = vi.id == null ? null : variableIdMap.getOrDefault(vi.id, vi.id);
            dst.name = vi.name;
            dst.context = vi.context;
            dst.inited = vi.inited;
            dst.nullified = vi.nullified;
            dst.ext = vi.ext;
            out.add(dst);
        }
        return out;
    }
}
