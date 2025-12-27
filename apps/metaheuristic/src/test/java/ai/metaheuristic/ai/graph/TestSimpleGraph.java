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

package ai.metaheuristic.ai.graph;

import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm.SingleSourcePaths;
import org.jgrapht.alg.interfaces.StrongConnectivityAlgorithm;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Serge
 * Date: 7/6/2019
 * Time: 2:10 AM
 */
public class TestSimpleGraph {

    @Test
    public void test() {

        // constructs a directed graph with the specified vertices and edges
        DirectedAcyclicGraph<String, DefaultEdge> directedGraph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        directedGraph.addVertex("a");
        directedGraph.addVertex("b");
        directedGraph.addVertex("c");
        directedGraph.addVertex("d");
        directedGraph.addVertex("e");
        directedGraph.addVertex("f");
        directedGraph.addVertex("g");
        directedGraph.addVertex("h");
        directedGraph.addVertex("i");
        directedGraph.addEdge("a", "b");
        directedGraph.addEdge("b", "d");
        directedGraph.addEdge("d", "c");
//        directedGraph.addEdge("c", "a");
        directedGraph.addEdge("e", "d");
        directedGraph.addEdge("e", "f");
        directedGraph.addEdge("f", "g");
//        directedGraph.addEdge("g", "e");
        directedGraph.addEdge("h", "e");
        directedGraph.addEdge("i", "h");

        // computes all the strongly connected components of the directed graph
        StrongConnectivityAlgorithm<String, DefaultEdge> scAlg =
                new KosarajuStrongConnectivityInspector<>(directedGraph);
        List<Graph<String, DefaultEdge>> stronglyConnectedSubgraphs =
                scAlg.getStronglyConnectedComponents();

        // prints the strongly connected components
        System.out.println("Strongly connected components:");
        for (int i = 0; i < stronglyConnectedSubgraphs.size(); i++) {
            System.out.println(stronglyConnectedSubgraphs.get(i));
        }
        System.out.println();

        // Prints the shortest path from vertex i to vertex c. This certainly
        // exists for our particular directed graph.
        System.out.println("Shortest path from i to c:");
        DijkstraShortestPath<String, DefaultEdge> dijkstraAlg =
                new DijkstraShortestPath<>(directedGraph);
        SingleSourcePaths<String, DefaultEdge> iPaths = dijkstraAlg.getPaths("i");
        System.out.println(iPaths.getPath("c") + "\n");

        // Prints the shortest path from vertex c to vertex i. This path does
        // NOT exist for our particular directed graph. Hence the path is
        // empty and the result must be null.
        System.out.println("Shortest path from c to i:");
        SingleSourcePaths<String, DefaultEdge> cPaths = dijkstraAlg.getPaths("c");
        System.out.println(cPaths.getPath("i"));


        outputGraph(asString(directedGraph));
        outputGraph(asString(new DirectedAcyclicGraph<>(DefaultEdge.class)));

    }

    private void outputGraph(String graphAsStr) {
        System.out.println("len: " + graphAsStr.length());
        System.out.println(graphAsStr);
        System.out.println("\n\n\n");
    }

    private static String asString(DirectedAcyclicGraph<String, DefaultEdge> graph) {
        Function<String, String> vertexIdProvider = v -> v;
        Function<String, Map<String, Attribute>> vertexAttributeProvider = v -> {
            Map<String, Attribute> m = new HashMap<>();
            m.put("attr", DefaultAttribute.createAttribute(v));
            return m;
        };

        DOTExporter<String, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider);
        exporter.setVertexAttributeProvider(vertexAttributeProvider);

        StringWriter writer = new StringWriter();
        exporter.exportGraph(graph, writer);
        return writer.toString();
    }
}
