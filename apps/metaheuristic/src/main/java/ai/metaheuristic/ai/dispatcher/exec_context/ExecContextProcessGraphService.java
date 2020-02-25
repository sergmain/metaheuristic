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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.io.*;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.dispatcher.data.SourceCodeData.SourceCodeGraph;

/**
 * @author Serge
 * Date: 2/17/2020
 * Time: 1:15 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextProcessGraphService {

    private final ExecContextCache execContextCache;

    private void changeGraph(ExecContextImpl execContext, Consumer<DirectedAcyclicGraph<String, DefaultEdge>> callable) throws ImportException {
        ExecContextParamsYaml wpy = execContext.getExecContextParamsYaml();
        DirectedAcyclicGraph<String, DefaultEdge> processGraph = importProcessGraph(wpy);

        try {
            callable.accept(processGraph);
        } finally {
            ComponentNameProvider<String> vertexIdProvider = v -> v;
            ComponentAttributeProvider<String> vertexAttributeProvider = v -> new HashMap<>();

            DOTExporter<String, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider, null, null, vertexAttributeProvider, null);

            Writer writer = new StringWriter();
            exporter.exportGraph(processGraph, writer);
            wpy.processesGraph = writer.toString();
            execContext.updateParams(wpy);
            execContextCache.save(execContext);
        }
    }

    @SneakyThrows
    public DirectedAcyclicGraph<String, DefaultEdge> importProcessGraph(ExecContextParamsYaml wpy) {
        GraphImporter<String, DefaultEdge> importer = buildImporter();

        DirectedAcyclicGraph<String, DefaultEdge> processGraph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        importer.importGraph(processGraph, new StringReader(wpy.processesGraph));
        return processGraph;
    }

    private static String toVertex(String id, Map<String, Attribute> attributes) {
        return id;
    }

    private static final EdgeProvider<String, DefaultEdge> ep = (f, t, l, attrs) -> new DefaultEdge();

    private static GraphImporter<String, DefaultEdge> buildImporter() {
        //noinspection UnnecessaryLocalVariable
        DOTImporter<String, DefaultEdge> importer = new DOTImporter<>(ExecContextProcessGraphService::toVertex, ep);
        return importer;
    }

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
