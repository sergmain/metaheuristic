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
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateCache;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextGraphRepository;
import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.exec_context_graph.ExecContextGraphParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
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
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
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

    private static final String TASK_CONTEXT_ID_ATTR = "ctxid";

    private final ExecContextGraphCache execContextGraphCache;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateCache execContextTaskStateCache;
    private final EntityManager em;

    public ExecContextGraph save(ExecContextGraph execContextGraph) {
        TxUtils.checkTxExists();
        if (execContextGraph.id!=null) {
            ExecContextGraphSyncService.checkWriteLockPresent(execContextGraph.id);
        }
        if (execContextGraph.id==null) {
            final ExecContextGraph ec = execContextGraphCache.save(execContextGraph);
            return ec;
        }
        else if (!em.contains(execContextGraph) ) {
//            https://stackoverflow.com/questions/13135309/how-to-find-out-whether-an-entity-is-detached-in-jpa-hibernate
            throw new IllegalStateException(S.f("#705.020 Bean %s isn't managed by EntityManager", execContextGraph));
        }
        execContextGraphCache.save(execContextGraph);
        return execContextGraph;
    }

    @SuppressWarnings("unused")
    public void save(ExecContextGraph execContextGraph, ExecContextTaskState execContextTaskState) {
        TxUtils.checkTxExists();
        if (execContextGraph.id!=null) {
            ExecContextGraphSyncService.checkWriteLockPresent(execContextGraph.id);
        }
        if (execContextGraph.id==null) {
            final ExecContextGraph ecg = execContextGraphCache.save(execContextGraph);
        }
        else if (!em.contains(execContextGraph) ) {
            throw new IllegalStateException(S.f("#705.023 Bean %s isn't managed by EntityManager", execContextGraph));
        }

        if (execContextTaskState.id!=null) {
            ExecContextTaskStateSyncService.checkWriteLockPresent(execContextTaskState.id);
        }
        if (execContextTaskState.id==null) {
            final ExecContextTaskState ects = execContextTaskStateCache.save(execContextTaskState);
        }
        else if (!em.contains(execContextTaskState) ) {
            throw new IllegalStateException(S.f("#705.025 Bean %s isn't managed by EntityManager", execContextTaskState));
        }
    }

    @SuppressWarnings("unused")
    public void saveState(ExecContextTaskState execContextTaskState) {
        TxUtils.checkTxExists();
        if (execContextTaskState.id==null) {
            final ExecContextTaskState ects = execContextTaskStateCache.save(execContextTaskState);
        }
        else if (!em.contains(execContextTaskState) ) {
            throw new IllegalStateException(S.f("#705.025 Bean %s isn't managed by EntityManager", execContextTaskState));
        }
    }

    private void changeGraph(ExecContextGraph execContextGraph, Consumer<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>> callable) {
        TxUtils.checkTxExists();
        ExecContextGraphSyncService.checkWriteLockPresent(execContextGraph.id);

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

    private void changeGraphWithState(
            ExecContextGraph execContextGraph, ExecContextTaskState execContextTaskState,
            BiConsumer<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, ExecContextTaskStateParamsYaml> callable) {

        TxUtils.checkTxExists();
        ExecContextGraphSyncService.checkWriteLockPresent(execContextGraph.id);
        ExecContextTaskStateSyncService.checkWriteLockPresent(execContextTaskState.id);

        if (!Objects.equals(execContextGraph.execContextId, execContextTaskState.execContextId)) {
            throw new IllegalStateException("(!Objects.equals(execContextGraph.execContextId, execContextTaskState.execContextId))");
        }

        ExecContextGraphParamsYaml ecgpy = execContextGraph.getExecContextGraphParamsYaml();
        ExecContextTaskStateParamsYaml ectspy = execContextTaskState.getExecContextTaskStateParamsYaml();
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = importProcessGraph(ecgpy);
        try {
            callable.accept(graph, ectspy);
        } finally {
            ecgpy.graph = asString(graph);
            execContextGraph.updateParams(ecgpy);
            execContextTaskState.updateParams(ectspy);
            save(execContextGraph, execContextTaskState);
        }
    }

    private void changeState(
            ExecContextGraph execContextGraph, ExecContextTaskState execContextTaskState,
            BiConsumer<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, ExecContextTaskStateParamsYaml> callable) {

        TxUtils.checkTxExists();
        ExecContextTaskStateSyncService.checkWriteLockPresent(execContextTaskState.id);
        if (execContextGraph.execContextId!=null && execContextTaskState.execContextId!=null && !Objects.equals(execContextGraph.execContextId, execContextTaskState.execContextId)) {
            throw new IllegalStateException(
                    "(execContextGraph.execContextId!=null && execContextTaskState.execContextId!=null && " +
                            "!Objects.equals(execContextGraph.execContextId, execContextTaskState.execContextId))");
        }

        ExecContextGraphParamsYaml ecgpy = execContextGraph.getExecContextGraphParamsYaml();
        ExecContextTaskStateParamsYaml ectspy = execContextTaskState.getExecContextTaskStateParamsYaml();
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = importProcessGraph(ecgpy);
        try {
            callable.accept(graph, ectspy);
        } finally {
            execContextTaskState.updateParams(ectspy);
            saveState(execContextTaskState);
        }
    }

    public static String asString(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph) {
        Function<ExecContextData.TaskVertex, String> vertexIdProvider = v -> v.taskId.toString();
        Function<ExecContextData.TaskVertex, Map<String, Attribute>> vertexAttributeProvider = v -> {
            Map<String, Attribute> m = new HashMap<>();
            m.put(TASK_CONTEXT_ID_ATTR, DefaultAttribute.createAttribute(v.taskContextId));
            return m;
        };

        DOTExporter<ExecContextData.TaskVertex, DefaultEdge> exporter = new DOTExporter<>(vertexIdProvider);
        exporter.setVertexAttributeProvider(vertexAttributeProvider);

        StringWriter writer = new StringWriter();
        exporter.exportGraph(graph, writer);
        return writer.toString();
    }

    @Nullable
    private static <T> T readOnlyGraphNullable(
            ExecContextGraph execContextGraph,
            Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, T> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContextGraph);
        return callable.apply(graph);
    }

    private static <T> T readOnlyGraph(
            ExecContextGraph execContextGraph,
            Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, T> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContextGraph);
        return callable.apply(graph);
    }

    private static <T> T readOnlyGraphWithState(
            ExecContextGraph execContextGraph, ExecContextTaskState execContextTaskState,
            BiFunction<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, ExecContextTaskStateParamsYaml, T> callable) {

        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContextGraph);
        ExecContextTaskStateParamsYaml ectspy = execContextTaskState.getExecContextTaskStateParamsYaml();
        return callable.apply(graph, ectspy);
    }

    private static long readOnlyGraphLong(ExecContextGraph execContextGraph, Function<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, Long> callable) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = prepareGraph(execContextGraph);
        return callable.apply(graph);
    }

    private static DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> prepareGraph(ExecContextGraph execContextGraph) {
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
     * !!! This method doesn't return the id of current Task and its new status. Must be changed by an outside code.
     */
    @SuppressWarnings("StatementWithEmptyBody")
    public ExecContextOperationStatusWithTaskList updateTaskExecState(Long execContextGraphId, Long execContextTaskStateId, Long taskId, EnumsApi.TaskExecState execState, String taskContextId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);
        return updateTaskExecState(execContextGraph, execContextTaskState, taskId, execState, taskContextId);
    }

    private ExecContextOperationStatusWithTaskList updateTaskExecState(ExecContextGraph execContextGraph, ExecContextTaskState execContextTaskState, Long taskId, EnumsApi.TaskExecState execState, String taskContextId) {
        final ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList();
        status.status = OperationStatusRest.OPERATION_STATUS_OK;

        changeState(execContextGraph, execContextTaskState, (graph, stateParamsYaml) -> {
            ExecContextData.TaskVertex tv = graph.vertexSet()
                    .stream()
                    .filter(o -> o.taskId.equals(taskId))
                    .findFirst()
                    .orElse(null);

            // Don't combine with stream, a side-effect could be occurred
            if (tv!=null) {
                stateParamsYaml.states.put(tv.taskId, execState);
                if (execState==EnumsApi.TaskExecState.ERROR) {
                    setStateForAllChildrenTasksInternal(graph, stateParamsYaml, taskId, status, EnumsApi.TaskExecState.SKIPPED, taskContextId);
                }
                else if (execState==EnumsApi.TaskExecState.NONE || execState==EnumsApi.TaskExecState.OK) {
                    // do nothing
                }
                else if (execState == EnumsApi.TaskExecState.SKIPPED) {
                    log.info("#915.015 TaskExecState for task #{} is SKIPPED", tv.taskId);
                    // todo 2020-08-16 need to decide what to do here
                }
                else if (execState == EnumsApi.TaskExecState.CHECK_CACHE) {
                    log.info("#915.017 TaskExecState for task #{} is CHECK_CACHE", tv.taskId);
                    // todo 2020-11-01 need to decide what to do here
                }
                else if (execState==EnumsApi.TaskExecState.IN_PROGRESS) {
                    // do nothing
                }
            }
        });
        status.status = OperationStatusRest.OPERATION_STATUS_OK;
        return status;
    }

    public List<ExecContextData.TaskWithState> getAllTasksTopologically(Long execContextGraphId, Long execContextTaskStateId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);

        return readOnlyGraphWithState(execContextGraph, execContextTaskState, (graph, stateParamsYaml) -> {
            TopologicalOrderIterator<ExecContextData.TaskVertex, DefaultEdge> iterator = new TopologicalOrderIterator<>(graph);

            List<ExecContextData.TaskWithState> tasks = new ArrayList<>();
            iterator.forEachRemaining(o-> {
                EnumsApi.TaskExecState state = execContextTaskState.getExecContextTaskStateParamsYaml().states.getOrDefault(o.taskId, EnumsApi.TaskExecState.NONE);
                tasks.add(new ExecContextData.TaskWithState(o.taskId, state));
            });
            return tasks;
        });
    }

    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(
            Long execContextGraphId, Long execContextTaskStateId, Long taskId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);
        final ExecContextOperationStatusWithTaskList withTaskList = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);

        changeState(execContextGraph, execContextTaskState, (graph, stateParamsYaml) -> {

            Set<ExecContextData.TaskVertex> set = findDescendantsInternal(graph, taskId);
            set.stream()
                    .peek( t-> stateParamsYaml.states.put(t.taskId, EnumsApi.TaskExecState.NONE))
                    .map(o->new ExecContextData.TaskWithState(taskId, EnumsApi.TaskExecState.NONE))
                    .collect(Collectors.toCollection(()->withTaskList.childrenTasks));
        });
        return withTaskList;
    }

    public static List<ExecContextData.TaskVertex> findLeafs(ExecContextGraph execContextGraph) {
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

    public Set<ExecContextData.TaskVertex> findDescendants(Long execContextGraphId, Long taskId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        return findDescendants(execContextGraph, taskId);
    }

    private static Set<ExecContextData.TaskVertex> findDescendants(ExecContextGraph execContextGraph, Long taskId) {
        return readOnlyGraph(execContextGraph, graph -> {
            final Set<ExecContextData.TaskVertex> descendantsInternal = findDescendantsInternal(graph, taskId);
            return descendantsInternal;
        });
    }

    public Set<ExecContextData.TaskWithState> findDescendantsWithState(Long execContextGraphId, Long execContextTaskStateId, Long taskId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);
        return findDescendantsWithState(execContextGraph, execContextTaskState, taskId);
    }

    private static Set<ExecContextData.TaskWithState> findDescendantsWithState(ExecContextGraph execContextGraph, ExecContextTaskState execContextTaskState, Long taskId) {
        return readOnlyGraph(execContextGraph, graph -> {
            final Set<ExecContextData.TaskVertex> descendantsInternal = findDescendantsInternal(graph, taskId);
            Set<ExecContextData.TaskWithState> set = descendantsInternal.stream()
                    .map(o-> new ExecContextData.TaskWithState(o.taskId, execContextTaskState.getExecContextTaskStateParamsYaml().states.getOrDefault(o.taskId, EnumsApi.TaskExecState.NONE)))
                    .collect(Collectors.toSet());
            return set;
        });
    }

    private static Set<ExecContextData.TaskVertex> findDescendantsInternal(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, Long taskId) {
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

    public Set<ExecContextData.TaskVertex> findDirectDescendants(Long execContextGraphId, Long taskId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        return readOnlyGraph(execContextGraph, graph -> {
            ExecContextData.TaskVertex vertex = graph.vertexSet()
                    .stream()
                    .filter(o -> taskId.equals(o.taskId))
                    .findFirst().orElse(null);
            if (vertex==null) {
                return Set.of();
            }

            Set<ExecContextData.TaskVertex> descendants = graph.outgoingEdgesOf(vertex).stream().map(graph::getEdgeTarget).collect(Collectors.toSet());
            return descendants;
        });
    }

    public static Set<ExecContextData.TaskVertex> findDirectAncestors(ExecContextGraph execContextGraph, ExecContextData.TaskVertex vertex) {
        return readOnlyGraph(execContextGraph, graph -> findDirectAncestorsInternal(graph, vertex));
    }

    private static Set<ExecContextData.TaskVertex> findDirectAncestorsInternal(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, ExecContextData.TaskVertex vertex) {
        Set<ExecContextData.TaskVertex> ancestors = graph.incomingEdgesOf(vertex).stream().map(graph::getEdgeSource).collect(Collectors.toSet());
        return ancestors;
    }

    public List<ExecContextData.TaskVertex> findAllForAssigning(Long execContextGraphId, Long execContextTaskStateId, boolean includeForCaching) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);
        return findAllForAssigning(execContextGraph, execContextTaskState, includeForCaching);
    }

    private static List<ExecContextData.TaskVertex> findAllForAssigning(ExecContextGraph execContextGraph, ExecContextTaskState execContextTaskState, boolean includeForCaching) {
        return readOnlyGraphWithState(execContextGraph, execContextTaskState, (graph,stateParamsYaml) -> {

            log.debug("Start find a task for assigning");
            if (log.isDebugEnabled()) {
                log.debug("\tcurrent state of tasks:");
                graph.vertexSet().forEach(o->log.debug("\t\ttask #{}, state {}", o.taskId, stateParamsYaml.states.getOrDefault(o.taskId, EnumsApi.TaskExecState.NONE)));
            }

            ExecContextData.TaskVertex startVertex = graph.vertexSet().stream()
                    .filter( v -> {
                        if (!graph.incomingEdgesOf(v).isEmpty()) {
                            return false;
                        }
                        EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(v.taskId, EnumsApi.TaskExecState.NONE);
                        if (includeForCaching) {
                            return (state == EnumsApi.TaskExecState.NONE || state == EnumsApi.TaskExecState.CHECK_CACHE);
                        }
                        else {
                            return state == EnumsApi.TaskExecState.NONE;
                        }
                    })
                    .findFirst()
                    .orElse(null);

            // if this is newly created graph then return only the start vertex of graph
            if (startVertex!=null) {
                if (log.isDebugEnabled()) {
                    log.debug("\tThe root vertex of graph wasn't processed, #{}, state {}",
                            startVertex.taskId, stateParamsYaml.states.getOrDefault(startVertex.taskId, EnumsApi.TaskExecState.NONE));
                }
                return List.of(startVertex);
            }

            log.debug("\tThe root vertex of execContextGraph was already processes");

            // get all non-processed tasks
            Iterator<ExecContextData.TaskVertex> iterator = new BreadthFirstIterator<>(graph, (ExecContextData.TaskVertex)null);
            List<ExecContextData.TaskVertex> vertices = new ArrayList<>();

            iterator.forEachRemaining(v -> {
                EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(v.taskId, EnumsApi.TaskExecState.NONE);
                if (includeForCaching) {
                    if (state == EnumsApi.TaskExecState.NONE || state == EnumsApi.TaskExecState.CHECK_CACHE) {
                        // remove all tasks which have non-processed tasks as a direct parent
                        if (isParentFullyProcessed(graph, execContextTaskState, v)) {
                            vertices.add(v);
                        }
                    }
                }
                else {
                    if (state == EnumsApi.TaskExecState.NONE) {
                        // remove all tasks which have non-processed tasks as a direct parent
                        if (isParentFullyProcessed(graph, execContextTaskState, v)) {
                            vertices.add(v);
                        }
                    }
                }
            });

            if (!vertices.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("\tfound tasks for assigning:");
                    StringBuilder sb = new StringBuilder("\t\t");
                    vertices.forEach(o->sb.append(S.f("#%s: %s, ", o.taskId, stateParamsYaml.states.getOrDefault(o.taskId, EnumsApi.TaskExecState.NONE))));
                    log.debug(sb.toString());
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
                        EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(v.taskId, EnumsApi.TaskExecState.NONE);
                        if (includeForCaching) {
                            return (state == EnumsApi.TaskExecState.NONE || state == EnumsApi.TaskExecState.CHECK_CACHE);
                        }
                        else {
                            return state == EnumsApi.TaskExecState.NONE;
                        }
                    })
                    .findFirst()
                    .orElse(null);

            if (endVertex!=null) {
                if (log.isDebugEnabled()) {
                    EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(endVertex.taskId, EnumsApi.TaskExecState.NONE);
                    log.debug("\tfound task which doesn't have any descendant, #{}, state {}", endVertex.taskId, state);
                }

                boolean allDone = graph.incomingEdgesOf(endVertex).stream()
                        .map(graph::getEdgeSource)
                        .peek(o->{
                            if (log.isDebugEnabled()) {
                                EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(endVertex.taskId, EnumsApi.TaskExecState.NONE);
                                log.debug("\t\tancestor of task #{} is #{}, state {}", endVertex.taskId, o.taskId, state);
                            }
                        })
                        .allMatch( v -> {
                            EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(endVertex.taskId, EnumsApi.TaskExecState.NONE);
                            return state != EnumsApi.TaskExecState.NONE && state != EnumsApi.TaskExecState.IN_PROGRESS
                                    && state != EnumsApi.TaskExecState.CHECK_CACHE;
                        });

                log.debug("\tall done: {}", allDone);
                if (allDone) {
                    return List.of(endVertex);
                }
            }
            return List.of();
        });
    }

    public List<ExecContextData.TaskVertex> findAllRootVertices(Long execContextGraphId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        return readOnlyGraph(execContextGraph,
                graph -> graph.vertexSet().stream()
                        .filter( v -> graph.incomingEdgesOf(v).isEmpty() )
                        .collect(Collectors.toList()));
    }

    private static boolean isParentFullyProcessed(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, ExecContextTaskState execContextTaskState, ExecContextData.TaskVertex vertex) {

        // we don't need to get all ancestors, we need only direct.
        // So it can be done just with edges
        ExecContextTaskStateParamsYaml stateParamsYaml = execContextTaskState.getExecContextTaskStateParamsYaml();
        for (ExecContextData.TaskVertex ancestor : graph.getAncestors(vertex)) {
            EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(ancestor.taskId, EnumsApi.TaskExecState.NONE);
            if (state==EnumsApi.TaskExecState.NONE || state==EnumsApi.TaskExecState.IN_PROGRESS || state==EnumsApi.TaskExecState.CHECK_CACHE) {
                return false;
            }
        }
        return true;
    }

    public List<ExecContextData.TaskVertex> findAll(Long execContextGraphId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        return findAll(execContextGraph);
    }

    private static List<ExecContextData.TaskVertex> findAll(ExecContextGraph execContextGraph) {
        return readOnlyGraph(execContextGraph, graph -> {
            List<ExecContextData.TaskVertex> vertices = new ArrayList<>(graph.vertexSet());
            return vertices;
        });
    }

    public Map<String, List<ExecContextData.TaskWithState>> findVerticesByTaskContextIds(Long execContextGraphId, Long execContextTaskStateId, Collection<String> taskContextIds) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);

        return readOnlyGraphWithState(execContextGraph, execContextTaskState, (graph, stateParamsYaml) -> {
            Map<String, List<ExecContextData.TaskWithState>> vertices = new HashMap<>();
            for (ExecContextData.TaskVertex v : graph.vertexSet()) {
                if (!taskContextIds.contains(v.taskContextId)) {
                    continue;
                }
                EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(v.taskId, EnumsApi.TaskExecState.NONE);
                vertices.computeIfAbsent(v.taskContextId, (o)->new ArrayList<>()).add( new ExecContextData.TaskWithState(v.taskId, state));
            }
            return vertices;
        });
    }

    public void setStateForAllChildrenTasks(Long execContextGraphId, Long execContextTaskStateId, Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);
        changeState(execContextGraph, execContextTaskState, (graph, stateParamsYaml) -> {
            setStateForAllChildrenTasksInternal(graph, stateParamsYaml, taskId, withTaskList, state);
        });
    }

    @SuppressWarnings("SameParameterValue")
    private static void setStateForAllChildrenTasksInternal(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, ExecContextTaskStateParamsYaml stateParamsYaml,
            Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {
        setStateForAllChildrenTasksInternal(graph, stateParamsYaml, taskId, withTaskList, state, null);
    }

    private static void setStateForAllChildrenTasksInternal(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, ExecContextTaskStateParamsYaml stateParamsYaml,
            Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state, @Nullable String taskContextId) {

        Set<ExecContextData.TaskVertex> set = findDescendantsInternal(graph, taskId);
        String context = taskContextId!=null ? ContextUtils.getWithoutSubContext(taskContextId) : null;

        // find and filter a 'mh.finish' vertex, which doesn't have any outgoing edges
        //noinspection SimplifiableConditionalExpression
        Set<ExecContextData.TaskVertex> setFiltered = set.stream()
                .filter(tv -> !graph.outgoingEdgesOf(tv).isEmpty() && (context==null ? true : ContextUtils.getWithoutSubContext(tv.taskContextId).startsWith(context)))
                .collect(Collectors.toSet());

        setFiltered.stream()
                .peek( t-> stateParamsYaml.states.put(t.taskId, state))
                .map(o->new ExecContextData.TaskWithState(o.taskId, state))
                .collect(Collectors.toCollection(()->withTaskList.childrenTasks));

        int i=1;
    }

    public OperationStatusRest addNewTasksToGraph(
            Long execContextGraphId, Long execContextTaskStateId, List<Long> parentTaskIds,
            List<TaskApiData.TaskWithContext> taskIds, EnumsApi.TaskExecState state) {

        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);
        return addNewTasksToGraph(execContextGraph, execContextTaskState, parentTaskIds, taskIds, state);
    }

    private OperationStatusRest addNewTasksToGraph(
            ExecContextGraph execContextGraph, ExecContextTaskState execContextTaskState, List<Long> parentTaskIds,
            List<TaskApiData.TaskWithContext> taskIds, EnumsApi.TaskExecState state) {

        changeGraphWithState(execContextGraph, execContextTaskState, (graph, stateParamsYaml) -> {
            List<ExecContextData.TaskVertex> vertices = graph.vertexSet()
                    .stream()
                    .filter(o -> parentTaskIds.contains(o.taskId))
                    .collect(Collectors.toList());

            taskIds.forEach(taskWithContext -> {
                stateParamsYaml.states.put(taskWithContext.taskId, state);
                final ExecContextData.TaskVertex v = new ExecContextData.TaskVertex(taskWithContext.taskId, taskWithContext.taskContextId );
                graph.addVertex(v);
                vertices.forEach(parentV -> graph.addEdge(parentV, v) );
            });
        });
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Nullable
    public Void createEdges(Long execContextGraphId, List<Long> lastIds, Set<ExecContextData.TaskVertex> descendants) {
        TxUtils.checkTxExists();
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        changeGraph(execContextGraph, graph ->
                graph.vertexSet().stream()
                        .filter(o -> lastIds.contains(o.taskId))
                        .forEach(parentV-> descendants.forEach(trgV -> graph.addEdge(parentV, trgV)))
        );
        return null;
    }

    private ExecContextGraph prepareExecContextGraph(Long execContextGraphId) {
        ExecContextGraph execContextGraph = execContextGraphCache.findById(execContextGraphId);
        if (execContextGraph==null) {
            throw new IllegalStateException("(execContextGraph==null)");
        }
        return execContextGraph;
    }

    private ExecContextTaskState prepareExecContextTaskState(Long execContextTaskStateId) {
        ExecContextTaskState execContextTaskState = execContextTaskStateCache.findById(execContextTaskStateId);
        if (execContextTaskState==null) {
            throw new IllegalStateException("(execContextTaskState==null)");
        }
        return execContextTaskState;
    }

    @Transactional
    public Void deleteOrphanGraphs(List<Long> ids) {
        execContextGraphRepository.deleteAllByIdIn(ids);
        return null;
    }


}
