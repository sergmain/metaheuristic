/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.source_code.graph.SourceCodeGraphLanguageYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        // it's 10 because mh.finish was added to processes graph even in wasn't specified explicitly in SourceCode
        assertEquals(10, graph.processes.size());

        Map<String, Long> ids = new HashMap<>();
        AtomicLong currId = new AtomicLong();

        // value of internalContextId doesn't matter in this case
        ExecContextData.ProcessVertex vertexAssembly = SourceCodeGraphLanguageYaml.getVertex(ids, currId, "assembly-raw-file", Consts.TOP_LEVEL_CONTEXT_ID);
        assertEquals(9, findDescendants(graph, vertexAssembly).size());
        assertEquals(1, findLeafs(graph).size());

        ExecContextData.ProcessVertex v = findVertex(graph.processGraph, vertexAssembly.process);
        assertNotNull(v);

        List<ExecContextData.ProcessVertex> vs1 = findTargets(graph.processGraph, vertexAssembly.process);

        assertEquals(1, vs1.size());

        ExecContextData.ProcessVertex v1 = vs1.get(0);
        assertNotNull(v1);
        String processDataset = "dataset-processing";
        assertEquals(processDataset, v1.process);

        List<ExecContextData.ProcessVertex> vs2 = findTargets(graph.processGraph, processDataset);

        assertEquals(3, vs2.size());

        ExecContextData.ProcessVertex v21 = vs2.stream().filter(o->o.process.equals("feature-processing_cluster")).findFirst().orElseThrow();
        ExecContextData.ProcessVertex v22 = vs2.stream().filter(o->o.process.equals("feature-processing_matrix")).findFirst().orElseThrow();
        ExecContextData.ProcessVertex v23 = vs2.stream().filter(o->o.process.equals("mh.permute-variables-and-hyper-params")).findFirst().orElseThrow();

        assertEquals(1, findTargets(graph.processGraph, v21.process).size());
        assertEquals(1, findTargets(graph.processGraph, v22.process).size());
        assertEquals(2, findTargets(graph.processGraph, v23.process).size());

        ExecContextParamsYaml.Process p = graph.processes.stream().filter(o->o.processCode.equals("feature-processing_cluster")).findFirst().orElseThrow();
        assertEquals("ai", p.tags);
        assertEquals(-1, p.priority);
    }
}
