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

package ai.metaheuristic.ai.mh.dispatcher..exec_context;

import ai.metaheuristic.ai.mh.dispatcher..beans.ExecContextImpl;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.RequiredArgsConstructor;
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
@Profile("mh.dispatcher.")
@Slf4j
@RequiredArgsConstructor
class ExecContextGraphService {

    private static final String TASK_EXEC_STATE_ATTR = "task_exec_state";

    private final ExecContextCache execContextCache;

    private void changeGraph(ExecContextImpl execContext, Consumer<DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge>> callable) throws ImportException {
        ExecContextParamsYaml wpy = execContext.getExecContextParamsYaml();

        GraphImporter<ExecContextParamsYaml.TaskVertex, DefaultEdge> importer = buildImporter();

        DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        importer.importGraph(graph, new StringReader(wpy.graph));

        try {
            callable.accept(graph);
        } finally {
            ComponentNameProvider<ExecContextParamsYaml.TaskVertex> vertexIdProvider = v -> v.taskId.toString();
            ComponentAttributeProvider<ExecContextParamsYaml.TaskVertex> vertexAttributeProvider = v -> {
                Map<String, Attribute> m = new HashMap<>();
                if (v.execState != null) {
                    m.put(TASK_EXEC_STATE_ATTR, DefaultAttribute.createAttribute(v.execState.toString()));
                }
                return m;
            };

            DOTExporter<ExecContextParamsYaml.TaskVertex, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider, null, null, vertexAttributeProvider, null);

            Writer writer = new StringWriter();
            exporter.exportGraph(graph, writer);
            wpy.graph = writer.toString();
            execContext.updateParams(wpy);
            execContextCache.save(execContext);
        }
    }

    private List<ExecContextParamsYaml.TaskVertex> readOnlyGraphListOfTaskVertex(
            ExecContextImpl execContext,
            Function<DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge>, List<ExecContextParamsYaml.TaskVertex>> callable) throws ImportException {
        DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        return graph != null ? callable.apply(graph) : null;
    }

    private Set<ExecContextParamsYaml.TaskVertex> readOnlyGraphSetOfTaskVertex(
            ExecContextImpl execContext, Function<DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge>, Set<ExecContextParamsYaml.TaskVertex>> callable) throws ImportException {
        DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        return graph != null ? callable.apply(graph) : null;
    }

    private ExecContextParamsYaml.TaskVertex readOnlyGraphTaskVertex(
            ExecContextImpl execContext,
            Function<DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge>, ExecContextParamsYaml.TaskVertex> callable) throws ImportException {
        DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        return graph != null ? callable.apply(graph) : null;
    }

    private long readOnlyGraphLong(ExecContextImpl execContext, Function<DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge>, Long> callable) throws ImportException {
        DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        return graph != null ? callable.apply(graph) : 0;
    }

    private DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge> prepareGraph(ExecContextImpl execContext) throws ImportException {
        ExecContextParamsYaml wpy = execContext.getExecContextParamsYaml();
        DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        if (wpy.graph==null || wpy.graph.isBlank()) {
            return graph;
        }
        GraphImporter<ExecContextParamsYaml.TaskVertex, DefaultEdge> importer = buildImporter();
        importer.importGraph(graph, new StringReader(wpy.graph));
        return graph;
    }

    private static ExecContextParamsYaml.TaskVertex toTaskVertex(String id, Map<String, Attribute> attributes) {
        ExecContextParamsYaml.TaskVertex v = new ExecContextParamsYaml.TaskVertex();
        v.taskId = Long.valueOf(id);
        if (attributes==null) {
            return v;
        }

        final Attribute execState = attributes.get(TASK_EXEC_STATE_ATTR);
        if (execState!=null) {
            v.execState = EnumsApi.TaskExecState.valueOf(execState.getValue());
        }
        return v;
    }

    private static final EdgeProvider<ExecContextParamsYaml.TaskVertex, DefaultEdge> ep = (f, t, l, attrs) -> new DefaultEdge();

    private static GraphImporter<ExecContextParamsYaml.TaskVertex, DefaultEdge> buildImporter() {
        //noinspection UnnecessaryLocalVariable
        DOTImporter<ExecContextParamsYaml.TaskVertex, DefaultEdge> importer = new DOTImporter<>(ExecContextGraphService::toTaskVertex, ep);
        return importer;
    }

    public ExecContextOperationStatusWithTaskList updateTaskExecStates(ExecContextImpl execContext, ConcurrentHashMap<Long, Integer> taskStates) {
        final ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList();
        status.status = OperationStatusRest.OPERATION_STATUS_OK;
        if (taskStates==null || taskStates.isEmpty()) {
            return status;
        }
        try {
            changeGraph(execContext, graph -> {
                List<ExecContextParamsYaml.TaskVertex> tvs = graph.vertexSet()
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
            log.error("Error updaing graph", th);
            status.status = new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage());
        }
        return status;
    }

    public ExecContextOperationStatusWithTaskList updateTaskExecState(ExecContextImpl execContext, Long taskId, int execState) {
        final ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList();
        try {
            changeGraph(execContext, graph -> {
                ExecContextParamsYaml.TaskVertex tv = graph.vertexSet()
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

    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(ExecContextImpl execContext, Long taskId) {
        try {
            final ExecContextOperationStatusWithTaskList withTaskList = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
            changeGraph(execContext, graph -> {

                Set<ExecContextParamsYaml.TaskVertex> set = findDescendantsInternal(graph, taskId);
                set.forEach( t->{
                    t.execState = EnumsApi.TaskExecState.NONE;
                });
                withTaskList.childrenTasks.addAll(set);
            });
            return withTaskList;
        }
        catch (Throwable th) {
            return new ExecContextOperationStatusWithTaskList(new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage()), List.of());
        }
    }

    public List<ExecContextParamsYaml.TaskVertex> findLeafs(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {

                try {
                    //noinspection UnnecessaryLocalVariable
                    List<ExecContextParamsYaml.TaskVertex> vertices = graph.vertexSet()
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
            return null;
        }
    }

    public Set<ExecContextParamsYaml.TaskVertex> findDescendants(ExecContextImpl execContext, Long taskId) {
        try {
            return readOnlyGraphSetOfTaskVertex(execContext, graph -> findDescendantsInternal(graph, taskId));
        }
        catch (Throwable th) {
            log.error("#915.022 Error", th);
            return null;
        }
    }

    private Set<ExecContextParamsYaml.TaskVertex> findDescendantsInternal(DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge> graph, Long taskId) {
        ExecContextParamsYaml.TaskVertex vertex = graph.vertexSet()
                .stream()
                .filter(o -> taskId.equals(o.taskId))
                .findFirst().orElse(null);
        if (vertex==null) {
            return Set.of();
        }

        Iterator<ExecContextParamsYaml.TaskVertex> iterator = new BreadthFirstIterator<>(graph, vertex);
        Set<ExecContextParamsYaml.TaskVertex> descendants = new HashSet<>();

        // Do not add start vertex to result.
        if (iterator.hasNext()) {
            iterator.next();
        }

        iterator.forEachRemaining(descendants::add);
        return descendants;
    }

    public List<ExecContextParamsYaml.TaskVertex> findAllForAssigning(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {

                // if this is newly created graph then return only start vertex of graph
                ExecContextParamsYaml.TaskVertex startVertex = graph.vertexSet().stream()
                        .filter( v -> v.execState== EnumsApi.TaskExecState.NONE && graph.incomingEdgesOf(v).isEmpty())
                        .findFirst()
                        .orElse(null);

                if (startVertex!=null) {
                    return List.of(startVertex);
                }

                // get all non-processed tasks
                Iterator<ExecContextParamsYaml.TaskVertex> iterator = new BreadthFirstIterator<>(graph, (ExecContextParamsYaml.TaskVertex)null);
                List<ExecContextParamsYaml.TaskVertex> vertices = new ArrayList<>();

                iterator.forEachRemaining(v -> {
                    if (v.execState==EnumsApi.TaskExecState.NONE) {
                        // remove all tasks which have non-processed as direct parent
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
            return null;
        }
    }

    public List<ExecContextParamsYaml.TaskVertex> findAllBroken(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {
                return graph.vertexSet().stream()
                        .filter( v -> v.execState == EnumsApi.TaskExecState.BROKEN || v.execState == EnumsApi.TaskExecState.ERROR )
                        .collect(Collectors.toList());
            });
        }
        catch (Throwable th) {
            log.error("#915.030 Error", th);
            return null;
        }
    }

    private boolean isParentFullyProcessedWithoutErrors(DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge> graph, ExecContextParamsYaml.TaskVertex vertex) {
        for (ExecContextParamsYaml.TaskVertex ancestor : graph.getAncestors(vertex)) {
            if (ancestor.execState!=EnumsApi.TaskExecState.OK) {
                return false;
            }
        }
        return true;
    }

    public List<ExecContextParamsYaml.TaskVertex> findAll(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {
                //noinspection UnnecessaryLocalVariable
                List<ExecContextParamsYaml.TaskVertex> vertices = new ArrayList<>(graph.vertexSet());
                return vertices;
            });
        }
        catch (Throwable th) {
            log.error("#915.030 Error", th);
            return null;
        }
    }

    public ExecContextParamsYaml.TaskVertex findVertex(ExecContextImpl execContext, Long taskId) {
        try {
            return readOnlyGraphTaskVertex(execContext, graph -> {
                //noinspection UnnecessaryLocalVariable
                ExecContextParamsYaml.TaskVertex vertex = graph.vertexSet()
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
            DirectedAcyclicGraph<ExecContextParamsYaml.TaskVertex, DefaultEdge> graph,
            Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {

        Set<ExecContextParamsYaml.TaskVertex> set = findDescendantsInternal(graph, taskId);
        set.forEach( t->{
            t.execState = state;
        });
        withTaskList.childrenTasks.addAll(set);
    }

    public OperationStatusRest addNewTasksToGraph(ExecContextImpl execContext, List<Long> parentTaskIds, List<Long> taskIds) {
        try {
            changeGraph(execContext, graph -> {
                List<ExecContextParamsYaml.TaskVertex> vertices = graph.vertexSet()
                        .stream()
                        .filter(o -> parentTaskIds.contains(o.taskId))
                        .collect(Collectors.toList());;

                taskIds.forEach(id -> {
                    final ExecContextParamsYaml.TaskVertex v = new ExecContextParamsYaml.TaskVertex(id, EnumsApi.TaskExecState.NONE);
                    graph.addVertex(v);
                    vertices.forEach(parentV -> graph.addEdge(parentV, v) );
                });
            });
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable th) {
            log.error("Erorr while adding task to graph", th);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage());
        }
    }
}
