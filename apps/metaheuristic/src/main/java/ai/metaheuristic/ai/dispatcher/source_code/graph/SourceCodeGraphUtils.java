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

import org.jgrapht.traverse.BreadthFirstIterator;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.dispatcher.data.SourceCodeData.SourceCodeGraph;

/**
 * @author Serge
 * Date: 2/17/2020
 * Time: 1:15 AM
 */
public class SourceCodeGraphUtils {

    public static void addNewTasksToGraph(
            SourceCodeGraph sourceCodeGraph, String vertex, List<String> parentProcesses) {

        List<String> parentVertices = sourceCodeGraph.processGraph.vertexSet()
                .stream()
                .filter(parentProcesses::contains)
                .collect(Collectors.toList());

        sourceCodeGraph.processGraph.addVertex(vertex);
        parentVertices.forEach(parentV -> sourceCodeGraph.processGraph.addEdge(parentV, vertex) );
    }

    public static Set<String> findDescendants(SourceCodeGraph sourceCodeGraph, String process) {
        String vertex = sourceCodeGraph.processGraph.vertexSet()
                .stream()
                .filter(process::equals)
                .findFirst().orElse(null);
        if (vertex==null) {
            return Set.of();
        }

        Iterator<String> iterator = new BreadthFirstIterator<>(sourceCodeGraph.processGraph, vertex);
        Set<String> descendants = new HashSet<>();

        // Do not add start vertex to result.
        if (iterator.hasNext()) {
            iterator.next();
        }

        iterator.forEachRemaining(descendants::add);
        return descendants;
    }

    public static List<String> findLeafs(SourceCodeGraph sourceCodeGraph) {
        //noinspection UnnecessaryLocalVariable
        List<String> vertices = sourceCodeGraph.processGraph.vertexSet()
                .stream()
                .filter(o -> sourceCodeGraph.processGraph.outDegreeOf(o)==0)
                .collect(Collectors.toList());
        return vertices;
    }

    public static String findVertex(SourceCodeGraph sourceCodeGraph, String process) {
        //noinspection UnnecessaryLocalVariable
        String vertex = sourceCodeGraph.processGraph.vertexSet()
                .stream()
                .filter(process::equals)
                .findFirst()
                .orElse(null);

        return vertex;
    }

    public static List<String> findTargets(SourceCodeGraph sourceCodeGraph, String process) {
        String v = findVertex(sourceCodeGraph, process);
        if (v==null) {
            return List.of();
        }

        //noinspection UnnecessaryLocalVariable
        List<String> vertices = sourceCodeGraph.processGraph.outgoingEdgesOf(v)
                .stream()
                .map(sourceCodeGraph.processGraph::getEdgeTarget)
                .collect(Collectors.toList());
        return vertices;
    }


}
