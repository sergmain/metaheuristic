/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.workbook;

import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
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
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 7/6/2019
 * Time: 10:42 PM
 */
@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
class WorkbookGraphService {

    public static final String EMPTY_GRAPH = "strict digraph G { }";
    public static final String TASK_EXEC_STATE_ATTR = "task_exec_state";

    private final WorkbookCache workbookCache;

    @FunctionalInterface
    public interface WorkWithGraphVoid {
        void execute(DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph);
    }

    @FunctionalInterface
    public interface WorkWithGraphLong {
        long execute(DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph);
    }

    @FunctionalInterface
    public interface WorkWithGraphListOfTaskVertex {
        List<WorkbookParamsYaml.TaskVertex> execute(DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph);
    }

    @FunctionalInterface
    public interface WorkWithGraphSetOfTaskVertex {
        Set<WorkbookParamsYaml.TaskVertex> execute(DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph);
    }

    @FunctionalInterface
    public interface WorkWithGraphTaskVertex {
        WorkbookParamsYaml.TaskVertex execute(DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph);
    }

    public void changeGraph(WorkbookImpl workbook, WorkWithGraphVoid callable) throws ImportException {
        WorkbookParamsYaml wpy = workbook.getWorkbookParamsYaml();

        GraphImporter<WorkbookParamsYaml.TaskVertex, DefaultEdge> importer = buildImporter();

        DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        importer.importGraph(graph, new StringReader(wpy.graph));

        try {
            callable.execute(graph);
        } finally {
            ComponentNameProvider<WorkbookParamsYaml.TaskVertex> vertexIdProvider = v -> v.taskId.toString();
            ComponentAttributeProvider<WorkbookParamsYaml.TaskVertex> vertexAttributeProvider = v -> {
                Map<String, Attribute> m = new HashMap<>();
                if (v.execState != null) {
                    m.put(TASK_EXEC_STATE_ATTR, DefaultAttribute.createAttribute(v.execState.toString()));
                }
                return m;
            };

            DOTExporter<WorkbookParamsYaml.TaskVertex, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider, null, null, vertexAttributeProvider, null);

            Writer writer = new StringWriter();
            exporter.exportGraph(graph, writer);
            wpy.graph = writer.toString();
            workbook.updateParams(wpy);
            workbookCache.save(workbook);
        }
    }

    public List<WorkbookParamsYaml.TaskVertex> readOnlyGraphListOfTaskVertex(WorkbookImpl workbook, WorkWithGraphListOfTaskVertex callable) throws ImportException {
        DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph = prepareGraph(workbook);
        return callable.execute(graph);
    }

    public Set<WorkbookParamsYaml.TaskVertex> readOnlyGraphSetOfTaskVertex(WorkbookImpl workbook, WorkWithGraphSetOfTaskVertex callable) throws ImportException {
        DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph = prepareGraph(workbook);
        return callable.execute(graph);
    }

    public WorkbookParamsYaml.TaskVertex readOnlyGraphTaskVertex(WorkbookImpl workbook, WorkWithGraphTaskVertex callable) throws ImportException {
        DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph = prepareGraph(workbook);
        return callable.execute(graph);
    }

    public long readOnlyGraphLong(WorkbookImpl workbook, WorkWithGraphLong callable) throws ImportException {
        DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph = prepareGraph(workbook);
        return callable.execute(graph);
    }

    private DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> prepareGraph(WorkbookImpl workbook) throws ImportException {
        WorkbookParamsYaml wpy = workbook.getWorkbookParamsYaml();
        GraphImporter<WorkbookParamsYaml.TaskVertex, DefaultEdge> importer = buildImporter();
        DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);
        importer.importGraph(graph, new StringReader(wpy.graph));
        return graph;
    }

    public void readOnlyGraphVoid(WorkbookImpl workbook, WorkWithGraphVoid callable) throws ImportException {
        DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph = prepareGraph(workbook);
        callable.execute(graph);
    }

    private static WorkbookParamsYaml.TaskVertex toTaskVertex(String id, Map<String, Attribute> attributes) {
        WorkbookParamsYaml.TaskVertex v = new WorkbookParamsYaml.TaskVertex();
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

    private static final EdgeProvider<WorkbookParamsYaml.TaskVertex, DefaultEdge> ep = (f, t, l, attrs) -> new DefaultEdge();

    private GraphImporter<WorkbookParamsYaml.TaskVertex, DefaultEdge> buildImporter() {
        //noinspection UnnecessaryLocalVariable
        DOTImporter<WorkbookParamsYaml.TaskVertex, DefaultEdge> importer = new DOTImporter<>(WorkbookGraphService::toTaskVertex, ep);
        return importer;
    }

    public WorkbookOperationStatusWithTaskList updateTaskExecStates(WorkbookImpl workbook, ConcurrentHashMap<Long, Integer> taskStates) {
        final WorkbookOperationStatusWithTaskList status = new WorkbookOperationStatusWithTaskList();
        status.status = OperationStatusRest.OPERATION_STATUS_OK;
        if (taskStates==null || taskStates.isEmpty()) {
            return status;
        }
        try {
            changeGraph(workbook, graph -> {
                List<WorkbookParamsYaml.TaskVertex> tvs = graph.vertexSet()
                        .stream()
                        .filter(o -> taskStates.containsKey(o.taskId))
                        .collect(Collectors.toList());

                // Don't combine streams, a side-effect could be occurred
                tvs.forEach(t -> {
                    t.execState = EnumsApi.TaskExecState.from(taskStates.get(t.taskId));
                    if (t.execState == EnumsApi.TaskExecState.ERROR || t.execState == EnumsApi.TaskExecState.BROKEN) {
                        setStateForAllChildrenTasksInternal(graph, t.taskId, new WorkbookOperationStatusWithTaskList(), EnumsApi.TaskExecState.BROKEN);
                    }
                    else if (t.execState == EnumsApi.TaskExecState.OK) {
                        setStateForAllChildrenTasksInternal(graph, t.taskId, new WorkbookOperationStatusWithTaskList(), EnumsApi.TaskExecState.NONE);
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

    public WorkbookOperationStatusWithTaskList updateTaskExecState(WorkbookImpl workbook, Long taskId, int execState) {
        final WorkbookOperationStatusWithTaskList status = new WorkbookOperationStatusWithTaskList();
        try {
            changeGraph(workbook, graph -> {
                WorkbookParamsYaml.TaskVertex tv = graph.vertexSet()
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

    public long getCountUnfinishedTasks(WorkbookImpl workbook) {
        try {
            return readOnlyGraphLong(workbook, graph -> graph
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

    public WorkbookOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(WorkbookImpl workbook, Long taskId) {
        try {
            final WorkbookOperationStatusWithTaskList withTaskList = new WorkbookOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
            changeGraph(workbook, graph -> {

                Set<WorkbookParamsYaml.TaskVertex> set = findDescendantsInternal(graph, taskId);
                set.forEach( t->{
                    t.execState = EnumsApi.TaskExecState.NONE;
                });
                withTaskList.childrenTasks.addAll(set);
            });
            return withTaskList;
        }
        catch (Throwable th) {
            return new WorkbookOperationStatusWithTaskList(new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage()), List.of());
        }
    }

    public List<WorkbookParamsYaml.TaskVertex> findLeafs(WorkbookImpl workbook) {
        try {
            return readOnlyGraphListOfTaskVertex(workbook, graph -> {

                //noinspection UnnecessaryLocalVariable
                List<WorkbookParamsYaml.TaskVertex> vertices = graph.vertexSet()
                        .stream()
                        .filter(o -> graph.outDegreeOf(o)==0)
                        .collect(Collectors.toList());
                return vertices;
            });
        }
        catch (Throwable th) {
            log.error("#915.020 Error", th);
            return null;
        }
    }

    public Set<WorkbookParamsYaml.TaskVertex> findDescendants(WorkbookImpl workbook, Long taskId) {
        try {
            return readOnlyGraphSetOfTaskVertex(workbook, graph -> findDescendantsInternal(graph, taskId));
        }
        catch (Throwable th) {
            log.error("#915.022 Error", th);
            return null;
        }
    }

    private Set<WorkbookParamsYaml.TaskVertex> findDescendantsInternal(DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph, Long taskId) {
        WorkbookParamsYaml.TaskVertex vertex = graph.vertexSet()
                .stream()
                .filter(o -> taskId.equals(o.taskId))
                .findFirst().orElse(null);
        if (vertex==null) {
            return Set.of();
        }

        Iterator<WorkbookParamsYaml.TaskVertex> iterator = new BreadthFirstIterator<>(graph, vertex);
        Set<WorkbookParamsYaml.TaskVertex> descendants = new HashSet<>();

        // Do not add start vertex to result.
        if (iterator.hasNext()) {
            iterator.next();
        }

        iterator.forEachRemaining(descendants::add);
        return descendants;
    }

    public List<WorkbookParamsYaml.TaskVertex> findAllWithDirectOrderAndStatusNone(WorkbookImpl workbook) {
        try {
            return readOnlyGraphListOfTaskVertex(workbook, graph -> {
                Iterator<WorkbookParamsYaml.TaskVertex> iterator = new BreadthFirstIterator<>(graph, (WorkbookParamsYaml.TaskVertex)null);
                List<WorkbookParamsYaml.TaskVertex> vertices = new ArrayList<>();

                iterator.forEachRemaining(e -> {
                    if (e.execState==EnumsApi.TaskExecState.NONE) {
                        vertices.add(e);
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

    public List<WorkbookParamsYaml.TaskVertex> findAll(WorkbookImpl workbook) {
        try {
            return readOnlyGraphListOfTaskVertex(workbook, graph -> {
                //noinspection UnnecessaryLocalVariable
                List<WorkbookParamsYaml.TaskVertex> vertices = new ArrayList<>(graph.vertexSet());
                return vertices;
            });
        }
        catch (Throwable th) {
            log.error("#915.030 Error", th);
            return null;
        }
    }

    public WorkbookParamsYaml.TaskVertex findVertex(WorkbookImpl workbook, Long taskId) {
        try {
            return readOnlyGraphTaskVertex(workbook, graph -> {
                //noinspection UnnecessaryLocalVariable
                WorkbookParamsYaml.TaskVertex vertex = graph.vertexSet()
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

    public WorkbookOperationStatusWithTaskList updateGraphWithSettingAllChildrenTasksAsBroken(WorkbookImpl workbook, Long taskId) {
        try {
            final WorkbookOperationStatusWithTaskList withTaskList = new WorkbookOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
            changeGraph(workbook, graph -> setStateForAllChildrenTasksInternal(graph, taskId, withTaskList, EnumsApi.TaskExecState.BROKEN));
            return withTaskList;
        }
        catch (Throwable th) {
            log.error("#915.050 Error", th);
            return new WorkbookOperationStatusWithTaskList(new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage()), List.of());
        }
    }

    private void setStateForAllChildrenTasksInternal(
            DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph,
            Long taskId, WorkbookOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {

        Set<WorkbookParamsYaml.TaskVertex> set = findDescendantsInternal(graph, taskId);
        set.forEach( t->{
            t.execState = state;
        });
        withTaskList.childrenTasks.addAll(set);
    }

    public OperationStatusRest addNewTasksToGraph(WorkbookImpl workbook, List<Long> parentTaskIds, List<Long> taskIds) {
        try {
            changeGraph(workbook, graph -> {
                List<WorkbookParamsYaml.TaskVertex> vertices = graph.vertexSet()
                        .stream()
                        .filter(o -> parentTaskIds.contains(o.taskId))
                        .collect(Collectors.toList());;

                taskIds.forEach(id -> {
                    final WorkbookParamsYaml.TaskVertex v = new WorkbookParamsYaml.TaskVertex(id, EnumsApi.TaskExecState.NONE);
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
