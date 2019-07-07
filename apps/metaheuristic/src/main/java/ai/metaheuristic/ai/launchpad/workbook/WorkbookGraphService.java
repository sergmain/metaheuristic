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
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.apache.commons.lang3.NotImplementedException;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.graph.Multigraph;
import org.jgrapht.io.*;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;

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

    }

    public static WorkbookParamsYaml.TaskVertex toTaskVertex(String id, Map<String, Attribute> attributes) {
        WorkbookParamsYaml.TaskVertex v = new WorkbookParamsYaml.TaskVertex();
        v.taskId = Long.valueOf(id);
        if (attributes==null) {
            return v;
        }

        final Attribute execState = attributes.get("task_exec_state");
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

    private static final EdgeProvider<WorkbookParamsYaml.TaskVertex, DefaultEdge> ep = (f, t, l, attrs) -> new DefaultEdge();

    private GraphImporter<WorkbookParamsYaml.TaskVertex, DefaultEdge> buildImporter() {
        //noinspection UnnecessaryLocalVariable
        DOTImporter<WorkbookParamsYaml.TaskVertex, DefaultEdge> importer = new DOTImporter<>(WorkbookGraphService::toTaskVertex, ep);
        return importer;
    }

    public OperationStatusRest updateGraphWithResettingAllChildrenTasks(WorkbookImpl workbook, Long taskId) {


        try {
            WorkbookParamsYaml wpy = workbook.getWorkbookParamsYaml();

            GraphImporter<WorkbookParamsYaml.TaskVertex, DefaultEdge> importer = buildImporter();

            DirectedAcyclicGraph<WorkbookParamsYaml.TaskVertex, DefaultEdge> graph = new DirectedAcyclicGraph<>(DefaultEdge.class);
            importer.importGraph(graph, new StringReader(wpy.graph));

            DepthFirstIterator depthFirstIterator
                    = new DepthFirstIterator<>(graph);
            BreadthFirstIterator breadthFirstIterator
                    = new BreadthFirstIterator<>(graph);

            WorkbookParamsYaml.TaskVertex v = graph.vertexSet().stream().filter(o-> o.taskId.equals(taskId)).findFirst().orElse(null);

            graph.getDescendants(v).stream().forEach(v-> taskPersistencer.resetTask(v.taskId));
            taskPersistencer.resetTask(task

            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable th) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, th.getMessage());
        }
    }

    public void updateGraphWithInvalidatingAllChildrenTasks(WorkbookImpl workbook, Long taskId) {

    }

    public void addNewTasksToGraph(WorkbookImpl wb, List<Long> parentTaskIds, List<Long> taskIds) {

    }

}
