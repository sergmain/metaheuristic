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

package ai.metaheuristic.ai.launchpad.source_code.graph;

import ai.metaheuristic.ai.launchpad.beans.ExecContextImpl;
import ai.metaheuristic.ai.launchpad.data.SourceCodeData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.traverse.BreadthFirstIterator;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.launchpad.data.SourceCodeData.*;

/**
 * @author Serge
 * Date: 2/17/2020
 * Time: 1:15 AM
 */
public class SourceCodeGraphUtils {

    public static void addNewTasksToGraph(
            SourceCodeGraph sourceCodeGraph, SimpleTaskVertex vertex, List<Long> parentTaskIds) {

        List<SimpleTaskVertex> parentVertices = sourceCodeGraph.graph.vertexSet()
                .stream()
                .filter(o -> parentTaskIds.contains(o.taskId))
                .collect(Collectors.toList());

        sourceCodeGraph.graph.addVertex(vertex);
        parentVertices.forEach(parentV -> sourceCodeGraph.graph.addEdge(parentV, vertex) );
    }

    public static Set<SimpleTaskVertex> findDescendants(SourceCodeGraph sourceCodeGraph, Long taskId) {
        SimpleTaskVertex vertex = sourceCodeGraph.graph.vertexSet()
                .stream()
                .filter(o -> taskId.equals(o.taskId))
                .findFirst().orElse(null);
        if (vertex==null) {
            return Set.of();
        }

        Iterator<SimpleTaskVertex> iterator = new BreadthFirstIterator<>(sourceCodeGraph.graph, vertex);
        Set<SimpleTaskVertex> descendants = new HashSet<>();

        // Do not add start vertex to result.
        if (iterator.hasNext()) {
            iterator.next();
        }

        iterator.forEachRemaining(descendants::add);
        return descendants;
    }

    public static List<SimpleTaskVertex> findLeafs(SourceCodeGraph sourceCodeGraph) {
        //noinspection UnnecessaryLocalVariable
        List<SimpleTaskVertex> vertices = sourceCodeGraph.graph.vertexSet()
                .stream()
                .filter(o -> sourceCodeGraph.graph.outDegreeOf(o)==0)
                .collect(Collectors.toList());
        return vertices;
    }

    public static SimpleTaskVertex findVertex(SourceCodeGraph sourceCodeGraph, Long taskId) {
        //noinspection UnnecessaryLocalVariable
        SimpleTaskVertex vertex = sourceCodeGraph.graph.vertexSet()
                .stream()
                .filter(o -> o.taskId.equals(taskId))
                .findFirst()
                .orElse(null);

        return vertex;
    }

    public static List<SimpleTaskVertex> findTargets(SourceCodeGraph sourceCodeGraph, Long taskId) {
        SimpleTaskVertex v = findVertex(sourceCodeGraph, taskId);
        if (v==null) {
            return List.of();
        }

        //noinspection UnnecessaryLocalVariable
        List<SimpleTaskVertex> vertices = sourceCodeGraph.graph.outgoingEdgesOf(v)
                .stream()
                .map(sourceCodeGraph.graph::getEdgeTarget)
                .collect(Collectors.toList());
        return vertices;
    }


}
