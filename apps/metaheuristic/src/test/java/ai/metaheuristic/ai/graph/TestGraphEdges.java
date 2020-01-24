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

import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.plan.PlanApiData;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 7/16/2019
 * Time: 8:53 PM
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
@Slf4j
public class TestGraphEdges extends PreparingPlan {

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

        OperationStatusRest osr = workbookGraphTopLevelService.addNewTasksToGraph(workbook.id, List.of(), List.of(1L));
        workbook = workbookCache.findById(workbook.id);

        assertEquals(EnumsApi.OperationStatus.OK, osr.status);

        long count = workbookService.getCountUnfinishedTasks(workbook);
        assertEquals(1, count);


        osr = workbookGraphTopLevelService.addNewTasksToGraph(workbook.id,List.of(1L), List.of(21L, 22L, 23L));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        workbook = workbookCache.findById(workbook.id);

        List<WorkbookParamsYaml.TaskVertex> leafs = workbookGraphTopLevelService.findLeafs(workbook);

        assertEquals(3, leafs.size());
        assertTrue(leafs.contains(new WorkbookParamsYaml.TaskVertex(21L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new WorkbookParamsYaml.TaskVertex(22L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new WorkbookParamsYaml.TaskVertex(23L, EnumsApi.TaskExecState.NONE)));

        osr = workbookGraphTopLevelService.addNewTasksToGraph( workbook.id,List.of(21L), List.of(311L, 312L, 313L));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        workbook = workbookCache.findById(workbook.id);

        Set<WorkbookParamsYaml.TaskVertex> descendands = workbookGraphTopLevelService.findDescendants(workbook, 1L);
        assertEquals(6, descendands.size());

        descendands = workbookGraphTopLevelService.findDescendants(workbook, 21L);
        assertEquals(3, descendands.size());

        leafs = workbookGraphTopLevelService.findLeafs(workbook);
        assertEquals(5, leafs.size());
    }
}
