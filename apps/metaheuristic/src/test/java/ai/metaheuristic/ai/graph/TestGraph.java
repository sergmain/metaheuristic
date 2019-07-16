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

package ai.metaheuristic.ai.graph;

import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookGraphService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.data.workbook.WorkbookParamsYaml.TaskVertex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Serge
 * Date: 7/7/2019
 * Time: 3:50 PM
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestGraph {

    @Autowired
    public WorkbookGraphService workbookGraphService;

    @Test
    public void test() {
        WorkbookImpl workbook = new WorkbookImpl();
        WorkbookParamsYaml params = new WorkbookParamsYaml();
        params.graph = WorkbookGraphService.EMPTY_GRAPH;
        workbook.updateParams(params);

        workbookGraphService.addNewTasksToGraph(workbook, List.of(), List.of(1L));

        long count = workbookGraphService.getCountUnfinishedTasks(workbook);
        assertEquals(1, count);

        workbookGraphService.addNewTasksToGraph(workbook, List.of(1L), List.of(2L, 3L));

        count = workbookGraphService.getCountUnfinishedTasks(workbook);
        assertEquals(3, count);

        List<TaskVertex> leafs = workbookGraphService.findLeafs(workbook);

        assertEquals(2, leafs.size());
        assertTrue(leafs.contains(new TaskVertex(2L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(3L, EnumsApi.TaskExecState.NONE)));

        setExecState(workbook, 1L, EnumsApi.TaskExecState.BROKEN);
        setExecState(workbook, 2L, EnumsApi.TaskExecState.NONE);
        setExecState(workbook, 3L, EnumsApi.TaskExecState.NONE);
        workbookGraphService.updateGraphWithInvalidatingAllChildrenTasks(workbook, 1L);

        // there is only 'ERROR' exec state
        Set<EnumsApi.TaskExecState> states = workbookGraphService.findAll(workbook).stream().map(o -> o.execState).collect(Collectors.toSet());
        assertEquals(1, states.size());
        assertTrue(states.contains(EnumsApi.TaskExecState.BROKEN));

        count = workbookGraphService.getCountUnfinishedTasks(workbook);
        assertEquals(0, count);


        setExecState(workbook, 1L, EnumsApi.TaskExecState.NONE);
        workbookGraphService.updateGraphWithResettingAllChildrenTasks(workbook, 1L);

        // there is only 'NONE' exec state
        states = workbookGraphService.findAll(workbook).stream().map(o -> o.execState).collect(Collectors.toSet());
        assertEquals(1, states.size());
        assertTrue(states.contains(EnumsApi.TaskExecState.NONE));
    }

    public void setExecState(WorkbookImpl workbook, Long id, EnumsApi.TaskExecState execState) {
        TaskImpl t1 = new TaskImpl();
        t1.id = id;
        t1.execState = execState.value;
        workbookGraphService.updateTaskExecState(workbook, t1.id, t1.execState );
    }
}
