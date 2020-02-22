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

package ai.metaheuristic.ai.mh.dispatcher..source_code.graph;

import ai.metaheuristic.api.EnumsApi;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static ai.metaheuristic.ai.mh.dispatcher..data.SourceCodeData.*;
import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 2/17/2020
 * Time: 2:38 AM
 */
public class TestSourceCodeGraphLanguageYaml {

    @Test
    public void test_01() throws IOException {
        String sourceCode = IOUtils.resourceToString("/source_code/yaml/plan-for-preprocessing-and-classification-v1.yaml", StandardCharsets.UTF_8);
        AtomicLong contextId = new AtomicLong();
        SourceCodeGraph graph = SourceCodeGraphFactory.parse(EnumsApi.SourceCodeLang.yaml, sourceCode, () -> "" + contextId.incrementAndGet());

        assertNotNull(graph);
        assertTrue(graph.clean);
        assertEquals(6, graph.graph.vertexSet().size());
        assertEquals(5, SourceCodeGraphUtils.findDescendants(graph, 1L).size());
        assertEquals(1, SourceCodeGraphUtils.findLeafs(graph).size());

        SimpleTaskVertex v = SourceCodeGraphUtils.findVertex(graph, 1L);
        assertNotNull(v);
        assertEquals("assembly-raw-file", v.processCode);

        List<SimpleTaskVertex> vs1 = SourceCodeGraphUtils.findTargets(graph, 1L);

        assertEquals(1, vs1.size());

        SimpleTaskVertex v1 = vs1.get(0);
        assertNotNull(v1);
        assertEquals(Long.valueOf(2L), v1.taskId);
        assertEquals("dataset-processing", v1.processCode);

        List<SimpleTaskVertex> vs2 = SourceCodeGraphUtils.findTargets(graph, 2L);

        assertEquals(3, vs2.size());

        SimpleTaskVertex v21 = vs2.stream().filter(o->o.processCode.equals("feature-processing_cluster")).findFirst().orElseThrow();
        SimpleTaskVertex v22 = vs2.stream().filter(o->o.processCode.equals("feature-processing_matrix")).findFirst().orElseThrow();
        SimpleTaskVertex v23 = vs2.stream().filter(o->o.processCode.equals("mh.permute-variables-and-hyper-params")).findFirst().orElseThrow();

        assertEquals(1, SourceCodeGraphUtils.findTargets(graph, v21.taskId).size());
        assertEquals(1, SourceCodeGraphUtils.findTargets(graph, v22.taskId).size());
        assertEquals(1, SourceCodeGraphUtils.findTargets(graph, v23.taskId).size());

    }
}
