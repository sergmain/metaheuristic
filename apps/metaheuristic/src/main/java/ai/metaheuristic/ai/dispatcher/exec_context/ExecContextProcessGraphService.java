/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.dot.DOTImporter;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.jgrapht.util.SupplierUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
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

    private static final String PROCESS_NAME_ATTR = "process";
    private static final String PROCESS_CONTEXT_ID_NAME_ATTR = "process_context_id";

    public static final DOTImporter<ExecContextData.ProcessVertex, DefaultEdge> DOT_IMPORTER = new DOTImporter<>();
    static {
        // https://stackoverflow.com/questions/60461351/import-graph-with-1-4-0
        DOT_IMPORTER.addVertexAttributeConsumer(((vertex, attribute) -> {
            switch (vertex.getSecond()) {
                case PROCESS_NAME_ATTR:
                    vertex.getFirst().process = attribute.getValue();
                    break;
                case PROCESS_CONTEXT_ID_NAME_ATTR:
                    vertex.getFirst().processContextId = attribute.getValue();
                    break;
                case "ID":
                    // do nothing
                    break;
                default:
                    log.error("Unknown attribute in process graph, attr: " + vertex.getSecond() + ", attr value: " + attribute.getValue());
            }
        }));
    }

    public static String asString(DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph) {
        Function<ExecContextData.ProcessVertex, String> vertexIdProvider = v -> v.id.toString();
        Function<ExecContextData.ProcessVertex, Map<String, Attribute>> vertexAttributeProvider = v -> {
            Map<String, Attribute> m = new HashMap<>();
            m.put(PROCESS_NAME_ATTR, DefaultAttribute.createAttribute(v.process));
            m.put(PROCESS_CONTEXT_ID_NAME_ATTR, DefaultAttribute.createAttribute(v.processContextId));
            return m;
        };

        DOTExporter<ExecContextData.ProcessVertex, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider);
        exporter.setVertexAttributeProvider(vertexAttributeProvider);

        StringWriter writer = new StringWriter();
        exporter.exportGraph(processGraph, writer);
        String result = writer.toString();
        return result;
    }

    public static DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> importProcessGraph(ExecContextParamsYaml wpy) {
        return importProcessGraph(wpy.processesGraph);
    }

    @SneakyThrows
    public static DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> importProcessGraph(String processGraphAsStr) {
        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = new DirectedAcyclicGraph<>(
                ExecContextData.ProcessVertex::new, SupplierUtil.DEFAULT_EDGE_SUPPLIER, false);
        AtomicLong id = new AtomicLong();
        processGraph.setVertexSupplier(() -> new ExecContextData.ProcessVertex(id.incrementAndGet()));

        try (final StringReader stringReader = new StringReader(processGraphAsStr)) {
            DOT_IMPORTER.importGraph(processGraph, stringReader);
        }
        return processGraph;
    }

    public static void addProcessVertexToGraph(
            DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph, ExecContextData.ProcessVertex vertex, Collection<ExecContextData.ProcessVertex> parentProcesses) {

        //noinspection SimplifyStreamApiCallChains
        List<ExecContextData.ProcessVertex> parentVertices = processGraph.vertexSet()
                .stream()
                .filter(parentProcesses::contains)
                .collect(Collectors.toList());

        processGraph.addVertex(vertex);
        parentVertices.forEach(parentV -> processGraph.addEdge(parentV, vertex) );
    }

    public static Set<ExecContextData.ProcessVertex> findDescendants(SourceCodeGraph sourceCodeGraph, ExecContextData.ProcessVertex startVertex) {
        return sourceCodeGraph.processGraph.getDescendants(startVertex);
    }

    public static Set<ExecContextData.ProcessVertex> findDirectAncestors(DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> graph, ExecContextData.ProcessVertex vertex) {
        Set<ExecContextData.ProcessVertex> ancestors = graph.incomingEdgesOf(vertex).stream().map(graph::getEdgeSource).collect(Collectors.toSet());
        return ancestors;
    }

    public static Set<ExecContextData.ProcessVertex> findAncestors(DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph, ExecContextData.ProcessVertex startVertex) {
        return processGraph.getAncestors(startVertex);
    }

    public static List<String> getTopologyOfProcesses(ExecContextParamsYaml execContextParamsYaml) {
        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = ExecContextProcessGraphService.importProcessGraph(execContextParamsYaml);

        TopologicalOrderIterator<ExecContextData.ProcessVertex, DefaultEdge> iterator = new TopologicalOrderIterator<>(processGraph);

        List<String> processes = new ArrayList<>();
        iterator.forEachRemaining(o->processes.add(o.process));
        return processes;
    }

    public static List<ExecContextData.ProcessVertex> findLeafs(SourceCodeGraph sourceCodeGraph) {
        List<ExecContextData.ProcessVertex> vertices = sourceCodeGraph.processGraph.vertexSet()
                .stream()
                .filter(o -> sourceCodeGraph.processGraph.outDegreeOf(o)==0)
                .collect(Collectors.toList());
        return vertices;
    }

    public static List<ExecContextData.ProcessVertex> findSubProcesses(
            DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph, String startProcessCode) {

        ExecContextData.ProcessVertex startVertex = findVertex(processGraph, startProcessCode);
        if (startVertex==null) {
            return List.of();
        }
        //noinspection SimplifyStreamApiCallChains
        List<String> ctxs = processGraph.outgoingEdgesOf(startVertex).stream()
                .map(processGraph::getEdgeTarget)
                .filter(o->!o.processContextId.equals(startVertex.processContextId) && o.processContextId.startsWith(startVertex.processContextId))
                .map(o->o.processContextId)
                .collect(Collectors.toList());
        TopologicalOrderIterator<ExecContextData.ProcessVertex, DefaultEdge> iterator = new TopologicalOrderIterator<>(processGraph);

        List<ExecContextData.ProcessVertex> vertices = new ArrayList<>();
        iterator.forEachRemaining(o->{
            if (ctxs.contains(o.processContextId)) {
                vertices.add(o);
            }
        });

        return vertices;
    }

    public static boolean anyError(SourceCodeGraph sourceCodeGraph) {
        boolean error = sourceCodeGraph.processGraph.vertexSet().stream().anyMatch(o -> S.b(o.process) || S.b(o.processContextId) );
        return error;
    }

    @Nullable
    public static ExecContextData.ProcessVertex findVertex(
            DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph, String process) {

        ExecContextData.ProcessVertex vertex = processGraph.vertexSet()
                .stream()
                .filter(v->v.process.equals(process))
                .findFirst()
                .orElse(null);

        return vertex;
    }

    public static List<ExecContextData.ProcessVertex> findTargets(
            DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph, String process) {
        ExecContextData.ProcessVertex v = findVertex(processGraph, process);
        if (v==null) {
            return List.of();
        }

        List<ExecContextData.ProcessVertex> vertices = processGraph.outgoingEdgesOf(v)
                .stream()
                .map(processGraph::getEdgeTarget)
                .collect(Collectors.toList());
        return vertices;
    }


}
