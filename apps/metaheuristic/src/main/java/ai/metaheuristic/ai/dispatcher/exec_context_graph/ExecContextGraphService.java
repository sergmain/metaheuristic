/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.exec_context_graph;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskApiData;
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
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.jgrapht.util.SupplierUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
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
public class ExecContextGraphService {

    private static final String TASK_ID_STR_ATTR = "tids";
    private static final String TASK_EXEC_STATE_ATTR = "state";
    private static final String TASK_CONTEXT_ID_ATTR = "ctxid";

    private final ExecContextGraphCache execContextGraphCache;
    private final ExecContextSyncService execContextSyncService;
    private final EntityManager em;

    public ExecContextGraph save(ExecContextGraph execContextGraph) {
        TxUtils.checkTxExists();
        if (execContextGraph.id!=null) {
            execContextSyncService.checkWriteLockPresent(execContextGraph.id);
        }
        if (execContextGraph.id==null) {
            final ExecContextGraph ec = execContextGraphCache.save(execContextGraph);
            return ec;
        }
        else if (!em.contains(execContextGraph) ) {
//            https://stackoverflow.com/questions/13135309/how-to-find-out-whether-an-entity-is-detached-in-jpa-hibernate
            throw new IllegalStateException(S.f("#705.020 Bean %s isn't managed by EntityManager", execContextGraph));
        }
        return execContextGraph;
    }

    private void changeGraph(ExecContextGraph execContextGraph, Consumer<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>> callable) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContextGraph.execContextId);

        ExecContextGraphParamsYaml ecpy = execContextGraph.getExecContextGraphParamsYaml();
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = importProcessGraph(ecpy);
        try {
            callable.accept(graph);
        } finally {
            ecpy.graph = asString(graph);
            execContextGraph.setParams(ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(ecpy));
            save(execContextGraph);
        }
    }

    public static String asString(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph) {
        Function<ExecContextData.TaskVertex, String> vertexIdProvider = v -> v.taskId.toString();
        Function<ExecContextData.TaskVertex, Map<String, Attribute>> vertexAttributeProvider = v -> {
            Map<String, Attribute> m = new HashMap<>();
            m.put(TASK_ID_STR_ATTR, DefaultAttribute.createAttribute(v.taskIdStr));
            m.put(TASK_EXEC_STATE_ATTR, DefaultAttribute.createAttribute(v.execState.toString()));
            m.put(TASK_CONTEXT_ID_ATTR, DefaultAttribute.createAttribute(v.taskContextId));
            return m;
        };

        DOTExporter<ExecContextData.TaskVertex, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider);
        exporter.setVertexAttributeProvider(vertexAttributeProvider);

        StringWriter writer = new StringWriter();
        exporter.exportGraph(graph, writer);
        return writer.toString();
    }

    private Set<ExecContextData.TaskVertex> readOnlyGraphSetOfTaskVertex(
            ExecContextGraph execContextGraph,
            Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, Set<ExecContextData.TaskVertex>> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContextGraph);
        Set<ExecContextData.TaskVertex> apply = callable.apply(graph);
        return apply!=null ? apply : Set.of();
    }

    @Nullable
    private <T> T readOnlyGraphNullable(
            ExecContextGraph execContextGraph,
            Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, T> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContextGraph);
        return callable.apply(graph);
    }

    private <T> T readOnlyGraph(
            ExecContextGraph execContextGraph,
            Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, T> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContextGraph);
        return callable.apply(graph);
    }

    private long readOnlyGraphLong(ExecContextGraph execContextGraph, Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, Long> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContextGraph);
        return callable.apply(graph);
    }

    private DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> prepareGraph(ExecContextGraph execContextGraph) {
        return importProcessGraph(execContextGraph.getExecContextGraphParamsYaml());
    }

    @SneakyThrows
    public static DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> importProcessGraph(ExecContextGraphParamsYaml wpy) {
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
                case TASK_CONTEXT_ID_ATTR:
                    vertex.getFirst().taskContextId = attribute.getValue();
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

    /**
     * !!! This method doesn't return the current Id of Task and its new status. Must be changed by outside code.
     */
    @SuppressWarnings("StatementWithEmptyBody")
    public ExecContextOperationStatusWithTaskList updateTaskExecState(@Nullable ExecContextGraph execContextGraph, Long taskId, EnumsApi.TaskExecState execState, @Nullable String taskContextId) {
        final ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList();
        status.status = OperationStatusRest.OPERATION_STATUS_OK;
        if (execContextGraph == null) {
            return status;
        }

        changeGraph(execContextGraph, graph -> {
            ExecContextData.TaskVertex tv = graph.vertexSet()
                    .stream()
                    .filter(o -> o.taskId.equals(taskId))
                    .findFirst()
                    .orElse(null);

            // Don't combine with stream, a side-effect could be occurred
            if (tv!=null) {
                tv.execState = execState;
                if (tv.execState==EnumsApi.TaskExecState.ERROR) {
                    setStateForAllChildrenTasksInternal(graph, taskId, status, EnumsApi.TaskExecState.SKIPPED, taskContextId);
                }
                else if (tv.execState==EnumsApi.TaskExecState.NONE || tv.execState==EnumsApi.TaskExecState.OK) {
                    // do nothing
                }
                else if (tv.execState == EnumsApi.TaskExecState.SKIPPED) {
                    log.info("#915.015 TaskExecState for task #{} is SKIPPED", tv.taskId);
                    // todo 2020-08-16 need to decide what to do here
                }
                else if (tv.execState == EnumsApi.TaskExecState.CHECK_CACHE) {
                    log.info("#915.017 TaskExecState for task #{} is CHECK_CACHE", tv.taskId);
                    // todo 2020-11-01 need to decide what to do here
                }
                else if (tv.execState==EnumsApi.TaskExecState.IN_PROGRESS) {
                    // do nothing
                }
            }
        });
        status.status = OperationStatusRest.OPERATION_STATUS_OK;
        return status;
    }

    public long getCountUnfinishedTasks(ExecContextGraph execContextGraph) {
        return readOnlyGraphLong(execContextGraph, graph -> graph
                .vertexSet()
                .stream()
                .filter(o -> o.execState==EnumsApi.TaskExecState.NONE || o.execState==EnumsApi.TaskExecState.IN_PROGRESS || o.execState==EnumsApi.TaskExecState.CHECK_CACHE)
                .count());
    }

    public List<ExecContextData.TaskVertex> getUnfinishedTaskVertices(ExecContextGraph execContextGraph) {
        return readOnlyGraph(execContextGraph, graph -> graph
                .vertexSet()
                .stream()
                .filter(o -> o.execState==EnumsApi.TaskExecState.NONE || o.execState==EnumsApi.TaskExecState.IN_PROGRESS || o.execState==EnumsApi.TaskExecState.CHECK_CACHE)
                .collect(Collectors.toList()));
    }

    public List<ExecContextData.TaskVertex> getAllTasksTopologically(ExecContextGraph execContextGraph) {
        return readOnlyGraph(execContextGraph, graph -> {
            TopologicalOrderIterator<ExecContextData.TaskVertex, DefaultEdge> iterator = new TopologicalOrderIterator<>(graph);

            List<ExecContextData.TaskVertex> tasks = new ArrayList<>();
            iterator.forEachRemaining(tasks::add);
            return tasks;
        });
    }

    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(@Nullable ExecContextGraph execContextGraph, Long taskId) {
        final ExecContextOperationStatusWithTaskList withTaskList = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
        if (execContextGraph==null) {
            return withTaskList;
        }
        changeGraph(execContextGraph, graph -> {

            Set<ExecContextData.TaskVertex> set = findDescendantsInternal(graph, taskId);
            set.forEach( t-> t.execState = EnumsApi.TaskExecState.NONE);
            withTaskList.childrenTasks.addAll(set);
        });
        return withTaskList;
    }

    public List<ExecContextData.TaskVertex> findLeafs(ExecContextGraph execContextGraph) {
        return readOnlyGraph(execContextGraph, graph -> {

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

    public Set<ExecContextData.TaskVertex> findDescendants(ExecContextGraph execContextGraph, Long taskId) {
        return readOnlyGraphSetOfTaskVertex(execContextGraph, graph -> findDescendantsInternal(graph, taskId));
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
        Set<ExecContextData.TaskVertex> descendants = new LinkedHashSet<>();

        // Do not add start vertex to result.
        if (iterator.hasNext()) {
            iterator.next();
        }

        iterator.forEachRemaining(descendants::add);
        return descendants;
    }

    public Set<ExecContextData.TaskVertex> findDirectDescendants(ExecContextGraph execContextGraph, Long taskId) {
        return readOnlyGraphSetOfTaskVertex(execContextGraph, graph -> findDirectDescendantsInternal(graph, taskId));
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

    public Set<ExecContextData.TaskVertex> findDirectAncestors(ExecContextGraph execContextGraph, ExecContextData.TaskVertex vertex) {
        return readOnlyGraphSetOfTaskVertex(execContextGraph, graph -> findDirectAncestorsInternal(graph, vertex));
    }

    private Set<ExecContextData.TaskVertex> findDirectAncestorsInternal(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, ExecContextData.TaskVertex vertex) {
        Set<ExecContextData.TaskVertex> ancestors = graph.incomingEdgesOf(vertex).stream().map(graph::getEdgeSource).collect(Collectors.toSet());
        return ancestors;
    }

    public List<ExecContextData.TaskVertex> findAllForAssigning(ExecContextGraph execContextGraph, boolean includeForCaching) {
        return readOnlyGraph(execContextGraph, graph -> {

            log.debug("Start find a task for assigning");
            if (log.isDebugEnabled()) {
                log.debug("\tcurrent state of tasks:");
                graph.vertexSet().forEach(o->log.debug("\t\ttask #{}, state {}", o.taskId, o.execState));
            }

            ExecContextData.TaskVertex startVertex = graph.vertexSet().stream()
                    .filter( v -> {
                        if (!graph.incomingEdgesOf(v).isEmpty()) {
                            return false;
                        }
                        if (includeForCaching) {
                            return (v.execState == EnumsApi.TaskExecState.NONE || v.execState == EnumsApi.TaskExecState.CHECK_CACHE);
                        }
                        else {
                            return v.execState == EnumsApi.TaskExecState.NONE;
                        }
                    })
                    .findFirst()
                    .orElse(null);

            // if this is newly created graph then return only the start vertex of graph
            if (startVertex!=null) {
                log.debug("\tThe root vertex of graph wasn't processed, #{}, state {}", startVertex.taskId, startVertex.execState);
                return List.of(startVertex);
            }

            log.debug("\tThe root vertex of execContextGraph was already processes");

            // get all non-processed tasks
            Iterator<ExecContextData.TaskVertex> iterator = new BreadthFirstIterator<>(graph, (ExecContextData.TaskVertex)null);
            List<ExecContextData.TaskVertex> vertices = new ArrayList<>();

            iterator.forEachRemaining(v -> {
                if (includeForCaching) {
                    if (v.execState == EnumsApi.TaskExecState.NONE || v.execState == EnumsApi.TaskExecState.CHECK_CACHE) {
                        // remove all tasks which have non-processed tasks as a direct parent
                        if (isParentFullyProcessed(graph, v)) {
                            vertices.add(v);
                        }
                    }
                }
                else {
                    if (v.execState == EnumsApi.TaskExecState.NONE) {
                        // remove all tasks which have non-processed tasks as a direct parent
                        if (isParentFullyProcessed(graph, v)) {
                            vertices.add(v);
                        }
                    }
                }
            });

            if (!vertices.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("\tfound tasks for assigning:");
                    StringBuilder sb = new StringBuilder();
                    vertices.forEach(o->sb.append(S.f("#%s: %s, ", o.taskId, o.execState)));
                    log.debug("\t\t" + sb.toString());
                }
                return vertices;
            }

            log.debug("\tthere isn't any task for assigning, let's check for 'mh.finish' task");

            // this case is about when all tasks in graph is completed and only mh_finish is left
            ExecContextData.TaskVertex endVertex = graph.vertexSet().stream()
                    .filter( v -> {
                        if (!graph.outgoingEdgesOf(v).isEmpty()) {
                            return false;
                        }
                        if (includeForCaching) {
                            return (v.execState == EnumsApi.TaskExecState.NONE || v.execState == EnumsApi.TaskExecState.CHECK_CACHE);
                        }
                        else {
                            return v.execState == EnumsApi.TaskExecState.NONE;
                        }
                    })
                    .findFirst()
                    .orElse(null);

            if (endVertex!=null) {
                log.debug("\tfound task which doesn't have any descendant, #{}, state {}", endVertex.taskId, endVertex.execState);

                boolean allDone = graph.incomingEdgesOf(endVertex).stream()
                        .map(graph::getEdgeSource)
                        .peek(o->log.debug("\t\tancestor of task #{} is #{}, state {}", endVertex.taskId, o.taskId, o.execState))
                        .allMatch( v -> v.execState!=EnumsApi.TaskExecState.NONE && v.execState!=EnumsApi.TaskExecState.IN_PROGRESS
                                && v.execState!=EnumsApi.TaskExecState.CHECK_CACHE);

                log.debug("\tall done: {}", allDone);
                if (allDone) {
                    return List.of(endVertex);
                }
            }
            return List.of();
        });
    }

    public List<ExecContextData.TaskVertex> findAllRootVertices(ExecContextGraph execContextGraph) {
        return readOnlyGraph(execContextGraph,
                graph -> graph.vertexSet().stream()
                        .filter( v -> graph.incomingEdgesOf(v).isEmpty() )
                        .collect(Collectors.toList()));
    }

    private boolean isParentFullyProcessed(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, ExecContextData.TaskVertex vertex) {
        // we don't need to get all ancestors, we need only direct.
        // So it can be done just with edges
        for (ExecContextData.TaskVertex ancestor : graph.getAncestors(vertex)) {
            if (ancestor.execState==EnumsApi.TaskExecState.NONE || ancestor.execState==EnumsApi.TaskExecState.IN_PROGRESS || ancestor.execState==EnumsApi.TaskExecState.CHECK_CACHE) {
                return false;
            }
        }
        return true;
    }

    public List<ExecContextData.TaskVertex> findAll(ExecContextGraph execContextGraph) {
        return readOnlyGraph(execContextGraph, graph -> {
            List<ExecContextData.TaskVertex> vertices = new ArrayList<>(graph.vertexSet());
            return vertices;
        });
    }

    @Nullable
    public ExecContextData.TaskVertex findVertex(ExecContextGraph execContextGraph, Long taskId) {
        return readOnlyGraphNullable(execContextGraph, graph -> {
            ExecContextData.TaskVertex vertex = graph.vertexSet()
                    .stream()
                    .filter(o -> o.taskId.equals(taskId))
                    .findFirst()
                    .orElse(null);
            return vertex;
        });
    }

    @SneakyThrows
    public Map<String, List<ExecContextData.TaskVertex>> findVerticesByTaskContextIds(ExecContextGraph execContextGraph, Collection<String> taskContextIds) {
        return readOnlyGraph(execContextGraph, graph -> {
            Map<String, List<ExecContextData.TaskVertex>> vertices = new HashMap<>();
            for (ExecContextData.TaskVertex v : graph.vertexSet()) {
                if (!taskContextIds.contains(v.taskContextId)) {
                    continue;
                }
                vertices.computeIfAbsent(v.taskContextId, (o)->new ArrayList<>()).add(v);
            }
            return vertices;
        });
    }

    public void setStateForAllChildrenTasks(ExecContextGraph execContextGraph, Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {
        changeGraph(execContextGraph, graph -> {
            setStateForAllChildrenTasksInternal(graph, taskId, withTaskList, state);
        });
    }

    @SuppressWarnings("SameParameterValue")
    private void setStateForAllChildrenTasksInternal(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph,
            Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {
        setStateForAllChildrenTasksInternal(graph, taskId, withTaskList, state, null);
    }

    private void setStateForAllChildrenTasksInternal(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph,
            Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state, @Nullable String taskContextId) {

        Set<ExecContextData.TaskVertex> set = findDescendantsInternal(graph, taskId);
        String context = taskContextId!=null ? ContextUtils.getWithoutSubContext(taskContextId) : null;

        // find and filter a 'mh.finish' vertex, which doesn't have any outgoing edges
        //noinspection SimplifiableConditionalExpression
        Set<ExecContextData.TaskVertex> setFiltered = set.stream()
                .filter(tv -> !graph.outgoingEdgesOf(tv).isEmpty() && (context==null ? true : ContextUtils.getWithoutSubContext(tv.taskContextId).startsWith(context)))
                .filter( tv-> tv.execState!=state)
                .collect(Collectors.toSet());

        setFiltered.forEach( tv-> tv.execState = state);

        withTaskList.childrenTasks.addAll(setFiltered);
    }

    public OperationStatusRest addNewTasksToGraph(ExecContextGraph execContextGraph, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> taskIds, EnumsApi.TaskExecState state) {
        changeGraph(execContextGraph, graph -> {
            List<ExecContextData.TaskVertex> vertices = graph.vertexSet()
                    .stream()
                    .filter(o -> parentTaskIds.contains(o.taskId))
                    .collect(Collectors.toList());

            taskIds.forEach(taskWithContext -> {
                final ExecContextData.TaskVertex v = new ExecContextData.TaskVertex(taskWithContext.taskId, taskWithContext.taskContextId, state);
                graph.addVertex(v);
                vertices.forEach(parentV -> graph.addEdge(parentV, v) );
            });
        });
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Nullable
    public Void createEdges(@Nullable ExecContextGraph execContextGraph, List<Long> lastIds, Set<ExecContextData.TaskVertex> descendants) {
        if (execContextGraph==null) {
            return null;
        }
        changeGraph(execContextGraph, graph ->
                graph.vertexSet().stream()
                        .filter(o -> lastIds.contains(o.taskId))
                        .forEach(parentV-> descendants.forEach(trgV -> graph.addEdge(parentV, trgV)))
        );
        return null;
    }
}
