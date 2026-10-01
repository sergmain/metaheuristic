/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.dot.DOTImporter;
import org.jgrapht.util.SupplierUtil;
import org.jspecify.annotations.Nullable;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Function;

/**
 * DOT in and out for the segment algebra (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 4, decision 13: the DOT is a view
 * derived from segments). The format is the one the whole-ExecContext graph stores: vertex id = Task id, attribute
 * {@code ctxid} = taskContextId, attribute {@code tag} only for a tagged Task. Independent of
 * {@code ExecContextGraphService}, which is removed once every ExecContext is segmented.
 *
 * <p>Error code prefix: {@code 01.907.} (unique to this class).
 */
public final class SegmentDotUtils {

    private static final String CTX_ATTR = "ctxid";
    private static final String TAG_ATTR = "tag";

    private SegmentDotUtils() {
    }

    /** A DOT vertex while it is being imported; identity is the Task id. */
    private static final class DotVertex {
        final long taskId;
        @Nullable String ctx;
        @Nullable String tag;

        DotVertex(long taskId) {
            this.taskId = taskId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof DotVertex v && v.taskId == taskId;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(taskId);
        }
    }

    /**
     * Parses a DOT into a {@link SegmentData.Graph}. A cycle, a vertex without {@code ctxid}, or unparsable text fails.
     */
    public static SegmentData.Graph parse(String dot) {
        final DirectedAcyclicGraph<DotVertex, DefaultEdge> g = new DirectedAcyclicGraph<>(
                null, SupplierUtil.DEFAULT_EDGE_SUPPLIER, false);
        final DOTImporter<DotVertex, DefaultEdge> importer = new DOTImporter<>();
        importer.setVertexFactory(id -> new DotVertex(Long.parseLong(id)));
        importer.addVertexAttributeConsumer((p, attr) -> {
            switch (p.getSecond()) {
                case CTX_ATTR -> p.getFirst().ctx = attr.getValue();
                case TAG_ATTR -> p.getFirst().tag = attr.getValue();
                default -> { }
            }
        });
        try {
            importer.importGraph(g, new StringReader(dot));
        }
        catch (RuntimeException e) {
            throw new IllegalStateException("01.907.020 DOT can't be imported as an acyclic graph: " + e.getMessage(), e);
        }

        final Map<Long, SegmentData.Node> nodes = new LinkedHashMap<>();
        for (DotVertex v : g.vertexSet()) {
            if (v.ctx == null) {
                throw new IllegalStateException("01.907.040 vertex #" + v.taskId + " has no " + CTX_ATTR);
            }
            nodes.put(v.taskId, new SegmentData.Node(v.taskId, v.ctx, v.tag));
        }
        final Set<SegmentData.Edge> edges = new LinkedHashSet<>();
        for (DefaultEdge e : g.edgeSet()) {
            edges.add(new SegmentData.Edge(g.getEdgeSource(e).taskId, g.getEdgeTarget(e).taskId));
        }
        return new SegmentData.Graph(nodes, edges);
    }

    /**
     * Writes a graph as DOT, vertices and edges in a stable order (vertices by Task id, edges by source then target).
     */
    public static String toDot(SegmentData.Graph graph) {
        final DirectedAcyclicGraph<DotVertex, DefaultEdge> g = new DirectedAcyclicGraph<>(
                null, SupplierUtil.DEFAULT_EDGE_SUPPLIER, false);
        final Map<Long, DotVertex> byId = new HashMap<>();
        graph.nodes().values().stream()
                .sorted(Comparator.comparingLong(SegmentData.Node::taskId))
                .forEach(n -> {
                    DotVertex v = new DotVertex(n.taskId());
                    v.ctx = n.ctx();
                    v.tag = n.tag();
                    byId.put(n.taskId(), v);
                    g.addVertex(v);
                });
        graph.edges().stream()
                .sorted(Comparator.comparingLong(SegmentData.Edge::from).thenComparingLong(SegmentData.Edge::to))
                .forEach(e -> {
                    DotVertex from = byId.get(e.from());
                    DotVertex to = byId.get(e.to());
                    if (from == null || to == null) {
                        throw new IllegalStateException("01.907.060 edge " + e + " refers to a Task that is not in the graph");
                    }
                    g.addEdge(from, to);
                });

        final Function<DotVertex, String> idProvider = v -> Long.toString(v.taskId);
        final DOTExporter<DotVertex, DefaultEdge> exporter = new DOTExporter<>(idProvider);
        exporter.setVertexAttributeProvider(v -> {
            Map<String, Attribute> m = new LinkedHashMap<>();
            m.put(CTX_ATTR, DefaultAttribute.createAttribute(v.ctx));
            if (v.tag != null) {
                m.put(TAG_ATTR, DefaultAttribute.createAttribute(v.tag));
            }
            return m;
        });
        final StringWriter writer = new StringWriter();
        exporter.exportGraph(g, writer);
        return writer.toString();
    }
}
