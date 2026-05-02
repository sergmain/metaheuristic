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
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.utils.JsonUtils;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecContextCloneRewriteUtilsTest {

    @Test
    public void test_rewriteGraph_emptyMap_returnsInputUnchanged() {
        String input = "strict digraph G { 100; 200; 100 -> 200; }";
        //act
        String out = ExecContextCloneRewriteUtils.rewriteDotGraph(input, Map.of());
        assertThat(out).isEqualTo(input);
    }

    @Test
    public void test_rewriteGraph_singleVertex_idIsRewritten() {
        // build source graph: one vertex, taskId=100, taskContextId="1###"
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> g =
                new DirectedAcyclicGraph<>(
                        ExecContextData.TaskVertex::new,
                        org.jgrapht.util.SupplierUtil.DEFAULT_EDGE_SUPPLIER,
                        false);
        g.addVertex(new ExecContextData.TaskVertex(100L, "1###"));
        String input = ExecContextGraphService.asString(g);

        //act
        String out = ExecContextCloneRewriteUtils.rewriteDotGraph(input, Map.of(100L, 500L));

        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> rebuilt =
                ExecContextGraphService.importExecContextGraph(out);
        assertThat(rebuilt.vertexSet()).hasSize(1);
        ExecContextData.TaskVertex v = rebuilt.vertexSet().iterator().next();
        assertThat(v.taskId).isEqualTo(500L);
        assertThat(v.taskContextId).isEqualTo("1###");
    }

    @Test
    public void test_rewriteGraph_edges_arePreservedWithRewrittenIds() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> g =
                new DirectedAcyclicGraph<>(
                        ExecContextData.TaskVertex::new,
                        org.jgrapht.util.SupplierUtil.DEFAULT_EDGE_SUPPLIER,
                        false);
        ExecContextData.TaskVertex v100 = new ExecContextData.TaskVertex(100L, "1###");
        ExecContextData.TaskVertex v200 = new ExecContextData.TaskVertex(200L, "1###");
        ExecContextData.TaskVertex v300 = new ExecContextData.TaskVertex(300L, "2###");
        g.addVertex(v100);
        g.addVertex(v200);
        g.addVertex(v300);
        g.addEdge(v100, v200);
        g.addEdge(v200, v300);
        String input = ExecContextGraphService.asString(g);

        Map<Long, Long> map = Map.of(100L, 500L, 200L, 600L, 300L, 700L);

        //act
        String out = ExecContextCloneRewriteUtils.rewriteDotGraph(input, map);

        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> rebuilt =
                ExecContextGraphService.importExecContextGraph(out);
        Set<Long> ids = rebuilt.vertexSet().stream()
                .map(v -> v.taskId).collect(java.util.stream.Collectors.toSet());
        assertThat(ids).containsExactlyInAnyOrder(500L, 600L, 700L);
        assertThat(rebuilt.edgeSet()).hasSize(2);
    }

    @Test
    public void test_rewriteGraph_unmappedVertex_passesThrough() {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> g =
                new DirectedAcyclicGraph<>(
                        ExecContextData.TaskVertex::new,
                        org.jgrapht.util.SupplierUtil.DEFAULT_EDGE_SUPPLIER,
                        false);
        g.addVertex(new ExecContextData.TaskVertex(100L, "1###"));
        g.addVertex(new ExecContextData.TaskVertex(200L, "1###"));
        String input = ExecContextGraphService.asString(g);

        // only 100 mapped; 200 not in map
        //act
        String out = ExecContextCloneRewriteUtils.rewriteDotGraph(input, Map.of(100L, 500L));

        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> rebuilt =
                ExecContextGraphService.importExecContextGraph(out);
        Set<Long> ids = rebuilt.vertexSet().stream()
                .map(v -> v.taskId).collect(java.util.stream.Collectors.toSet());
        assertThat(ids).containsExactlyInAnyOrder(500L, 200L);
    }

    @Test
    public void test_rewriteTaskState_states_keysAreRewritten() {
        ExecContextTaskStateParamsYaml src = new ExecContextTaskStateParamsYaml();
        src.states.put(100L, EnumsApi.TaskExecState.OK);
        src.states.put(200L, EnumsApi.TaskExecState.IN_PROGRESS);
        src.triesWasMade.put(100L, 3);
        src.triesWasMade.put(200L, 1);

        Map<Long, Long> map = Map.of(100L, 500L, 200L, 600L);

        //act
        ExecContextTaskStateParamsYaml out = ExecContextCloneRewriteUtils.rewriteTaskState(src, map);

        assertThat(out.states).hasSize(2);
        assertThat(out.states).containsEntry(500L, EnumsApi.TaskExecState.OK);
        assertThat(out.states).containsEntry(600L, EnumsApi.TaskExecState.IN_PROGRESS);
        assertThat(out.triesWasMade).hasSize(2);
        assertThat(out.triesWasMade).containsEntry(500L, 3);
        assertThat(out.triesWasMade).containsEntry(600L, 1);
    }

    @Test
    public void test_rewriteTaskState_inputNotMutated() {
        ExecContextTaskStateParamsYaml src = new ExecContextTaskStateParamsYaml();
        src.states.put(100L, EnumsApi.TaskExecState.OK);
        Map<Long, Long> map = Map.of(100L, 500L);

        //act
        ExecContextCloneRewriteUtils.rewriteTaskState(src, map);

        // src must be untouched
        assertThat(src.states).containsOnlyKeys(100L);
    }

    @Test
    public void test_rewriteTaskState_unmappedKey_passesThrough() {
        ExecContextTaskStateParamsYaml src = new ExecContextTaskStateParamsYaml();
        src.states.put(100L, EnumsApi.TaskExecState.OK);
        src.states.put(999L, EnumsApi.TaskExecState.ERROR);

        //act
        ExecContextTaskStateParamsYaml out =
                ExecContextCloneRewriteUtils.rewriteTaskState(src, Map.of(100L, 500L));

        assertThat(out.states).containsOnlyKeys(500L, 999L);
        assertThat(out.states).containsEntry(999L, EnumsApi.TaskExecState.ERROR);
    }

    @Test
    public void test_rewriteVariableStateJson_emptyInput_yieldsEmptyStates() throws Exception {
        //act
        String json = ExecContextCloneRewriteUtils.rewriteVariableStateJson("", Map.of(), Map.of());

        ExecContextApiData.ExecContextVariableStates out = JsonUtils.getMapper()
                .readValue(json, ExecContextApiData.ExecContextVariableStates.class);
        assertThat(out.states).isEmpty();
    }

    @Test
    public void test_rewriteVariableStateJson_taskAndVarIdsRewritten() throws Exception {
        ExecContextApiData.ExecContextVariableStates src = new ExecContextApiData.ExecContextVariableStates();
        ExecContextApiData.VariableState s1 = new ExecContextApiData.VariableState();
        s1.taskId = 100L;
        s1.processorId = 1L;
        s1.execContextId = 7L;
        s1.taskContextId = "1###";
        s1.process = "p";
        s1.functionCode = "f";
        s1.inputs = new ArrayList<>(List.of(
                new ExecContextApiData.VariableInfo(11L, "in1", EnumsApi.VariableContext.local, null)));
        s1.outputs = new ArrayList<>(List.of(
                new ExecContextApiData.VariableInfo(12L, "out1", EnumsApi.VariableContext.local, null)));
        src.states.add(s1);
        String inputJson = JsonUtils.getMapper().writeValueAsString(src);

        Map<Long, Long> taskIdMap = Map.of(100L, 500L);
        Map<Long, Long> varIdMap = Map.of(11L, 111L, 12L, 222L);

        //act
        String outJson = ExecContextCloneRewriteUtils.rewriteVariableStateJson(inputJson, taskIdMap, varIdMap);

        ExecContextApiData.ExecContextVariableStates out = JsonUtils.getMapper()
                .readValue(outJson, ExecContextApiData.ExecContextVariableStates.class);
        assertThat(out.states).hasSize(1);
        ExecContextApiData.VariableState rs = out.states.get(0);
        assertThat(rs.taskId).isEqualTo(500L);
        assertThat(rs.taskContextId).isEqualTo("1###");
        assertThat(rs.inputs).hasSize(1);
        assertThat(rs.inputs.get(0).id).isEqualTo(111L);
        assertThat(rs.inputs.get(0).name).isEqualTo("in1");
        assertThat(rs.outputs).hasSize(1);
        assertThat(rs.outputs.get(0).id).isEqualTo(222L);
    }

    @Test
    public void test_rewriteVariableStateJson_unmappedIdsPassThrough() throws Exception {
        ExecContextApiData.ExecContextVariableStates src = new ExecContextApiData.ExecContextVariableStates();
        ExecContextApiData.VariableState s1 = new ExecContextApiData.VariableState();
        s1.taskId = 999L;
        s1.processorId = 1L;
        s1.execContextId = 7L;
        s1.taskContextId = "1###";
        s1.process = "p";
        s1.functionCode = "f";
        s1.inputs = new ArrayList<>(List.of(
                new ExecContextApiData.VariableInfo(99L, "in1", EnumsApi.VariableContext.local, null)));
        s1.outputs = null;
        src.states.add(s1);
        String inputJson = JsonUtils.getMapper().writeValueAsString(src);

        //act — empty maps, so nothing changes
        String outJson = ExecContextCloneRewriteUtils.rewriteVariableStateJson(inputJson, Map.of(), Map.of());

        ExecContextApiData.ExecContextVariableStates out = JsonUtils.getMapper()
                .readValue(outJson, ExecContextApiData.ExecContextVariableStates.class);
        assertThat(out.states).hasSize(1);
        assertThat(out.states.get(0).taskId).isEqualTo(999L);
        assertThat(out.states.get(0).inputs.get(0).id).isEqualTo(99L);
    }
}
