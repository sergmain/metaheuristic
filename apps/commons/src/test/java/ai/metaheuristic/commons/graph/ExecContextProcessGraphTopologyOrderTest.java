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

package ai.metaheuristic.commons.graph;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.graph.source_code_graph.SourceCodeGraphLanguageYaml;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterizes the column ordering produced by
 * {@link ExecContextProcessGraphService#getTopologyOfProcesses}.
 *
 * Scenario mirrors the DSL-v2 req-rung group: a common ancestor fans out to two
 * CONCURRENT branches (no static edge between them). Branch A ("objectives":
 * wrapper -> store-objective-result) is authored FIRST (lower vertex ids); branch B
 * ("resolve-requirement-status" -> "amendment-gate") is authored SECOND.
 *
 * At runtime the objectives branch executes before resolve-requirement-status,
 * so the header column of store-objective-result must sit LEFT of
 * resolve-requirement-status; otherwise the runtime edge store -> resolve renders
 * right-to-left in the exec-context state view.
 */
@Execution(ExecutionMode.CONCURRENT)
public class ExecContextProcessGraphTopologyOrderTest {

    private static final String ROOT = "check-objectives";
    private static final String WRAPPER_A = "objectives-wrapper";
    private static final String STORE_A = "store-objective-result";
    private static final String RESOLVE_B = "resolve-requirement-status";
    private static final String AMEND_B = "amendment-gate";

    private static List<String> topologyOfConcurrentBranches() {
        ExecContextParamsYaml ecpy = new ExecContextParamsYaml();
        ecpy.processesGraph = ConstsApi.EMPTY_GRAPH;
        DirectedAcyclicGraph<ExecContextApiData.ProcessVertex, DefaultEdge> g =
                ExecContextProcessGraphService.importProcessGraph(ecpy);

        Map<String, Long> ids = new HashMap<>();
        AtomicLong currId = new AtomicLong();

        ExecContextApiData.ProcessVertex root =
                SourceCodeGraphLanguageYaml.createProcessVertex(ids, currId, ROOT, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        ExecContextProcessGraphService.addProcessVertexToGraph(g, root, List.of());

        // branch A (authored first -> lower ids): objectives wrapper then its tail store task
        ExecContextApiData.ProcessVertex wrapperA =
                SourceCodeGraphLanguageYaml.createProcessVertex(ids, currId, WRAPPER_A, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        ExecContextProcessGraphService.addProcessVertexToGraph(g, wrapperA, List.of(root));
        ExecContextApiData.ProcessVertex storeA =
                SourceCodeGraphLanguageYaml.createProcessVertex(ids, currId, STORE_A, "1,2");
        ExecContextProcessGraphService.addProcessVertexToGraph(g, storeA, List.of(wrapperA));

        // branch B (authored second -> higher ids): resolve then amendment gate
        ExecContextApiData.ProcessVertex resolveB =
                SourceCodeGraphLanguageYaml.createProcessVertex(ids, currId, RESOLVE_B, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        ExecContextProcessGraphService.addProcessVertexToGraph(g, resolveB, List.of(root));
        ExecContextApiData.ProcessVertex amendB =
                SourceCodeGraphLanguageYaml.createProcessVertex(ids, currId, AMEND_B, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        ExecContextProcessGraphService.addProcessVertexToGraph(g, amendB, List.of(resolveB));

        ecpy.processesGraph = ExecContextProcessGraphService.asString(g);
        return ExecContextProcessGraphService.getTopologyOfProcesses(ecpy);
    }

    @Test
    public void test_concurrentBranchesKeepAuthoredOrder() {
        List<String> order = topologyOfConcurrentBranches();
        System.out.println("topology order = " + order);

        int store = order.indexOf(STORE_A);
        int resolve = order.indexOf(RESOLVE_B);
        assertTrue(store >= 0, "store must be present: " + order);
        assertTrue(resolve >= 0, "resolve must be present: " + order);

        // objectives branch is authored first; its tail store-objective-result
        // must sit to the LEFT of resolve-requirement-status.
        assertTrue(store < resolve,
                "store-objective-result must be LEFT of resolve-requirement-status, but order = " + order);
    }

    @Test
    public void test_orderIsDeterministic() {
        assertEquals(topologyOfConcurrentBranches(), topologyOfConcurrentBranches());
    }
}
