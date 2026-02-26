/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextGraphRepository;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
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
import org.jgrapht.alg.util.Pair;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.dot.DOTImporter;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.jgrapht.util.SupplierUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@RequiredArgsConstructor(onConstructor_={@Autowired})
@SuppressWarnings("WeakerAccess")
public class ExecContextGraphService {

    private static final String TASK_CONTEXT_ID_ATTR = "ctxid";

    private final ExecContextGraphCache execContextGraphCache;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;

    public ExecContextGraph save(ExecContextGraph execContextGraph) {
        TxUtils.checkTxExists();
        if (execContextGraph.id!=null) {
            ExecContextGraphSyncService.checkWriteLockPresent(execContextGraph.id);
        }
        final ExecContextGraph ec = execContextGraphCache.save(execContextGraph);
        return ec;
    }

    @SuppressWarnings("unused")
    public void save(ExecContextData.GraphAndStates graphAndStates) {
        TxUtils.checkTxExists();
        if (graphAndStates.graph().id!=null) {
            ExecContextGraphSyncService.checkWriteLockPresent(graphAndStates.graph().id);
        }
        final ExecContextGraph ecg = execContextGraphCache.save(graphAndStates.graph());

        if (graphAndStates.states().id!=null) {
            ExecContextTaskStateSyncService.checkWriteLockPresent(graphAndStates.states().id);
        }
        final ExecContextTaskState ects = execContextTaskStateRepository.save(graphAndStates.states());
    }

    @SuppressWarnings("unused")
    public void saveState(ExecContextTaskState execContextTaskState) {
        TxUtils.checkTxExists();
        final ExecContextTaskState ects = execContextTaskStateRepository.save(execContextTaskState);
    }

    private void changeGraph(ExecContextGraph execContextGraph, Consumer<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>> callable) {
        TxUtils.checkTxExists();
        ExecContextGraphSyncService.checkWriteLockPresent(execContextGraph.id);

        ExecContextGraphParamsYaml ecpy = execContextGraph.getExecContextGraphParamsYaml();
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = importExecContextGraph(ecpy);
        try {
            callable.accept(graph);
        } finally {
            ecpy.graph = asString(graph);
            execContextGraph.setParams(ExecContextParamsYamlUtils.BASE_YAML_UTILS.toString(ecpy));
            save(execContextGraph);
        }
    }

    private void changeGraphWithState(
        ExecContextData.GraphAndStates graphAndStates, BiConsumer<DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge>, ExecContextTaskStateParamsYaml> callable) {

        TxUtils.checkTxExists();
        ExecContextGraphSyncService.checkWriteLockPresent(graphAndStates.graph().id);
        ExecContextTaskStateSyncService.checkWriteLockPresent(graphAndStates.states().id);

        ExecContextGraphParamsYaml ecgpy = graphAndStates.graph().getExecContextGraphParamsYaml();
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = importExecContextGraph(ecgpy);
        ExecContextTaskStateParamsYaml ectspy = graphAndStates.states().getExecContextTaskStateParamsYaml();
        try {
            callable.accept(graph, ectspy);
        } finally {
            ecgpy.graph = asString(graph);
            graphAndStates.graph().updateParams(ecgpy);
            graphAndStates.states().updateParams(ectspy);
            save(graphAndStates);
        }
    }

    private void changeState(
        ExecContextData.ExecContextDAC execContextDAC, ExecContextTaskState execContextTaskState,
        BiConsumer<ExecContextData.ExecContextDAC, ExecContextTaskStateParamsYaml> callable) {

        TxUtils.checkTxExists();
        ExecContextTaskStateSyncService.checkWriteLockPresent(execContextTaskState.id);
        if (execContextDAC.execContextId()!=null && execContextTaskState.execContextId!=null && !Objects.equals(execContextDAC.execContextId(), execContextTaskState.execContextId)) {
            throw new IllegalStateException(
                    "(execContextGraph.execContextId!=null && execContextTaskState.execContextId!=null && " +
                            "!Objects.equals(execContextGraph.execContextId, execContextTaskState.execContextId))");
        }

        ExecContextTaskStateParamsYaml ectspy = execContextTaskState.getExecContextTaskStateParamsYaml();
        try {
            callable.accept(execContextDAC, ectspy);
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

    private static DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> prepareGraph(ExecContextGraph execContextGraph) {
        return importExecContextGraph(execContextGraph.getExecContextGraphParamsYaml());
    }

    public static DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> importExecContextGraph(ExecContextGraphParamsYaml wpy) {
        return importExecContextGraph(wpy.graph);
    }

    private static final DOTImporter<ExecContextData.TaskVertex, DefaultEdge> DOT_IMPORTER = new DOTImporter<>();
    static {
        // https://stackoverflow.com/questions/60461351/import-graph-with-1-4-0
        DOT_IMPORTER.setVertexFactory(ExecContextGraphService::getTaskVertexFactory);
        DOT_IMPORTER.addVertexAttributeConsumer(ExecContextGraphService::getVertexAttributeConsumer);
    }

    @SneakyThrows
    public static DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> importExecContextGraph(String graphAsString) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = new DirectedAcyclicGraph<>(
                ExecContextData.TaskVertex::new, SupplierUtil.DEFAULT_EDGE_SUPPLIER, false);

        // https://stackoverflow.com/questions/60461351/import-graph-with-1-4-0
//        DOTImporter<ExecContextData.TaskVertex, DefaultEdge> importer = new DOTImporter<>();
//        importer.setVertexFactory(ExecContextGraphService::getTaskVertexFactory);
//        importer.addVertexAttributeConsumer((ExecContextGraphService::getVertexAttributeConsumer));

        DOT_IMPORTER.importGraph(graph, new StringReader(graphAsString));
        return graph;
    }

    private static void getVertexAttributeConsumer(Pair<ExecContextData.TaskVertex, String> vertex, Attribute attribute) {
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
    }

    private static ExecContextData.TaskVertex getTaskVertexFactory(String id) {
        return new ExecContextData.TaskVertex(Long.parseLong(id));
    }

    /**
     * !!! This method doesn't return the id of current Task and its new status. Must be changed by an outside code.
     */
    public ExecContextOperationStatusWithTaskList updateTaskExecState(ExecContextData.ExecContextDAC execContextDAC, Long execContextTaskStateId, List<TaskData.TaskWithStateAndTaskContextId> taskWithStates) {
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);
        final ExecContextOperationStatusWithTaskList status = updateTaskExecState(execContextDAC, execContextTaskState, taskWithStates);
        execContextTaskStateRepository.save(execContextTaskState);
        return status;
    }

    private ExecContextOperationStatusWithTaskList updateTaskExecState(ExecContextData.ExecContextDAC execContextDAC, ExecContextTaskState execContextTaskState, List<TaskData.TaskWithStateAndTaskContextId> taskWithStates) {
        final ExecContextOperationStatusWithTaskList status = new ExecContextOperationStatusWithTaskList();
        status.status = OperationStatusRest.OPERATION_STATUS_OK;

        changeState(execContextDAC, execContextTaskState, (graph, stateParamsYaml) -> {
            for (TaskData.TaskWithStateAndTaskContextId taskWithState : taskWithStates) {
                Long taskId = taskWithState.taskId;
                EnumsApi.TaskExecState execState = taskWithState.state;

                ExecContextData.TaskVertex tv = graph.graph().vertexSet()
                    .stream()
                    .filter(o -> o.taskId.equals(taskId))
                    .findFirst()
                    .orElse(null);

                // Don't combine with stream, a side-effect could be occurred
                if (tv!=null) {
                    stateParamsYaml.states.put(tv.taskId, execState);
                    if (execState==EnumsApi.TaskExecState.ERROR) {
                        setStateForAllChildrenTasksInternal(graph, stateParamsYaml, taskId, status, EnumsApi.TaskExecState.SKIPPED, taskWithState.taskContextId);
                    }
                    else if (execState==EnumsApi.TaskExecState.NONE || execState==EnumsApi.TaskExecState.OK) {
                        // do nothing
                    }
                    else if (execState == EnumsApi.TaskExecState.SKIPPED) {
                        // When a task is SKIPPED (e.g. condition-gated mh.nop with false condition),
                        // propagate SKIPPED to all children tasks in the same context branch.
                        // This prevents sub-process tasks from being stuck in NONE/PRE_INIT state.
                        setStateForAllChildrenTasksInternal(graph, stateParamsYaml, taskId, status, EnumsApi.TaskExecState.SKIPPED, taskWithState.taskContextId);
                    }
                    else if (execState == EnumsApi.TaskExecState.CHECK_CACHE) {
                        log.info("915.017 TaskExecState for task #{} is CHECK_CACHE", tv.taskId);
                        // todo 2020-11-01 need to decide what to do here
                    }
                    else if (execState == EnumsApi.TaskExecState.IN_PROGRESS) {
                        // do nothing
                    }
                    else if (execState == EnumsApi.TaskExecState.ERROR_WITH_RECOVERY) {
                        // todo 2022-02-17 need to decide what to do here
                    }
                }
            }
        });
        status.status = OperationStatusRest.OPERATION_STATUS_OK;
        return status;
    }

    public List<TaskData.TaskWithState> getAllTasksTopologically(Long execContextGraphId, Long execContextTaskStateId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);

        return readOnlyGraphWithState(execContextGraph, execContextTaskState, (graph, stateParamsYaml) -> {
            TopologicalOrderIterator<ExecContextData.TaskVertex, DefaultEdge> iterator = new TopologicalOrderIterator<>(graph);

            List<TaskData.TaskWithState> tasks = new ArrayList<>();
            iterator.forEachRemaining(o-> {
                EnumsApi.TaskExecState state = execContextTaskState.getExecContextTaskStateParamsYaml().states.getOrDefault(o.taskId, EnumsApi.TaskExecState.NONE);
                tasks.add(new TaskData.TaskWithState(o.taskId, state));
            });
            return tasks;
        });
    }

    public ExecContextOperationStatusWithTaskList updateTaskStatesWithResettingAllChildrenTasks(

        ExecContextData.ExecContextDAC execContextDAC, Long execContextTaskStateId, Long taskId) {
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);
        final ExecContextOperationStatusWithTaskList withTaskList = new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);

        changeState(execContextDAC, execContextTaskState, (graph, stateParamsYaml) -> {

            Set<ExecContextData.TaskVertex> set = findDescendantsInternal(graph.graph(), taskId);
            set.stream()
                    .peek( t-> stateParamsYaml.states.put(t.taskId, EnumsApi.TaskExecState.NONE))
                    .map(o->new TaskData.TaskWithState(taskId, EnumsApi.TaskExecState.NONE))
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
                log.error("915.019 error", th);
                throw new RuntimeException("Error", th);
            }
        });
    }

    public Set<ExecContextData.TaskVertex> findDescendants(Long execContextId, Long execContextGraphId, Long taskId) {
        ExecContextData.ExecContextDAC execContextDAC = getExecContextDAC(execContextId, execContextGraphId);
        return findDescendants(execContextDAC, taskId);
    }

    private static Set<ExecContextData.TaskVertex> findDescendants(ExecContextData.ExecContextDAC execContextDAC, Long taskId) {
        final Set<ExecContextData.TaskVertex> descendantsInternal = findDescendantsInternal(execContextDAC.graph(), taskId);
        return descendantsInternal;
    }

    public Set<TaskData.TaskWithState> findDescendantsWithState(Long execContextGraphId, Long execContextTaskStateId, Long taskId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);
        return findDescendantsWithState(execContextGraph, execContextTaskState, taskId);
    }

    private static Set<TaskData.TaskWithState> findDescendantsWithState(ExecContextGraph execContextGraph, ExecContextTaskState execContextTaskState, Long taskId) {
        return readOnlyGraph(execContextGraph, graph -> {
            final Set<ExecContextData.TaskVertex> descendantsInternal = findDescendantsInternal(graph, taskId);
            Set<TaskData.TaskWithState> set = descendantsInternal.stream()
                    .map(o-> new TaskData.TaskWithState(o.taskId, execContextTaskState.getExecContextTaskStateParamsYaml().states.getOrDefault(o.taskId, EnumsApi.TaskExecState.NONE)))
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
        return findDirectDescendants(execContextGraph, taskId);
    }

    public static Set<ExecContextData.TaskVertex> findDirectDescendants(ExecContextGraph execContextGraph, Long taskId) {
        return readOnlyGraph(execContextGraph, graph -> {
            ExecContextData.TaskVertex vertex = graph.vertexSet()
                .stream()
                .filter(o -> taskId.equals(o.taskId))
                .findFirst().orElse(null);
            if (vertex == null) {
                return Set.of();
            }

            Set<ExecContextData.TaskVertex> descendants = graph.outgoingEdgesOf(vertex).stream().map(graph::getEdgeTarget).collect(Collectors.toSet());
            return descendants;
        });
    }

    public static Set<ExecContextData.TaskVertex> findAncestors(ExecContextGraph execContextGraph, ExecContextData.TaskVertex vertex) {
        return readOnlyGraph(execContextGraph, graph -> graph.getAncestors(vertex));
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

    public static List<ExecContextData.TaskVertex> findAllForAssigning(ExecContextGraph execContextGraph, ExecContextTaskState execContextTaskState, boolean includeForCaching) {
        return readOnlyGraphWithState(execContextGraph, execContextTaskState, (graph,stateParamsYaml) -> {

            log.debug("Start searching a task for assigning");
            if (log.isDebugEnabled()) {
                log.debug("\tcurrent state of tasks:");
                graph.vertexSet().forEach(o->log.debug("\t\ttask #{}, state {}", o.taskId, stateParamsYaml.states.getOrDefault(o.taskId, EnumsApi.TaskExecState.NONE)));
            }

            ExecContextData.TaskVertex startVertex = graph.vertexSet().stream()
                    .filter( v -> graph.incomingEdgesOf(v).isEmpty())
                    .findFirst()
                    .orElse(null);

            // if this is newly created graph then return only the start vertex of graph
            if (startVertex!=null) {
                EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(startVertex.taskId, EnumsApi.TaskExecState.NONE);
                boolean found = includeForCaching ?
                        (state == EnumsApi.TaskExecState.NONE || state == EnumsApi.TaskExecState.CHECK_CACHE) :
                        state == EnumsApi.TaskExecState.NONE;

                if (found) {
                    if (log.isDebugEnabled()) {
                        log.debug("\tThe root vertex of graph wasn't processed, #{}, state {}",
                                startVertex.taskId, stateParamsYaml.states.getOrDefault(startVertex.taskId, EnumsApi.TaskExecState.NONE));
                    }
                    return List.of(startVertex);
                }
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
                                EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(o.taskId, EnumsApi.TaskExecState.NONE);
                                log.debug("\t\tancestor of task #{} is #{}, state {}", endVertex.taskId, o.taskId, state);
                            }
                        })
                        .allMatch( v -> {
                            EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(v.taskId, EnumsApi.TaskExecState.NONE);
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
        return readOnlyGraph(execContextGraph, ExecContextGraphService::findAllRootVertices);
    }

    public static List<ExecContextData.TaskVertex> findAllRootVertices(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph) {
        return graph.vertexSet().stream()
            .filter(v -> graph.incomingEdgesOf(v).isEmpty())
            .collect(Collectors.toList());
    }

    public boolean verifyGraph(Long execContextGraphId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        return readOnlyGraph(execContextGraph, ExecContextGraphService::verifyGraph);
    }

    public static boolean verifyGraph(DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph) {
        return ExecContextGraphService.findAllRootVertices(graph).size()<2;
    }

    private static boolean isParentFullyProcessed(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph, ExecContextTaskState execContextTaskState, ExecContextData.TaskVertex vertex) {

        // we don't need to get all ancestors, we need only direct.
        // So it can be done just with edges
        ExecContextTaskStateParamsYaml stateParamsYaml = execContextTaskState.getExecContextTaskStateParamsYaml();
        for (ExecContextData.TaskVertex ancestor : graph.getAncestors(vertex)) {
            EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(ancestor.taskId, EnumsApi.TaskExecState.NONE);
            if (!EnumsApi.TaskExecState.isFinishedState(state)) {
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

    /**
     *
     * @return Map - key - taskContextId, value - ExecContextData.TaskWithState
     */
    public Map<String, List<TaskData.TaskWithState>> findVerticesByTaskContextIds(Long execContextGraphId, Long execContextTaskStateId, Collection<String> taskContextIds) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);

        return readOnlyGraphWithState(execContextGraph, execContextTaskState, (graph, stateParamsYaml) -> {
            Map<String, List<TaskData.TaskWithState>> vertices = new HashMap<>();
            for (ExecContextData.TaskVertex v : graph.vertexSet()) {
                if (!taskContextIds.contains(v.taskContextId)) {
                    continue;
                }
                EnumsApi.TaskExecState state = stateParamsYaml.states.getOrDefault(v.taskId, EnumsApi.TaskExecState.NONE);
                vertices.computeIfAbsent(v.taskContextId, (o)->new ArrayList<>()).add( new TaskData.TaskWithState(v.taskId, state));
            }
            return vertices;
        });
    }

    @SuppressWarnings("ReturnOfNull")
    public static ExecContextData.@Nullable TaskVertex findVertexByTaskId(ExecContextGraph execContextGraph, Long taskId) {
        return readOnlyGraphNullable(execContextGraph, (graph) -> {
            for (ExecContextData.TaskVertex v : graph.vertexSet()) {
                if (taskId.equals(v.taskId)) {
                    return v;
                }
            }
            return null;
        });
    }

    public void setStateForAllChildrenTasks(ExecContextData.ExecContextDAC execContextDAC, Long execContextTaskStateId, Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);
        changeState(execContextDAC, execContextTaskState,
                (graph, stateParamsYaml) -> setStateForAllChildrenTasksInternal(graph, stateParamsYaml, taskId, withTaskList, state));
    }

    @SuppressWarnings("SameParameterValue")
    private static void setStateForAllChildrenTasksInternal(
        ExecContextData.ExecContextDAC execContextDAC, ExecContextTaskStateParamsYaml stateParamsYaml,
        Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {

        setStateForAllChildrenTasksInternal(execContextDAC, stateParamsYaml, taskId, withTaskList, state, null);
    }

    private static void setStateForAllChildrenTasksInternal(
        ExecContextData.ExecContextDAC execContextDAC, ExecContextTaskStateParamsYaml stateParamsYaml,
            Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state, @Nullable String taskContextId) {

        Set<ExecContextData.TaskVertex> set = findDescendantsInternal(execContextDAC.graph(), taskId);

        // Only mark descendants whose ALL parents are in an error-or-skipped state.
        // This prevents marking a task SKIPPED if it has another parent that is still active (OK, IN_PROGRESS, NONE).
        Set<ExecContextData.TaskVertex> toMark = new LinkedHashSet<>();
        for (ExecContextData.TaskVertex tv : set) {
            if (allParentsErrorOrSkipped(execContextDAC.graph(), stateParamsYaml, tv)) {
                toMark.add(tv);
            }
        }

        toMark.stream()
                .peek( t-> stateParamsYaml.states.put(t.taskId, state))
                .map(o->new TaskData.TaskWithState(o.taskId, state))
                .collect(Collectors.toCollection(()->withTaskList.childrenTasks));

        // Cascade SKIPPED to tasks that are not descendants of taskId but whose ALL parents
        // are now in ERROR or SKIPPED state. This handles the case where siblings in a sequential
        // chain become unreachable because all their predecessors were SKIPPED.
        // No context filter is applied — the allParentsErrorOrSkipped check is the proper guard
        // and handles cross-context cascading (e.g. inner subProcess ERROR propagating to outer sequential tasks).
        propagateSkippedToUnreachableTasks(execContextDAC, stateParamsYaml, withTaskList, state);

        //noinspection unused
        int i=1;
    }

    /**
     * After the initial SKIPPED marking, iterate through the entire graph to find tasks
     * that are not yet SKIPPED but whose ALL parents are in ERROR or SKIPPED state.
     * Mark them SKIPPED and repeat until no more tasks qualify (fixed-point cascade).
     */
    private static void propagateSkippedToUnreachableTasks(
            ExecContextData.ExecContextDAC execContextDAC, ExecContextTaskStateParamsYaml stateParamsYaml,
            ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state) {

        boolean changed = true;
        while (changed) {
            changed = false;
            for (ExecContextData.TaskVertex tv : execContextDAC.graph().vertexSet()) {
                EnumsApi.TaskExecState currentState = stateParamsYaml.states.getOrDefault(tv.taskId, EnumsApi.TaskExecState.NONE);
                if (currentState == EnumsApi.TaskExecState.SKIPPED || currentState == EnumsApi.TaskExecState.ERROR || currentState == EnumsApi.TaskExecState.OK) {
                    // already in terminal state
                    continue;
                }
                if (execContextDAC.graph().incomingEdgesOf(tv).isEmpty()) {
                    // root vertex — no parents to check
                    continue;
                }
                if (allParentsErrorOrSkipped(execContextDAC.graph(), stateParamsYaml, tv)) {
                    stateParamsYaml.states.put(tv.taskId, state);
                    withTaskList.childrenTasks.add(new TaskData.TaskWithState(tv.taskId, state));
                    changed = true;
                }
            }
        }
    }

    /**
     * Check if ALL parents (direct ancestors) of a task vertex are in ERROR or SKIPPED state.
     */
    private static boolean allParentsErrorOrSkipped(
            DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph,
            ExecContextTaskStateParamsYaml stateParamsYaml,
            ExecContextData.TaskVertex tv) {

        Set<DefaultEdge> incoming = graph.incomingEdgesOf(tv);
        if (incoming.isEmpty()) {
            return false;
        }
        for (DefaultEdge edge : incoming) {
            ExecContextData.TaskVertex parent = graph.getEdgeSource(edge);
            EnumsApi.TaskExecState parentState = stateParamsYaml.states.getOrDefault(parent.taskId, EnumsApi.TaskExecState.NONE);
            if (parentState != EnumsApi.TaskExecState.ERROR && parentState != EnumsApi.TaskExecState.SKIPPED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Public static entry point for unit tests that operate directly on the graph and state objects
     * without requiring Spring context or DB access.
     */
    public static void setStateForAllChildrenTasksStatic(
        ExecContextData.ExecContextDAC execContextDAC, ExecContextTaskStateParamsYaml stateParamsYaml,
        Long taskId, ExecContextOperationStatusWithTaskList withTaskList, EnumsApi.TaskExecState state, @Nullable String taskContextId) {

        setStateForAllChildrenTasksInternal(execContextDAC, stateParamsYaml, taskId, withTaskList, state, taskContextId);
    }

    @SuppressWarnings("SimplifyStreamApiCallChains")
    public OperationStatusRest addNewTasksToGraph(
        ExecContextData.GraphAndStates graphAndStates, List<Long> parentTaskIds,
            List<TaskApiData.TaskWithContext> taskIds, EnumsApi.TaskExecState state) {
        TxUtils.checkTxExists();

        changeGraphWithState(graphAndStates, (graph, stateParamsYaml) -> {
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

    public void createEdges(ExecContextGraph execContextGraph, List<Long> lastIds, Set<ExecContextData.TaskVertex> descendants) {
        TxUtils.checkTxExists();
        changeGraph(execContextGraph, graph ->
                graph.vertexSet().stream()
                        .filter(o -> lastIds.contains(o.taskId))
                        .forEach(parentV-> descendants.forEach(trgV -> graph.addEdge(parentV, trgV)))
        );
    }

    public ExecContextGraph prepareExecContextGraph(Long execContextGraphId) {
        ExecContextGraph execContextGraph = execContextGraphCache.findById(execContextGraphId);
        if (execContextGraph==null) {
            throw new IllegalStateException("(execContextGraph==null)");
        }
        return execContextGraph;
    }

    public ExecContextTaskState prepareExecContextTaskState(Long execContextTaskStateId) {
        ExecContextTaskState execContextTaskState = execContextTaskStateRepository.findById(execContextTaskStateId).orElse(null);
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

    public ExecContextData.GraphAndStates prepareGraphAndStates(Long execContextGraphId, Long execContextTaskStateId) {
        ExecContextGraph graphAndStates = prepareExecContextGraph(execContextGraphId);
        ExecContextTaskState execContextTaskState = prepareExecContextTaskState(execContextTaskStateId);
        return new ExecContextData.GraphAndStates(graphAndStates, execContextTaskState);
    }

    public ExecContextData.ExecContextDAC getExecContextDAC(Long execContextId, Long execContextGraphId) {
        ExecContextGraph execContextGraph = prepareExecContextGraph(execContextGraphId);
        return getExecContextDAC(execContextId, execContextGraph);
    }

    public static ExecContextData.ExecContextDAC getExecContextDAC(Long execContextId, ExecContextGraph execContextGraph) {
        DirectedAcyclicGraph<ExecContextData.TaskVertex, DefaultEdge> graph = importExecContextGraph(execContextGraph.getExecContextGraphParamsYaml());
        ExecContextData.ExecContextDAC execContextDAC = new ExecContextData.ExecContextDAC(execContextId, graph, execContextGraph.version);
        return execContextDAC;
    }
}
