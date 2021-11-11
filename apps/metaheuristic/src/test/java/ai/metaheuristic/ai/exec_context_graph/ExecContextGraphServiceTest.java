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

package ai.metaheuristic.ai.exec_context_graph;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextGraph;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Serge
 * Date: 11/10/2021
 * Time: 6:30 PM
 */
public class ExecContextGraphServiceTest {

    @Test
    public void test_findAllForAssigning() throws IOException {
        ExecContextGraph ecg = new ExecContextGraph();
        ecg.id = 2908L;
        ecg.execContextId = null;
        ecg.version = 2;
        ecg.setParams( IOUtils.resourceToString("/test_data/exec_context_graph/exec-context-graph.yaml", StandardCharsets.UTF_8) );

        ExecContextTaskState ects = new ExecContextTaskState();
        ects.id = 2908L;
        ects.execContextId = null;
        ects.setParams(IOUtils.resourceToString("/test_data/exec_context_graph/exec_context_task_state.yaml", StandardCharsets.UTF_8));


        List<ExecContextData.TaskVertex> vertices = ExecContextGraphService.findAllForAssigning(ecg, ects, true);

        System.out.println(vertices);

        assertEquals(2, vertices.size());
        assertTrue(vertices.contains(new ExecContextData.TaskVertex(978308L)));
        assertTrue(vertices.contains(new ExecContextData.TaskVertex(978189L)));
    }
}
