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
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.dot.DOTImporter;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jgrapht.util.SupplierUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 7/6/2019
 * Time: 10:42 PM
 */
@SuppressWarnings("UnnecessaryLocalVariable")
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
class ExecContextGraphService_140 {

    private static final String TASK_ID_ATTR = "task_id";
    private static final String EXEC_STATE_ATTR = "exec_state";

    private final ExecContextCache execContextCache;

    private void changeGraph(ExecContextImpl execContext, Consumer<DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge>> callable) {
        ExecContextParamsYaml ecpy = execContext.getExecContextParamsYaml();
/*
        GraphImporter<ExecContextData.TaskVertex_140, DefaultEdge> importer = buildImporter();

        DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        importer.importGraph(graph, new StringReader(wpy.graph));
*/
        DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph = importProcessGraph(ecpy);

        try {
            callable.accept(graph);
        } finally {
            ecpy.graph = asString(graph);
            execContext.updateParams(ecpy);
            execContextCache.save(execContext);
        }
    }

    private static String asString(DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph) {
        Function<ExecContextData.TaskVertex_140, String> vertexIdProvider = v -> v.id.toString();
        Function<ExecContextData.TaskVertex_140, Map<String, Attribute>> vertexAttributeProvider = v -> {
            Map<String, Attribute> m = new HashMap<>();
            m.put(TASK_ID_ATTR, DefaultAttribute.createAttribute(v.taskId.toString()));
            m.put(EXEC_STATE_ATTR, DefaultAttribute.createAttribute(v.execState.toString()));
            return m;
        };

        DOTExporter<ExecContextData.TaskVertex_140, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider);
        exporter.setVertexAttributeProvider(vertexAttributeProvider);

        StringWriter writer = new StringWriter();
        exporter.exportGraph(graph, writer);
        return writer.toString();
    }

    private List<ExecContextData.TaskVertex_140> readOnlyGraphListOfTaskVertex(
            ExecContextImpl execContext,
            Function<DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge>, List<ExecContextData.TaskVertex_140>> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph = prepareGraph(execContext);
        return callable.apply(graph);
    }

    private @NonNull Set<ExecContextData.TaskVertex_140> readOnlyGraphSetOfTaskVertex(
            @NonNull ExecContextImpl execContext,
            @NonNull Function<DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge>, Set<ExecContextData.TaskVertex_140>> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph = prepareGraph(execContext);
        Set<ExecContextData.TaskVertex_140> apply = callable.apply(graph);
        return apply!=null ? apply : Set.of();
    }

    private @Nullable ExecContextData.TaskVertex_140 readOnlyGraphTaskVertex(
            @NonNull ExecContextImpl execContext,
            @NonNull Function<DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge>, ExecContextData.TaskVertex_140> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph = prepareGraph(execContext);
        return callable.apply(graph);
    }

    private long readOnlyGraphLong(@NonNull ExecContextImpl execContext, @NonNull Function<DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge>, @lombok.NonNull Long> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph = prepareGraph(execContext);
        return callable.apply(graph);
    }

    private @NonNull DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> prepareGraph(@NonNull ExecContextImpl execContext) {
        return importProcessGraph(execContext.getExecContextParamsYaml());
/*
        ExecContextParamsYaml wpy = execContext.getExecContextParamsYaml();
        DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        if (S.b(wpy.graph)) {
            return graph;
        }
        GraphImporter<ExecContextData.TaskVertex_140, DefaultEdge> importer = buildImporter();
        importer.importGraph(graph, new StringReader(wpy.graph));
        return graph;
*/
    }

    @SneakyThrows
    public static DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> importProcessGraph(ExecContextParamsYaml wpy) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph = new DirectedAcyclicGraph<>(
                ExecContextData.TaskVertex_140::new, SupplierUtil.DEFAULT_EDGE_SUPPLIER, false);
        AtomicLong id = new AtomicLong();
        graph.setVertexSupplier(()->new ExecContextData.TaskVertex_140(id.incrementAndGet()));

        // https://stackoverflow.com/questions/60461351/import-graph-with-1-4-0
        DOTImporter<ExecContextData.TaskVertex_140, DefaultEdge> importer = new DOTImporter<>();
        importer.addVertexAttributeConsumer(((vertex, attribute) -> {
            switch(vertex.getSecond()) {
                case EXEC_STATE_ATTR:
                    vertex.getFirst().execState = EnumsApi.TaskExecState.valueOf(attribute.getValue());
                    break;
                case TASK_ID_ATTR:
                    vertex.getFirst().taskId = Long.valueOf(attribute.getValue());
                    break;
                case "ID":
                    // do nothing
                    break;
                default:
                    log.error("Unknown attribute in vertex: " + vertex.getSecond()+", attr value: " + attribute.getValue());
            }
        }));

        importer.importGraph(graph, new StringReader(wpy.graph));
        return graph;
    }

/*
    private static ExecContextData.TaskVertex_140 toTaskVertex(String id, Map<String, Attribute> attributes) {
        ExecContextData.TaskVertex_140 v = new ExecContextData.TaskVertex_140();
        v.taskId = Long.valueOf(id);
        if (attributes==null) {
            return v;
        }

        final Attribute execState = attributes.get(EXEC_STATE_ATTR);
        if (execState!=null) {
            v.execState = EnumsApi.TaskExecState.valueOf(execState.getValue());
        }
        return v;
    }
*/

/*
    private static final EdgeProvider<ExecContextData.TaskVertex_140, DefaultEdge> ep = (f, t, l, attrs) -> new DefaultEdge();

    private static GraphImporter<ExecContextData.TaskVertex_140, DefaultEdge> buildImporter() {
        DOTImporter<ExecContextData.TaskVertex_140, DefaultEdge> importer = new DOTImporter<>(ExecContextGraphService::toTaskVertex, ep);
        return importer;
    }
*/

    public ExecContextOperationStatusWithTaskList updateTaskExecStates(ExecContextImpl execContext, ConcurrentHashMap<Long, Integer> taskStates) {
        final ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList();
        status.status = OperationStatusRest.OPERATION_STATUS_OK;
        if (taskStates==null || taskStates.isEmpty()) {
            return status;
        }
        try {
            changeGraph(execContext, graph -> {
                List<ExecContextData.TaskVertex_140> tvs = graph.vertexSet()
                        .stream()
                        .filter(o -> taskStates.containsKey(o.taskId))
                        .collect(Collectors.toList());

                // Don't join streams, a side-effect could be occurred
                tvs.forEach(taskVertex -> {
                    taskVertex.execState = EnumsApi.TaskExecState.from(taskStates.get(taskVertex.taskId));
                    if (taskVertex.execState == EnumsApi.TaskExecState.ERROR || taskVertex.execState == EnumsApi.TaskExecState.BROKEN) {
                        setStateForAllChildrenTasksInternal(graph, taskVertex.taskId, new ExecContextOperationStatusWithTaskList(), EnumsApi.TaskExecState.BROKEN);
                    }
                    else if (taskVertex.execState == EnumsApi.TaskExecState.OK) {
                        setStateForAllChildrenTasksInternal(graph, taskVertex.taskId, new ExecContextOperationStatusWithTaskList(), EnumsApi.TaskExecState.NONE);
                    }
                });
            });
        }
        catch (Throwable th) {
            log.error("Error updating graph", th);
            status.status = new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage());
        }
        return status;
    }

    public ExecContextOperationStatusWithTaskList updateTaskExecState(ExecContextImpl execContext, Long taskId, int execState) {
        final ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList();
        try {
            changeGraph(execContext, graph -> {
                ExecContextData.TaskVertex_140 tv = graph.vertexSet()
                        .stream()
                        .filter(o -> o.taskId.equals(taskId))
                        .findFirst()
                        .orElse(null);

                // Don't combine streams, a side-effect could be occurred
                if (tv!=null) {
                    tv.execState = EnumsApi.TaskExecState.from(execState);
                    if (tv.execState==EnumsApi.TaskExecState.ERROR) {
                        setStateForAllChildrenTasksInternal(graph, tv.taskId, status, EnumsApi.TaskExecState.BROKEN);
                    }
                    else if (tv.execState==EnumsApi.TaskExecState.OK) {
                        setStateForAllChildrenTasksInternal(graph, tv.taskId, status, EnumsApi.TaskExecState.NONE);
                    }
                }
            });
            status.status = OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable th) {
            log.error("Error while updating graph", th);
            status.status = new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage());
        }
        return status;
    }

    public long getCountUnfinishedTasks(ExecContextImpl execContext) {
        try {
            return readOnlyGraphLong(execContext, graph -> graph
                    .vertexSet()
                    .stream()
                    .filter(o -> o.execState==EnumsApi.TaskExecState.NONE || o.execState==EnumsApi.TaskExecState.IN_PROGRESS)
                    .count());
        }
        catch (Throwable th) {
            log.error("#915.010 Error", th);
            return 0L;
        }
    }

    public List<ExecContextData.TaskVertex_140> getUnfinishedTaskVertices(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> graph
                    .vertexSet()
                    .stream()
                    .filter(o -> o.execState==EnumsApi.TaskExecState.NONE || o.execState==EnumsApi.TaskExecState.IN_PROGRESS)
                    .collect(Collectors.toList()));
        }
        catch (Throwable th) {
            log.error("#915.010 Error", th);
            return List.of();
        }
    }

/*    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(ExecContextImpl execContext, Long taskId) {
        try {
            final ExecContextOperationStatusWithTaskList withTaskList = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
            changeGraph(execContext, graph -> {

                Set<ExecContextData.TaskVertex_140> set = findDescendantsInternal(graph, taskId);
                set.forEach( t->{
                    t.execState = EnumsApi.TaskExecState.NONE;
                });
                if (true) {
                    throw new IllegalStateException("need to be re-written");
                }
                withTaskList.childrenTasks.addAll((Set)set);
            });
            return withTaskList;
        }
        catch (Throwable th) {
            return new ExecContextOperationStatusWithTaskList(new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage()), List.of());
        }
    }*/

    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(ExecContextImpl execContext, Long taskId) {
        try {
            final ExecContextOperationStatusWithTaskList withTaskList = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
            changeGraph(execContext, graph -> {

                Set<ExecContextData.TaskVertex_140> set = findDescendantsInternal(graph, taskId);
                set.forEach( t-> t.execState = EnumsApi.TaskExecState.NONE);
                withTaskList.childrenTasks.addAll(set);
            });
            return withTaskList;
        }
        catch (Throwable th) {
            return new ExecContextOperationStatusWithTaskList(new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage()), List.of());
        }
    }

    public List<ExecContextData.TaskVertex_140> findLeafs(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {

                try {
                    List<ExecContextData.TaskVertex_140> vertices = graph.vertexSet()
                            .stream()
                            .filter(o -> graph.outDegreeOf(o)==0)
                            .collect(Collectors.toList());
                    return vertices;
                } catch (Throwable th) {
                    log.error("#915.019 error", th);
                    throw new RuntimeException("Error", th);
                }
            });
        }
        catch (Throwable th) {
            log.error("#915.020 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return List.of();
        }
    }

    public Set<ExecContextData.TaskVertex_140> findDirectDescendants(ExecContextImpl execContext, Long taskId) {
        try {
            return readOnlyGraphSetOfTaskVertex(execContext, graph -> findDirectDescendantsInternal(graph, taskId));
        }
        catch (Throwable th) {
            log.error("#916.140 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return Set.of();
        }
    }

    private Set<ExecContextData.TaskVertex_140> findDirectDescendantsInternal(DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph, Long taskId) {
        ExecContextData.TaskVertex_140 vertex = graph.vertexSet()
                .stream()
                .filter(o -> taskId.equals(o.taskId))
                .findFirst().orElse(null);
        if (vertex==null) {
            return Set.of();
        }

        Set<ExecContextData.TaskVertex_140> descendants = graph.outgoingEdgesOf(vertex).stream().map(graph::getEdgeTarget).collect(Collectors.toSet());
        return descendants;
    }

    public Set<ExecContextData.TaskVertex_140> findDescendants(ExecContextImpl execContext, Long taskId) {
        try {
            return readOnlyGraphSetOfTaskVertex(execContext, graph -> findDescendantsInternal(graph, taskId));
        }
        catch (Throwable th) {
            log.error("#915.022 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return Set.of();
        }
    }

    private Set<ExecContextData.TaskVertex_140> findDescendantsInternal(DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph, Long taskId) {
        ExecContextData.TaskVertex_140 vertex = graph.vertexSet()
                .stream()
                .filter(o -> taskId.equals(o.taskId))
                .findFirst().orElse(null);
        if (vertex==null) {
            return Set.of();
        }

        Set<ExecContextData.TaskVertex_140> descendants = graph.getDescendants(vertex);
        return descendants;
    }

    public List<ExecContextData.TaskVertex_140> findAllForAssigning(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {

                // if this is newly created graph then return only start vertex of graph
                ExecContextData.TaskVertex_140 startVertex = graph.vertexSet().stream()
                        .filter( v -> v.execState== EnumsApi.TaskExecState.NONE && graph.incomingEdgesOf(v).isEmpty())
                        .findFirst()
                        .orElse(null);

                if (startVertex!=null) {
                    return List.of(startVertex);
                }

                // get all non-processed tasks
//                Iterator<ExecContextData.TaskVertex_140> iterator = new BreadthFirstIterator<>(graph, (ExecContextData.TaskVertex_140)null);
                Iterator<ExecContextData.TaskVertex_140> iterator = new DepthFirstIterator<>(graph, (ExecContextData.TaskVertex_140)null);
                List<ExecContextData.TaskVertex_140> vertices = new ArrayList<>();

                iterator.forEachRemaining(v -> {
                    if (v.execState==EnumsApi.TaskExecState.NONE) {
                        // remove all tasks which have non-processed tasks as direct parent
                        if (isParentFullyProcessedWithoutErrors(graph, v)) {
                            vertices.add(v);
                        }
                    }
                });

                return vertices;
            });
        }
        catch (Throwable th) {
            log.error("#915.030 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return List.of();
        }
    }

    public List<ExecContextData.TaskVertex_140> findAllBroken(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {
                return graph.vertexSet().stream()
                        .filter( v -> v.execState == EnumsApi.TaskExecState.BROKEN || v.execState == EnumsApi.TaskExecState.ERROR )
                        .collect(Collectors.toList());
            });
        }
        catch (Throwable th) {
            log.error("#915.030 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return List.of();
        }
    }

    private boolean isParentFullyProcessedWithoutErrors(DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph, ExecContextData.TaskVertex_140 vertex) {
        // todo 2020-03-15 actually, we don't need to get all ancestors, we need only direct.
        //  So it can be done just with edges
        for (ExecContextData.TaskVertex_140 ancestor : graph.getAncestors(vertex)) {
            if (ancestor.execState!=EnumsApi.TaskExecState.OK) {
                return false;
            }
        }
        return true;
    }

    public @NonNull List<ExecContextData.TaskVertex_140> findAll(@NonNull ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {
                List<ExecContextData.TaskVertex_140> vertices = new ArrayList<>(graph.vertexSet());
                return vertices;
            });
        }
        catch (Throwable th) {
            log.error("#915.030 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return List.of();
        }
    }

    public @Nullable ExecContextData.TaskVertex_140 findVertex(@NonNull ExecContextImpl execContext, @NonNull Long taskId) {
        try {
            return readOnlyGraphTaskVertex(execContext, graph -> {
                ExecContextData.TaskVertex_140 vertex = graph.vertexSet()
                        .stream()
                        .filter(o -> o.taskId.equals(taskId))
                        .findFirst()
                        .orElse(null);
                return vertex;
            });
        }
        catch (Throwable th) {
            log.error("#915.040 Error", th);
            return null;
        }
    }

    public ExecContextOperationStatusWithTaskList updateGraphWithSettingAllChildrenTasksAsBroken(ExecContextImpl execContext, Long taskId) {
        try {
            final ExecContextOperationStatusWithTaskList withTaskList = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
            changeGraph(execContext, graph -> setStateForAllChildrenTasksInternal(graph, taskId, withTaskList, EnumsApi.TaskExecState.BROKEN));
            return withTaskList;
        }
        catch (Throwable th) {
            log.error("#915.050 Error", th);
            return new ExecContextOperationStatusWithTaskList(new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage()), List.of());
        }
    }

/*
    private void setStateForAllChildrenTasksInternal(
            DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph,
            Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {

        Set<ExecContextData.TaskVertex_140> set = findDescendantsInternal(graph, taskId);
        set.forEach( t->{
            t.execState = state;
        });
        if (true) {
            throw new IllegalStateException("need to be re-written");
        }
        withTaskList.childrenTasks.addAll((Set)set);
    }
*/

    private void setStateForAllChildrenTasksInternal(
            DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph,
            Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {

        Set<ExecContextData.TaskVertex_140> set = findDescendantsInternal(graph, taskId);
        // find and filter a 'mh.finish' vertex, which doesn't have any outgoing edges
        set.stream().filter(tv -> !graph.outgoingEdgesOf(tv).isEmpty()).forEach( tv-> tv.execState = state);
        withTaskList.childrenTasks.addAll(set);
    }

    public OperationStatusRest addNewTasksToGraph(ExecContextImpl execContext, List<Long> parentTaskIds, List<Long> taskIds) {
        try {
            changeGraph(execContext, graph -> {
                List<ExecContextData.TaskVertex_140> vertices = graph.vertexSet()
                        .stream()
                        .filter(o -> parentTaskIds.contains(o.taskId))
                        .collect(Collectors.toList());;

                taskIds.forEach(taskId -> {
                    final ExecContextData.TaskVertex_140 v = graph.addVertex();
                    v.taskId = taskId;
                    v.execState = EnumsApi.TaskExecState.NONE;
//                    final ExecContextData.TaskVertex_140 v = new ExecContextData.TaskVertex_140(taskId, EnumsApi.TaskExecState.NONE);
                    vertices.forEach(parentV -> graph.addEdge(parentV, v) );
                });
            });
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable th) {
            log.error("Error while adding task to graph", th);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage());
        }
    }

    @Nullable
    public Void createEdges(ExecContextImpl execContext, List<Long> lastIds, Set<ExecContextData.TaskVertex_140> descendants) {
        changeGraph(execContext, graph ->
                graph.vertexSet().stream()
                        .filter(o -> lastIds.contains(o.taskId))
                        .forEach(parentV-> descendants.forEach(trgV -> graph.addEdge(parentV, trgV)))
        );
        return null;
    }

    public List<List<ExecContextData.TaskVertex_140>> graphAsListOfLIst(ExecContextImpl execContext) {
        try {
            DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph = prepareGraph(execContext);
            List<List<ExecContextData.TaskVertex_140>> list = new ArrayList<>();

            // get head of graph
            List<ExecContextData.TaskVertex_140> vertices = graph.vertexSet()
                    .stream()
                    .filter(o -> graph.getAncestors(o).isEmpty())
                    .collect(Collectors.toList());

            while (!vertices.isEmpty()) {
                list.add(vertices);

                List<ExecContextData.TaskVertex_140> nextLine = new ArrayList<>();
                for (ExecContextData.TaskVertex_140 vertex : vertices) {
                    Set<ExecContextData.TaskVertex_140> descendants = graph.getDescendants(vertex);
                    nextLine.addAll(descendants);
                }
                vertices = nextLine;
            }
            return list;

        }
        catch (Throwable th) {
            log.error("#916.120 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return List.of();
        }
    }

    public Set<ExecContextData.TaskVertex_140> findDirectAncestors(ExecContextImpl execContext, ExecContextData.TaskVertex_140 vertex) {
        if (vertex==null) {
            return Set.of();
        }
        try {
            return readOnlyGraphSetOfTaskVertex(execContext, graph -> findDirectAncestorsInternal(graph, vertex));
        }
        catch (Throwable th) {
            log.error("#916.145 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return Set.of();
        }
    }

    private Set<ExecContextData.TaskVertex_140> findDirectAncestorsInternal(DirectedAcyclicGraph<ExecContextData.TaskVertex_140, DefaultEdge> graph, ExecContextData.TaskVertex_140 vertex) {
        Set<ExecContextData.TaskVertex_140> ancestors = graph.incomingEdgesOf(vertex).stream().map(graph::getEdgeSource).collect(Collectors.toSet());
        return ancestors;
    }

}
