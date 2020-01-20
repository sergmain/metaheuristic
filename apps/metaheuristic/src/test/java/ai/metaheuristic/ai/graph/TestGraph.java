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
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookOperationStatusWithTaskList;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import lombok.extern.slf4j.Slf4j;
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
import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 7/7/2019
 * Time: 3:50 PM
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
@Slf4j
public class TestGraph extends PreparingPlan {

    @Autowired
    public WorkbookCache workbookCache;

    @Override
    public String getPlanYamlAsString() {
        return getPlanParamsYamlAsString_Simple();
    }

    @Test
    public void test() {

        PlanApiData.TaskProducingResultComplex result = workbookService.createWorkbook(plan.getId(), workbookYaml);
        workbook = (WorkbookImpl)result.workbook;

        assertNotNull(workbook);

        OperationStatusRest osr = workbookService.addNewTasksToGraph(workbook.id, List.of(), List.of(1L));
        workbook = workbookCache.findById(workbook.id);

        assertEquals(EnumsApi.OperationStatus.OK, osr.status);

        long count = workbookService.getCountUnfinishedTasks(workbook);
        assertEquals(1, count);


        osr = workbookService.addNewTasksToGraph(workbook.id,List.of(1L), List.of(2L, 3L));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        workbook = workbookCache.findById(workbook.id);

        count = workbookService.getCountUnfinishedTasks(workbook);
        assertEquals(3, count);

        List<TaskVertex> leafs = workbookService.findLeafs(workbook);

        assertEquals(2, leafs.size());
        assertTrue(leafs.contains(new TaskVertex(2L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(3L, EnumsApi.TaskExecState.NONE)));

        setExecState(workbook, 1L, EnumsApi.TaskExecState.BROKEN);
        setExecState(workbook, 2L, EnumsApi.TaskExecState.NONE);
        setExecState(workbook, 3L, EnumsApi.TaskExecState.NONE);

        WorkbookOperationStatusWithTaskList status =
                workbookService.updateGraphWithSettingAllChildrenTasksAsBroken(workbook.id,1L);
        assertEquals(EnumsApi.OperationStatus.OK, status.getStatus().status);
        workbook = workbookCache.findById(workbook.id);

        // there is only 'BROKEN' exec state
        Set<EnumsApi.TaskExecState> states = workbookService.findAll(workbook).stream().map(o -> o.execState).collect(Collectors.toSet());
        assertEquals(1, states.size());
        assertTrue(states.contains(EnumsApi.TaskExecState.BROKEN));

        count = workbookService.getCountUnfinishedTasks(workbook);
        assertEquals(0, count);


        setExecState(workbook, 1L, EnumsApi.TaskExecState.NONE);
        workbookService.updateGraphWithResettingAllChildrenTasks(workbook.id,1L);
        workbook = workbookCache.findById(workbook.id);

        // there is only 'NONE' exec state
        states = workbookService.findAll(workbook).stream().map(o -> o.execState).collect(Collectors.toSet());
        assertEquals(1, states.size());
        assertTrue(states.contains(EnumsApi.TaskExecState.NONE));
    }

    public void setExecState(WorkbookImpl workbook, Long id, EnumsApi.TaskExecState execState) {
        TaskImpl t1 = new TaskImpl();
        t1.id = id;
        t1.execState = execState.value;
        workbookService.updateTaskExecStateByWorkbookId(workbook.id, t1.id, t1.execState );
    }

}
