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

package ai.metaheuristic.ai.dispatcher.exec_context_graph;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYamlUtils;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Sergio Lissner
 * Date: 11/18/2023
 * Time: 2:13 PM
 */
public class ValidateGraphStructureTest {


    @Test
    public void test_findAllRootVertices() {
        String yaml = """
            graph: "strict digraph G {\\r\\n  46651 [ ctxid=\\"1\\" ];\\r\\n  46652 [ ctxid=\\"1\\" ];\\r\\
              \\n  46653 [ ctxid=\\"1\\" ];\\r\\n  46654 [ ctxid=\\"1\\" ];\\r\\n  46655 [ ctxid=\\"1\\"\\
              \\ ];\\r\\n  46656 [ ctxid=\\"1\\" ];\\r\\n  46651 -> 46652;\\r\\n  46652 -> 46653;\\r\\n \\
              \\ 46654 -> 46655;\\r\\n  46655 -> 46656;\\r\\n}\\r\\n"
            version: 1
            """;

        ExecContextGraphParamsYaml ecgpy = ExecContextGraphParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = ExecContextGraphService.importExecContextGraph(ecgpy.graph);


        List<ExecContextData.TaskVertex> l = ExecContextGraphService.findAllRootVertices(graph);


        assertEquals(2, l.size());

        assertFalse(ExecContextGraphService.verifyGraph(graph));

    }
}
