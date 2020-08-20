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
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.util.SupplierUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 7/6/2019
 * Time: 10:42 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("WeakerAccess")
class ExecContextGraphService {

    private static final String TASK_ID_STR_ATTR = "task_id_str";
    private static final String TASK_EXEC_STATE_ATTR = "task_exec_state";

    private final ExecContextCache execContextCache;

    private void changeGraph(ExecContextImpl execContext, Consumer<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>> callable) {
        ExecContextParamsYaml ecpy = execContext.getExecContextParamsYaml();
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = importProcessGraph(ecpy);
        try {
            callable.accept(graph);
        } finally {
            ecpy.graph = asString(graph);
            execContext.updateParams(ecpy);
            execContextCache.save(execContext);
        }
    }

    public static String asString(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph) {
        Function<ExecContextData.TaskVertex, String> vertexIdProvider = v -> v.taskId.toString();
        Function<ExecContextData.TaskVertex, Map<String, Attribute>> vertexAttributeProvider = v -> {
            Map<String, Attribute> m = new HashMap<>();
            m.put(TASK_ID_STR_ATTR, DefaultAttribute.createAttribute(v.taskIdStr));
            m.put(TASK_EXEC_STATE_ATTR, DefaultAttribute.createAttribute(v.execState.toString()));
            return m;
        };

        DOTExporter<ExecContextData.TaskVertex, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider);
        exporter.setVertexAttributeProvider(vertexAttributeProvider);

        StringWriter writer = new StringWriter();
        exporter.exportGraph(graph, writer);
        return writer.toString();
    }

    private List<ExecContextData.TaskVertex> readOnlyGraphListOfTaskVertex(
            ExecContextImpl execContext,
            Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, List<ExecContextData.TaskVertex>> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        return callable.apply(graph);
    }

    private @NonNull Set<ExecContextData.TaskVertex> readOnlyGraphSetOfTaskVertex(
            @NonNull ExecContextImpl execContext,
            @NonNull Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, Set<ExecContextData.TaskVertex>> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        Set<ExecContextData.TaskVertex> apply = callable.apply(graph);
        return apply!=null ? apply : Set.of();
    }

    private @Nullable
    ExecContextData.TaskVertex readOnlyGraphTaskVertex(
            @NonNull ExecContextImpl execContext,
            @NonNull Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, ExecContextData.TaskVertex> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        return callable.apply(graph);
    }

    private long readOnlyGraphLong(@NonNull ExecContextImpl execContext, @NonNull Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, @lombok.NonNull Long> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        return callable.apply(graph);
    }

    private DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> prepareGraph(ExecContextImpl execContext) {
        return importProcessGraph(execContext.getExecContextParamsYaml());
    }

    @SneakyThrows
    public static DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> importProcessGraph(ExecContextParamsYaml wpy) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = new DirectedAcyclicGraph<>(
                ExecContextData.TaskVertex::new, SupplierUtil.DEFAULT_EDGE_SUPPLIER, false);

        // https://stackoverflow.com/questions/60461351/import-graph-with-1-4-0
        DOTImporter<ExecContextData.TaskVertex, DefaultEdge> importer = new DOTImporter<>();
        importer.setVertexFactory(id->new ExecContextData.TaskVertex(Long.parseLong(id)));
        importer.addVertexAttributeConsumer(((vertex, attribute) -> {
            switch(vertex.getSecond()) {
                case TASK_EXEC_STATE_ATTR:
                    vertex.getFirst().execState = EnumsApi.TaskExecState.valueOf(attribute.getValue());
                    break;
                case TASK_ID_STR_ATTR:
                    vertex.getFirst().taskIdStr = attribute.getValue();
                    break;
                case "ID":
                    // do nothing
                    break;
                default:
                    log.error("Unknown attribute in task graph, attr: " + vertex.getSecond()+", attr value: " + attribute.getValue());
            }
        }));

        importer.importGraph(graph, new StringReader(wpy.graph));
        return graph;
    }

    public ExecContextOperationStatusWithTaskList updateTaskExecStates(ExecContextImpl execContext, ConcurrentHashMap<Long, Integer> taskStates) {
        final ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList();
        status.status = OperationStatusRest.OPERATION_STATUS_OK;
        if (taskStates==null || taskStates.isEmpty()) {
            return status;
        }
        try {
            changeGraph(execContext, graph -> {
                List<ExecContextData.TaskVertex> tvs = graph.vertexSet()
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
                    else if (taskVertex.execState == EnumsApi.TaskExecState.SKIPPED) {
                        // todo 2020-08-16 need to decide what to do here
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
                ExecContextData.TaskVertex tv = graph.vertexSet()
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
                    else if (tv.execState == EnumsApi.TaskExecState.SKIPPED) {
                        // todo 2020-08-16 need to decide what to do here
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

    public List<ExecContextData.TaskVertex> getUnfinishedTaskVertices(ExecContextImpl execContext) {
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

    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(ExecContextImpl execContext, Long taskId) {
        try {
            final ExecContextOperationStatusWithTaskList withTaskList = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
            changeGraph(execContext, graph -> {

                Set<ExecContextData.TaskVertex> set = findDescendantsInternal(graph, taskId);
                set.forEach( t-> t.execState = EnumsApi.TaskExecState.NONE);
                withTaskList.childrenTasks.addAll(set);
            });
            return withTaskList;
        }
        catch (Throwable th) {
            return new ExecContextOperationStatusWithTaskList(new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage()), List.of());
        }
    }

    public List<ExecContextData.TaskVertex> findLeafs(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {

                try {
                    List<ExecContextData.TaskVertex> vertices = graph.vertexSet()
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

    public Set<ExecContextData.TaskVertex> findDescendants(ExecContextImpl execContext, Long taskId) {
        try {
            return readOnlyGraphSetOfTaskVertex(execContext, graph -> findDescendantsInternal(graph, taskId));
        }
        catch (Throwable th) {
            log.error("#915.022 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return Set.of();
        }
    }

    public List<List<ExecContextData.TaskVertex>> graphAsListOfLIst(ExecContextImpl execContext) {
        try {
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
            List<List<ExecContextData.TaskVertex>> list = new ArrayList<>();

            // get head of graph
            List<ExecContextData.TaskVertex> vertices = graph.vertexSet()
                    .stream()
                    .filter(o -> graph.getAncestors(o).isEmpty())
                    .collect(Collectors.toList());

            while (!vertices.isEmpty()) {
                list.add(vertices);

                List<ExecContextData.TaskVertex> nextLine = new ArrayList<>();
                for (ExecContextData.TaskVertex vertex : vertices) {
                    Set<ExecContextData.TaskVertex> descendants = graph.getDescendants(vertex);
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

    private Set<ExecContextData.TaskVertex> findDescendantsInternal(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, Long taskId) {
        ExecContextData.TaskVertex vertex = graph.vertexSet()
                .stream()
                .filter(o -> taskId.equals(o.taskId))
                .findFirst().orElse(null);
        if (vertex==null) {
            return Set.of();
        }

        Iterator<ExecContextData.TaskVertex> iterator = new BreadthFirstIterator<>(graph, vertex);
        Set<ExecContextData.TaskVertex> descendants = new HashSet<>();

        // Do not add start vertex to result.
        if (iterator.hasNext()) {
            iterator.next();
        }

        iterator.forEachRemaining(descendants::add);
        return descendants;
    }

    public Set<ExecContextData.TaskVertex> findDirectDescendants(ExecContextImpl execContext, Long taskId) {
        try {
            return readOnlyGraphSetOfTaskVertex(execContext, graph -> findDirectDescendantsInternal(graph, taskId));
        }
        catch (Throwable th) {
            log.error("#916.140 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return Set.of();
        }
    }

    private Set<ExecContextData.TaskVertex> findDirectDescendantsInternal(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, Long taskId) {
        ExecContextData.TaskVertex vertex = graph.vertexSet()
                .stream()
                .filter(o -> taskId.equals(o.taskId))
                .findFirst().orElse(null);
        if (vertex==null) {
            return Set.of();
        }

        Set<ExecContextData.TaskVertex> descendants = graph.outgoingEdgesOf(vertex).stream().map(graph::getEdgeTarget).collect(Collectors.toSet());
        return descendants;
    }

    public Set<ExecContextData.TaskVertex> findDirectAncestors(ExecContextImpl execContext, ExecContextData.TaskVertex vertex) {
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

    private Set<ExecContextData.TaskVertex> findDirectAncestorsInternal(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, ExecContextData.TaskVertex vertex) {
        Set<ExecContextData.TaskVertex> ancestors = graph.incomingEdgesOf(vertex).stream().map(graph::getEdgeSource).collect(Collectors.toSet());
        return ancestors;
    }

    public List<ExecContextData.TaskVertex> findAllForAssigning(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {

                log.debug("Start find a task for assigning");
                if (log.isDebugEnabled()) {
                    log.debug("\tcurrent state of tasks:");
                    graph.vertexSet().forEach(o->log.debug("\t\ttask #{}, state {}", o.taskId, o.execState));
                }

                // if this is newly created graph then return only the start vertex of graph
                ExecContextData.TaskVertex startVertex = graph.vertexSet().stream()
                        .filter( v -> v.execState== EnumsApi.TaskExecState.NONE && graph.incomingEdgesOf(v).isEmpty())
                        .findFirst()
                        .orElse(null);

                if (startVertex!=null) {
                    log.debug("\tthere is a task without any ancestors, #{}, state {}", startVertex.taskId, startVertex.execState);
                    return List.of(startVertex);
                }

                log.debug("\tthere isn't any task with state NONE and which doesn't have ancestors");

                // get all non-processed tasks
                Iterator<ExecContextData.TaskVertex> iterator = new BreadthFirstIterator<>(graph, (ExecContextData.TaskVertex)null);
                List<ExecContextData.TaskVertex> vertices = new ArrayList<>();

                iterator.forEachRemaining(v -> {
                    if (v.execState==EnumsApi.TaskExecState.NONE) {
                        // remove all tasks which have non-processed tasks as direct parent
                        if (isParentFullyProcessedWithoutErrors(graph, v)) {
                            vertices.add(v);
                        }
                    }
                });

                if (!vertices.isEmpty()) {
                    if (log.isDebugEnabled()) {
                        log.debug("\tfound tasks for assigning:");
                        vertices.forEach(o->log.debug("\t\ttask #{}, state {}", o.taskId, o.execState));
                    }
                    return vertices;
                }

                log.debug("\tthere isn't any task for assigning, let's check for 'mh.finish' task");

                // this case is about when all tasks in graph is completed and only mh_finish is left
                ExecContextData.TaskVertex endVertex = graph.vertexSet().stream()
                        .filter( v -> v.execState== EnumsApi.TaskExecState.NONE && graph.outgoingEdgesOf(v).isEmpty())
                        .findFirst()
                        .orElse(null);

                if (endVertex!=null) {
                    log.debug("\tfound task which doesn't have any descendant, #{}, state {}", endVertex.taskId, endVertex.execState);

                    boolean allDone = graph.incomingEdgesOf(endVertex).stream()
                            .map(graph::getEdgeSource)
                            .peek(o->log.debug("\t\tancestor of task #{} is #{}, state {}", endVertex.taskId, o.taskId, o.execState))
                            .allMatch( v -> v.execState!=EnumsApi.TaskExecState.NONE && v.execState!=EnumsApi.TaskExecState.IN_PROGRESS);

                    log.debug("\talldone: {}", allDone);
                    if (allDone) {
                        return List.of(endVertex);
                    }
                }
                return List.of();
            });
        }
        catch (Throwable th) {
            log.error("#916.160 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return List.of();
        }
    }

    public List<ExecContextData.TaskVertex> findAllBroken(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext,
                    graph -> graph.vertexSet().stream()
                            .filter( v -> v.execState == EnumsApi.TaskExecState.BROKEN || v.execState == EnumsApi.TaskExecState.ERROR )
                            .collect(Collectors.toList()));
        }
        catch (Throwable th) {
            log.error("#915.030 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return List.of();
        }
    }

    private boolean isParentFullyProcessedWithoutErrors(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, ExecContextData.TaskVertex vertex) {
        // todo 2020-03-15 actually, we don't need to get all ancestors, we need only direct.
        //  So it can be done just with edges
        for (ExecContextData.TaskVertex ancestor : graph.getAncestors(vertex)) {
            if (ancestor.execState!=EnumsApi.TaskExecState.OK) {
                return false;
            }
        }
        return true;
    }

    public List<ExecContextData.TaskVertex> findAll(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {
                List<ExecContextData.TaskVertex> vertices = new ArrayList<>(graph.vertexSet());
                return vertices;
            });
        }
        catch (Throwable th) {
            log.error("#915.030 Error", th);
            // TODO 2020.03.09 need to implement better handling of Throwable
            return List.of();
        }
    }

    @Nullable
    public ExecContextData.TaskVertex findVertex(ExecContextImpl execContext, Long taskId) {
        try {
            return readOnlyGraphTaskVertex(execContext, graph -> {
                ExecContextData.TaskVertex vertex = graph.vertexSet()
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

    private void setStateForAllChildrenTasksInternal(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph,
            Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {

        Set<ExecContextData.TaskVertex> set = findDescendantsInternal(graph, taskId);
        // find and filter a 'mh.finish' vertex, which doesn't have any outgoing edges
        set.stream().filter(tv -> !graph.outgoingEdgesOf(tv).isEmpty()).forEach( tv-> tv.execState = state);
        withTaskList.childrenTasks.addAll(set);
    }

    public OperationStatusRest addNewTasksToGraph(ExecContextImpl execContext, List<Long> parentTaskIds, List<Long> taskIds) {
        try {
            changeGraph(execContext, graph -> {
                List<ExecContextData.TaskVertex> vertices = graph.vertexSet()
                        .stream()
                        .filter(o -> parentTaskIds.contains(o.taskId))
                        .collect(Collectors.toList());;

                taskIds.forEach(taskId -> {
                    final ExecContextData.TaskVertex v = new ExecContextData.TaskVertex(taskId);
                    graph.addVertex(v);
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
    public Void createEdges(ExecContextImpl execContext, List<Long> lastIds, Set<ExecContextData.TaskVertex> descendants) {
        changeGraph(execContext, graph ->
                graph.vertexSet().stream()
                        .filter(o -> lastIds.contains(o.taskId))
                        .forEach(parentV-> descendants.forEach(trgV -> graph.addEdge(parentV, trgV)))
        );
        return null;
    }
}
