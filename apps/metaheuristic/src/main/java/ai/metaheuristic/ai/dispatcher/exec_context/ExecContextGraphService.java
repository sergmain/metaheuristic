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
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.io.*;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
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
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
class ExecContextGraphService {

    private static final String TASK_EXEC_STATE_ATTR = "task_exec_state";

    private final @NonNull ExecContextCache execContextCache;

    private void changeGraph(@NonNull ExecContextImpl execContext, @NonNull Consumer<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>> callable) throws ImportException {
        ExecContextParamsYaml wpy = execContext.getExecContextParamsYaml();

        GraphImporter<ExecContextData.TaskVertex, DefaultEdge> importer = buildImporter();

        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        importer.importGraph(graph, new StringReader(wpy.graph));

        try {
            callable.accept(graph);
        } finally {
            ComponentNameProvider<ExecContextData.TaskVertex> vertexIdProvider = v -> v.taskId.toString();
            ComponentAttributeProvider<ExecContextData.TaskVertex> vertexAttributeProvider = v -> {
                Map<String, Attribute> m = new HashMap<>();
                m.put(TASK_EXEC_STATE_ATTR, DefaultAttribute.createAttribute(v.execState.toString()));
                return m;
            };

            DOTExporter<ExecContextData.TaskVertex, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider, null, null, vertexAttributeProvider, null);

            Writer writer = new StringWriter();
            exporter.exportGraph(graph, writer);
            wpy.graph = Objects.requireNonNull(writer.toString());
            execContext.updateParams(wpy);
            execContextCache.save(execContext);
        }
    }

    private List<ExecContextData.TaskVertex> readOnlyGraphListOfTaskVertex(
            @NonNull ExecContextImpl execContext,
            @NonNull Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, @lombok.NonNull List<ExecContextData.TaskVertex>> callable) throws ImportException {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        return callable.apply(graph);
    }

    private @NonNull Set<ExecContextData.TaskVertex> readOnlyGraphSetOfTaskVertex(
            @NonNull ExecContextImpl execContext,
            @NonNull Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, Set<ExecContextData.TaskVertex>> callable) throws ImportException {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        Set<ExecContextData.TaskVertex> apply = callable.apply(graph);
        return apply!=null ? apply : Set.of();
    }

    private @Nullable ExecContextData.TaskVertex readOnlyGraphTaskVertex(
            @NonNull ExecContextImpl execContext,
            @NonNull Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, ExecContextData.TaskVertex> callable) throws ImportException {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        return callable.apply(graph);
    }

    private long readOnlyGraphLong(@NonNull ExecContextImpl execContext, @NonNull Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, @lombok.NonNull Long> callable) throws ImportException {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContext);
        return callable.apply(graph);
    }

    private @NonNull DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> prepareGraph(@NonNull ExecContextImpl execContext) throws ImportException {
        ExecContextParamsYaml wpy = execContext.getExecContextParamsYaml();
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        if (S.b(wpy.graph)) {
            return graph;
        }
        GraphImporter<ExecContextData.TaskVertex, DefaultEdge> importer = buildImporter();
        importer.importGraph(graph, new StringReader(wpy.graph));
        return graph;
    }

    private static ExecContextData.TaskVertex toTaskVertex(String id, Map<String, Attribute> attributes) {
        ExecContextData.TaskVertex v = new ExecContextData.TaskVertex();
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

    private static final EdgeProvider<ExecContextData.TaskVertex, DefaultEdge> ep = (f, t, l, attrs) -> new DefaultEdge();

    private static GraphImporter<ExecContextData.TaskVertex, DefaultEdge> buildImporter() {
        //noinspection UnnecessaryLocalVariable
        DOTImporter<ExecContextData.TaskVertex, DefaultEdge> importer = new DOTImporter<>(ExecContextGraphService::toTaskVertex, ep);
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

                Set<ExecContextData.TaskVertex> set = findDescendantsInternal(graph, taskId);
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

    public @NonNull List<ExecContextData.TaskVertex> findLeafs(@NonNull ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {

                try {
                    //noinspection UnnecessaryLocalVariable
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

    public List<ExecContextData.TaskVertex> findAllForAssigning(ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {

                // if this is newly created graph then return only start vertex of graph
                ExecContextData.TaskVertex startVertex = graph.vertexSet().stream()
                        .filter( v -> v.execState== EnumsApi.TaskExecState.NONE && graph.incomingEdgesOf(v).isEmpty())
                        .findFirst()
                        .orElse(null);

                if (startVertex!=null) {
                    return List.of(startVertex);
                }

                // get all non-processed tasks
                Iterator<ExecContextData.TaskVertex> iterator = new BreadthFirstIterator<>(graph, (ExecContextData.TaskVertex)null);
                List<ExecContextData.TaskVertex> vertices = new ArrayList<>();

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
            // TODO 2020.03.09 need to implement better handling of Throwable
            return List.of();
        }
    }

    public List<ExecContextData.TaskVertex> findAllBroken(ExecContextImpl execContext) {
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

    private boolean isParentFullyProcessedWithoutErrors(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, ExecContextData.TaskVertex vertex) {
        for (ExecContextData.TaskVertex ancestor : graph.getAncestors(vertex)) {
            if (ancestor.execState!=EnumsApi.TaskExecState.OK) {
                return false;
            }
        }
        return true;
    }

    public @NonNull List<ExecContextData.TaskVertex> findAll(@NonNull ExecContextImpl execContext) {
        try {
            return readOnlyGraphListOfTaskVertex(execContext, graph -> {
                //noinspection UnnecessaryLocalVariable
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

    public @Nullable ExecContextData.TaskVertex findVertex(@NonNull ExecContextImpl execContext, @NonNull Long taskId) {
        try {
            return readOnlyGraphTaskVertex(execContext, graph -> {
                //noinspection UnnecessaryLocalVariable
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
        set.forEach( t->{
            t.execState = state;
        });
        withTaskList.childrenTasks.addAll(set);
    }

    public OperationStatusRest addNewTasksToGraph(ExecContextImpl execContext, List<Long> parentTaskIds, List<Long> taskIds) {
        try {
            changeGraph(execContext, graph -> {
                List<ExecContextData.TaskVertex> vertices = graph.vertexSet()
                        .stream()
                        .filter(o -> parentTaskIds.contains(o.taskId))
                        .collect(Collectors.toList());;

                taskIds.forEach(id -> {
                    final ExecContextData.TaskVertex v = new ExecContextData.TaskVertex(id, EnumsApi.TaskExecState.NONE);
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
