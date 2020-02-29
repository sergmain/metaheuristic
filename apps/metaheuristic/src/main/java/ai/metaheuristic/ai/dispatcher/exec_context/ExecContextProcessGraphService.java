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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.GraphImporter;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.dot.DOTImporter;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.util.SupplierUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
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

    private final ExecContextCache execContextCache;

    private void changeGraph(ExecContextImpl execContext, Consumer<DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge>> callable) {
        ExecContextParamsYaml wpy = execContext.getExecContextParamsYaml();
        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = importProcessGraph(wpy);

        try {
            callable.accept(processGraph);
        } finally {
            wpy.processesGraph = asString(processGraph);
            execContext.updateParams(wpy);
            execContextCache.save(execContext);
        }
    }

    public static String asString(DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph) {
        Function<ExecContextData.ProcessVertex, String> vertexIdProvider = v -> v.id.toString();
        Function<ExecContextData.ProcessVertex, Map<String, Attribute>> vertexAttributeProvider = v -> {
            Map<String, Attribute> m = new HashMap<>();
            if (!S.b(v.process)) {
                m.put(PROCESS_NAME_ATTR, DefaultAttribute.createAttribute(v.process));
            }
            return m;
        };

        DOTExporter<ExecContextData.ProcessVertex, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider);
        exporter.setVertexAttributeProvider(vertexAttributeProvider);

        Writer writer = new StringWriter();
        exporter.exportGraph(processGraph, writer);
        //noinspection UnnecessaryLocalVariable
        String result = writer.toString();
        return result;
    }

/*
    @SneakyThrows
    public DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> importProcessGraph(ExecContextParamsYaml wpy) {
        GraphImporter<ExecContextData.ProcessVertex, DefaultEdge> importer = buildImporter();
        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        importer.importGraph(processGraph, new StringReader(wpy.processesGraph));

        return processGraph;
    }
*/

    @SneakyThrows
    public DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> importProcessGraph(ExecContextParamsYaml wpy) {
        DirectedAcyclicGraph<ExecContextData.ProcessVertex, DefaultEdge> processGraph = new DirectedAcyclicGraph<>(
                ExecContextData.ProcessVertex::new, SupplierUtil.DEFAULT_EDGE_SUPPLIER, false);
        AtomicLong id = new AtomicLong();
        processGraph.setVertexSupplier(()->new ExecContextData.ProcessVertex(id.incrementAndGet()));

        // https://stackoverflow.com/questions/60461351/import-graph-with-1-4-0
        DOTImporter<ExecContextData.ProcessVertex, DefaultEdge> importer = new DOTImporter<>();
        importer.addVertexAttributeConsumer(((vertex, attribute) -> vertex.getFirst().process = attribute.getValue()));

        importer.importGraph(processGraph, new StringReader(wpy.processesGraph));
        return processGraph;
    }

    private static ExecContextData.ProcessVertex toVertex(String id, Map<String, Attribute> attributes) {
        ExecContextData.ProcessVertex v = new ExecContextData.ProcessVertex();
        v.id = Long.valueOf(id);
        if (attributes==null) {
            throw new IllegalStateException("(attributes==null)");
        }

        final Attribute attr = attributes.get(PROCESS_NAME_ATTR);
        if (attr==null) {
            throw new IllegalStateException("attribute 'process' wasn't found");
        }
        v.process = attr.getValue();
        return v;
    }

//    private static final EdgeProvider<ExecContextData.ProcessVertex, DefaultEdge> ep = (f, t, l, attrs) -> new DefaultEdge();

    @NonNull
    public static String fixVertex(String vertex) {
        String fixVertex = vertex.strip();
        fixVertex = StringUtils.replaceEach(fixVertex, new String[]{"<", ">", " "}, new String[]{"_", "_", "_"});
        fixVertex = "<" + fixVertex + ">";
        return fixVertex;
    }

    private static GraphImporter<ExecContextData.ProcessVertex, DefaultEdge> buildImporter() {
        //noinspection UnnecessaryLocalVariable
        DOTImporter<ExecContextData.ProcessVertex, DefaultEdge> importer = new DOTImporter<>();
        return importer;
    }

    public static void addNewTasksToGraph(
            SourceCodeGraph sourceCodeGraph, ExecContextData.ProcessVertex vertex, List<ExecContextData.ProcessVertex> parentProcesses) {

        List<ExecContextData.ProcessVertex> parentVertices = sourceCodeGraph.processGraph.vertexSet()
                .stream()
                .filter(parentProcesses::contains)
                .collect(Collectors.toList());

        sourceCodeGraph.processGraph.addVertex(vertex);
        parentVertices.forEach(parentV -> sourceCodeGraph.processGraph.addEdge(parentV, vertex) );
    }

    public static Set<ExecContextData.ProcessVertex> findDescendants(SourceCodeGraph sourceCodeGraph, ExecContextData.ProcessVertex startVertex) {
        ExecContextData.ProcessVertex vertex = sourceCodeGraph.processGraph.vertexSet()
                .stream()
                .filter(startVertex::equals)
                .findFirst().orElse(null);
        if (vertex==null) {
            return Set.of();
        }

        Iterator<ExecContextData.ProcessVertex> iterator = new BreadthFirstIterator<>(sourceCodeGraph.processGraph, vertex);
        Set<ExecContextData.ProcessVertex> descendants = new HashSet<>();

        // Do not add start vertex to result.
        if (iterator.hasNext()) {
            iterator.next();
        }

        iterator.forEachRemaining(descendants::add);
        return descendants;
    }

    public static List<ExecContextData.ProcessVertex> findLeafs(SourceCodeGraph sourceCodeGraph) {
        //noinspection UnnecessaryLocalVariable
        List<ExecContextData.ProcessVertex> vertices = sourceCodeGraph.processGraph.vertexSet()
                .stream()
                .filter(o -> sourceCodeGraph.processGraph.outDegreeOf(o)==0)
                .collect(Collectors.toList());
        return vertices;
    }

    public static ExecContextData.ProcessVertex findVertex(SourceCodeGraph sourceCodeGraph, String process) {
        //noinspection UnnecessaryLocalVariable
        ExecContextData.ProcessVertex vertex = sourceCodeGraph.processGraph.vertexSet()
                .stream()
                .filter(v->v.process.equals(process))
                .findFirst()
                .orElse(null);

        return vertex;
    }

    public static List<ExecContextData.ProcessVertex> findTargets(SourceCodeGraph sourceCodeGraph, String process) {
        ExecContextData.ProcessVertex v = findVertex(sourceCodeGraph, process);
        if (v==null) {
            return List.of();
        }

        //noinspection UnnecessaryLocalVariable
        List<ExecContextData.ProcessVertex> vertices = sourceCodeGraph.processGraph.outgoingEdgesOf(v)
                .stream()
                .map(sourceCodeGraph.processGraph::getEdgeTarget)
                .collect(Collectors.toList());
        return vertices;
    }


}
