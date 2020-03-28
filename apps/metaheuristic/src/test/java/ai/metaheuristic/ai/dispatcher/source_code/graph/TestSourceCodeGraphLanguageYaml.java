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

package ai.metaheuristic.ai.dispatcher.source_code.graph;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.api.EnumsApi;
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
        assertEquals(6, graph.processGraph.vertexSet().size());

        // it's 5, not 6, because mh.finish isn't defined in this SourceCode
        assertEquals(5, graph.processes.size());

        Map<String, Long> ids = new HashMap<>();
        AtomicLong currId = new AtomicLong();

        ExecContextData.ProcessVertex vertexAssembly = SourceCodeGraphLanguageYaml.getVertex(ids, currId, "assembly-raw-file");
        assertEquals(5, findDescendants(graph, vertexAssembly).size());
        assertEquals(1, findLeafs(graph).size());

        ExecContextData.ProcessVertex v = findVertex(graph, vertexAssembly.process);
        assertNotNull(v);

        List<ExecContextData.ProcessVertex> vs1 = findTargets(graph, vertexAssembly.process);

        assertEquals(1, vs1.size());

        ExecContextData.ProcessVertex v1 = vs1.get(0);
        assertNotNull(v1);
        String processDataset = "dataset-processing";
        assertEquals(processDataset, v1.process);

        List<ExecContextData.ProcessVertex> vs2 = findTargets(graph, processDataset);

        assertEquals(3, vs2.size());

        ExecContextData.ProcessVertex v21 = vs2.stream().filter(o->o.process.equals("feature-processing_cluster")).findFirst().orElseThrow();
        ExecContextData.ProcessVertex v22 = vs2.stream().filter(o->o.process.equals("feature-processing_matrix")).findFirst().orElseThrow();
        ExecContextData.ProcessVertex v23 = vs2.stream().filter(o->o.process.equals("mh.permute-variables-and-hyper-params")).findFirst().orElseThrow();

        assertEquals(1, findTargets(graph, v21.process).size());
        assertEquals(1, findTargets(graph, v22.process).size());
        assertEquals(1, findTargets(graph, v23.process).size());
    }
}
