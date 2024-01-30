/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphFactory;
import ai.metaheuristic.ai.exceptions.SourceCodeGraphException;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static ai.metaheuristic.ai.dispatcher.data.SourceCodeData.SourceCodeGraph;
import static ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 2/17/2020
 * Time: 2:38 AM
 */
public class TestSourceCodeGraphLanguageYaml {

    @Test
    public void test_01() throws IOException {
        String sourceCode = IOUtils.resourceToString("/source_code/yaml/source-code-for-preprocessing-and-classification-v1.yaml", StandardCharsets.UTF_8);
        AtomicLong contextId = new AtomicLong();
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, sourceCode, () -> "" + contextId.incrementAndGet());

        assertNotNull(graph);
        assertTrue(graph.clean);
        assertEquals(10, graph.processGraph.vertexSet().size());
        assertEquals(10, graph.processes.size());
        // check for mh.finish
        assertEquals(1, findLeafs(graph).size(), "Graph: \n" + asString(graph.processGraph));

        // value of internalContextId doesn't matter in this case
        ExecContextData.ProcessVertex vertexAssembly = findVertex(graph.processGraph, "assembly-raw-file");
        assertNotNull(vertexAssembly);
        assertEquals(1, graph.processGraph.outgoingEdgesOf(vertexAssembly).size(), "Graph: \n" + asString(graph.processGraph));
        assertEquals(9, findDescendants(graph, vertexAssembly).size(), "Graph: \n" + asString(graph.processGraph));

        ExecContextData.ProcessVertex v = findVertex(graph.processGraph, vertexAssembly.process);
        assertNotNull(v);

        List<ExecContextData.ProcessVertex> vs1 = findTargets(graph.processGraph, vertexAssembly.process);

        assertEquals(1, vs1.size());

        ExecContextData.ProcessVertex v1 = vs1.get(0);
        assertNotNull(v1);
        String processDataset = "dataset-processing";
        assertEquals(processDataset, v1.process);

        List<ExecContextData.ProcessVertex> vs2 = findTargets(graph.processGraph, processDataset);

        assertEquals(3, vs2.size(), "Graph: \n" + asString(graph.processGraph));

        ExecContextData.ProcessVertex v21 = vs2.stream().filter(o->o.process.equals("feature-processing_cluster")).findFirst().orElseThrow();
        ExecContextData.ProcessVertex v22 = vs2.stream().filter(o->o.process.equals("feature-processing_matrix")).findFirst().orElseThrow();
        ExecContextData.ProcessVertex v23 = vs2.stream().filter(o->o.process.equals("mh.permute-variables-and-hyper-params")).findFirst().orElseThrow();

        assertEquals(1, findTargets(graph.processGraph, v21.process).size(), "Graph: \n" + asString(graph.processGraph));
        assertEquals(1, findTargets(graph.processGraph, v22.process).size(), "Graph: \n" + asString(graph.processGraph));
        assertEquals(2, findTargets(graph.processGraph, v23.process).size(), "Graph: \n" + asString(graph.processGraph));

        ExecContextParamsYaml.Process p = graph.processes.stream().filter(o->o.processCode.equals("feature-processing_cluster")).findFirst().orElseThrow();
        assertEquals("ai", p.tag);
        assertEquals(-1, p.priority);
    }

    @Test
    public void test_02_empty_graph_v1() throws IOException {
        String sourceCode = IOUtils.resourceToString("/source_code/yaml/for_testing_graph/empty-graph-v1.yaml", StandardCharsets.UTF_8);
        AtomicLong contextId = new AtomicLong();
        assertThrows(SourceCodeGraphException.class, ()-> SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, sourceCode, () -> "" + contextId.incrementAndGet()));
    }

    @Test
    public void test_03_one_process() throws IOException {
        String sourceCode = IOUtils.resourceToString("/source_code/yaml/for_testing_graph/one-process-graph-v2.yaml", StandardCharsets.UTF_8);
        AtomicLong contextId = new AtomicLong();
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, sourceCode, () -> "" + contextId.incrementAndGet());

        assertNotNull(graph);
        // check for mh.finish
        assertEquals(1, findLeafs(graph).size(), "Graph: \n" + asString(graph.processGraph));

        assertEquals(2, graph.processGraph.vertexSet().size());
        assertEquals(2, graph.processes.size());

        ExecContextData.ProcessVertex nopVertex1 = findVertex(graph.processGraph, "mh.nop-1");
        assertNotNull(nopVertex1);
        assertEquals(1, graph.processGraph.outgoingEdgesOf(nopVertex1).size(), "Graph: \n" + asString(graph.processGraph));

        ExecContextData.ProcessVertex finishVertex = findVertex(graph.processGraph, Consts.MH_FINISH_FUNCTION);
        assertNotNull(finishVertex);
        assertEquals(0, findDescendants(graph, finishVertex).size(), "Graph: \n" + asString(graph.processGraph));
        assertEquals(1, graph.processGraph.incomingEdgesOf(finishVertex).size(), "Graph: \n" + asString(graph.processGraph));
    }

    @Test
    public void test_04_sub_processes_logic_and() throws IOException {
        String sourceCode = IOUtils.resourceToString("/source_code/yaml/for_testing_graph/sub-process-logic-and.yaml", StandardCharsets.UTF_8);
        AtomicLong contextId = new AtomicLong();
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, sourceCode, () -> "" + contextId.incrementAndGet());

        assertNotNull(graph);
        // check for mh.finish
        assertEquals(1, findLeafs(graph).size(), "Graph: \n" + asString(graph.processGraph));

        assertEquals(5, graph.processGraph.vertexSet().size());
        assertEquals(5, graph.processes.size());

        ExecContextData.ProcessVertex nopVertex1 = findVertex(graph.processGraph, "mh.nop-1");
        assertNotNull(nopVertex1);
        assertEquals(1, graph.processGraph.outgoingEdgesOf(nopVertex1).size(), "Graph: \n" + asString(graph.processGraph));

        ExecContextData.ProcessVertex nopVertex2 = findVertex(graph.processGraph, "mh.nop-2");
        assertNotNull(nopVertex2);
        assertEquals(3, findDescendants(graph, nopVertex2).size(), "Graph: \n" + asString(graph.processGraph));

        ExecContextData.ProcessVertex finishVertex = findVertex(graph.processGraph, Consts.MH_FINISH_FUNCTION);
        assertNotNull(finishVertex);
        assertEquals(0, findDescendants(graph, finishVertex).size(), "Graph: \n" + asString(graph.processGraph));
        assertEquals(3, graph.processGraph.incomingEdgesOf(finishVertex).size(), "Graph: \n" + asString(graph.processGraph));


    }

    @Test
    public void test_05_sub_processes_logic_sequence() throws IOException {
        String sourceCode = IOUtils.resourceToString("/source_code/yaml/for_testing_graph/sub-process-logic-sequence.yaml", StandardCharsets.UTF_8);
        AtomicLong contextId = new AtomicLong();
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, sourceCode, () -> "" + contextId.incrementAndGet());

        assertNotNull(graph);
        // check for mh.finish
        assertEquals(1, findLeafs(graph).size(), "Graph: \n" + asString(graph.processGraph));

        assertEquals(6, graph.processGraph.vertexSet().size());
        assertEquals(6, graph.processes.size());

        ExecContextData.ProcessVertex nopVertex1 = findVertex(graph.processGraph, "mh.nop-1");
        assertNotNull(nopVertex1);
        assertEquals(1, graph.processGraph.outgoingEdgesOf(nopVertex1).size(), "Graph: \n" + asString(graph.processGraph));

        ExecContextData.ProcessVertex nopVertex2 = findVertex(graph.processGraph, "mh.nop-2");
        assertNotNull(nopVertex2);
        assertEquals(4, findDescendants(graph, nopVertex2).size(), "Graph: \n" + asString(graph.processGraph));
        assertEquals(2, graph.processGraph.outgoingEdgesOf(nopVertex2).size(), "Graph: \n" + asString(graph.processGraph));

        ExecContextData.ProcessVertex finishVertex = findVertex(graph.processGraph, Consts.MH_FINISH_FUNCTION);
        assertNotNull(finishVertex);
        assertEquals(0, findDescendants(graph, finishVertex).size(), "Graph: \n" + asString(graph.processGraph));
        assertEquals(2, graph.processGraph.incomingEdgesOf(finishVertex).size(), "Graph: \n" + asString(graph.processGraph));
    }

    @Test
    public void test_06_sub_processes_two_levels_logic_sequence() throws IOException {
        String sourceCode = IOUtils.resourceToString("/source_code/yaml/for_testing_graph/sub-process-two-levels-logic-sequence.yaml", StandardCharsets.UTF_8);
        AtomicLong contextId = new AtomicLong();
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, sourceCode, () -> "" + contextId.incrementAndGet());

        assertNotNull(graph);
        // check for mh.finish
        assertEquals(1, findLeafs(graph).size(), "Graph: \n" + asString(graph.processGraph));

        assertEquals(9, graph.processGraph.vertexSet().size(), "Graph: \n" + asString(graph.processGraph));
        assertEquals(9, graph.processes.size(), "Graph: \n" + asString(graph.processGraph));

        ExecContextData.ProcessVertex nopVertex1 = findVertex(graph.processGraph, "mh.nop-1");
        assertNotNull(nopVertex1);
        assertEquals(1, graph.processGraph.outgoingEdgesOf(nopVertex1).size(), "Graph: \n" + asString(graph.processGraph));

        ExecContextData.ProcessVertex nopVertex2 = findVertex(graph.processGraph, "mh.nop-2");
        assertNotNull(nopVertex2);
        assertEquals(7, findDescendants(graph, nopVertex2).size(), "Graph: \n" + asString(graph.processGraph));
        assertEquals(2, graph.processGraph.outgoingEdgesOf(nopVertex2).size(), "Graph: \n" + asString(graph.processGraph));

        ExecContextData.ProcessVertex finishVertex = findVertex(graph.processGraph, Consts.MH_FINISH_FUNCTION);
        assertNotNull(finishVertex);
        assertEquals(0, findDescendants(graph, finishVertex).size(), "Graph: \n" + asString(graph.processGraph));
        assertEquals(3, graph.processGraph.incomingEdgesOf(finishVertex).size(), "Graph: \n" + asString(graph.processGraph));


    }
}
