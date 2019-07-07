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
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.io.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class WorkbookGraphService {

    public static final String EMPTY_GRAPH = "strict digraph G { }";
    public static final String TASK_EXEC_STATE_ATTR = "task_exec_state";

    private final WorkbookRepository workbookRepository;
    private final TaskPersistencer taskPersistencer;



/*
    private GraphImporter<String, DefaultEdge> buildGraphIDImporter()
    {
        return new DOTImporter<String, DefaultEdge>(
                (label, attributes) -> label, (from, to, label, attributes) -> new DefaultEdge(), null,
                (component, attributes) -> {
                    if (component instanceof GraphWithID) {
                        Attribute idAttribute = attributes.get("ID");
                        String id = "G";
                        if (idAttribute != null) {
                            id = idAttribute.getValue();
                        }
                        ((GraphWithID) component).id = id;
                    }
                });
    }
*/

    @FunctionalInterface
    public interface WorkWithGraphVoid {
        void execute(DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph);
    }

    public void processGraphVoid(WorkbookImpl workbook, WorkWithGraphVoid callable) throws ImportException {
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
            workbookRepository.save(workbook);
        }
    }



    private static final ComponentNameProvider<WorkbookParamsYaml.TaskVertex> vertexIdProvider = v -> v.taskId.toString();
    private static final ComponentNameProvider<WorkbookParamsYaml.TaskVertex> vertexLabelProvider = v -> "#" + v.taskId.toString();

    public int getCountUnfinishedTasks(Long workbookId) {
        if (true) throw new NotImplementedException("Not yet");
        return 0;
    }

    public static InternalVertex toInternalVertex(WorkbookParamsYaml.TaskVertex vertex) {
        return null;
    }

    public static WorkbookParamsYaml.TaskVertex toTaskVertex(InternalVertex vertex) {
        return null;
    }

    public static WorkbookParamsYaml.TaskVertex toTaskVertex(String id, Map<String, Attribute> attributes) {
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

    private class InternalVertex {
        String id;
        Map<String, Attribute> attributes;

        public InternalVertex(String id, Map<String, Attribute> attributes) {
            this.id = id;
            this.attributes = attributes;
        }

        public String getId() {
            return id;
        }

        public Map<String, Attribute> getAttributes() {
            return attributes;
        }
    }

//    DepthFirstIterator depthFirstIterator = new DepthFirstIterator<>(graph);
//    BreadthFirstIterator breadthFirstIterator = new BreadthFirstIterator<>(graph);

    private static final EdgeProvider<WorkbookParamsYaml.TaskVertex, DefaultEdge> ep = (f, t, l, attrs) -> new DefaultEdge();

    private GraphImporter<WorkbookParamsYaml.TaskVertex, DefaultEdge> buildImporter() {
        //noinspection UnnecessaryLocalVariable
        DOTImporter<WorkbookParamsYaml.TaskVertex, DefaultEdge> importer = new DOTImporter<>(WorkbookGraphService::toTaskVertex, ep);
        return importer;
    }

    public OperationStatusRest updateGraphWithResettingAllChildrenTasks(WorkbookImpl workbook, Long taskId) {
        try {
            processGraphVoid(workbook, graph -> {
                    graph.vertexSet()
                            .stream()
                            .filter(o -> o.taskId.equals(taskId))
                            .findFirst()
                            .ifPresent(currV -> graph.getDescendants(currV).forEach(t -> {
                        taskPersistencer.resetTask(t.taskId);
                        t.execState = EnumsApi.TaskExecState.NONE;
                    }));
            });
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable th) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage());
        }
    }

    public OperationStatusRest updateGraphWithInvalidatingAllChildrenTasks(WorkbookImpl workbook, Long taskId) {
        try {
            processGraphVoid(workbook, graph -> {
                graph.vertexSet()
                        .stream()
                        .filter(o -> o.taskId.equals(taskId))
                        .findFirst()
                        .ifPresent(currV -> graph.getDescendants(currV).forEach(t -> {
                            taskPersistencer.resetTask(t.taskId);
                            t.execState = EnumsApi.TaskExecState.NONE;
                        }));
            });
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable th) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage());
        }
    }

    public OperationStatusRest addNewTasksToGraph(WorkbookImpl workbook, List<Long> parentTaskIds, List<Long> taskIds) {
        try {
            final List<WorkbookParamsYaml.TaskVertex> parentVertexes = parentTaskIds
                    .stream().map(o-> new WorkbookParamsYaml.TaskVertex(o, EnumsApi.TaskExecState.NONE)).collect(Collectors.toList());

            processGraphVoid(workbook, graph -> {
                taskIds.forEach(id -> {
                    final WorkbookParamsYaml.TaskVertex v = new WorkbookParamsYaml.TaskVertex(id, EnumsApi.TaskExecState.NONE);
                    graph.addVertex(v);
                    parentVertexes.forEach(parentV -> graph.addEdge(parentV, v) );
                });
            });
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable th) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage());
        }
    }
}
