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

import ai.metaheuristic.ai.launchpad.data.SourceCodeData;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 2/17/2020
 * Time: 1:15 AM
 */
public class SourceCodeGraphUtils {

    public static void addNewTasksToGraph(
            SourceCodeData.SourceCodeGraph sourceCodeGraph, SourceCodeData.SimpleTaskVertex vertex, List<Long> parentTaskIds) {

        List<SourceCodeData.SimpleTaskVertex> parentVertices = sourceCodeGraph.graph.vertexSet()
                .stream()
                .filter(o -> parentTaskIds.contains(o.taskId))
                .collect(Collectors.toList());

        parentVertices.forEach(parentV -> sourceCodeGraph.graph.addEdge(parentV, vertex) );
    }

}
