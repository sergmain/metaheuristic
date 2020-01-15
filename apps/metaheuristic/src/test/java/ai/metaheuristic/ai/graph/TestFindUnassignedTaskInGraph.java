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
import java.util.stream.Collectors;

import static ai.metaheuristic.api.data.workbook.WorkbookParamsYaml.TaskVertex;
import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 7/25/2019
 * Time: 3:50 PM
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
@Slf4j
public class TestFindUnassignedTaskInGraph extends PreparingPlan {

    @Autowired
    public WorkbookCache workbookCache;

    @Override
    public String getPlanYamlAsString() {
        return getPlanParamsYamlAsString_Simple();
    }

    @Test
    public void test() {

        PlanApiData.TaskProducingResultComplex result = workbookService.createWorkbook(plan.getId(), resourceCodes);
        workbook = (WorkbookImpl)result.workbook;

        assertNotNull(workbook);

        OperationStatusRest osr = workbookService.addNewTasksToGraph(workbook.id, List.of(), List.of(1L));
        workbook = workbookCache.findById(workbook.id);

        assertEquals(EnumsApi.OperationStatus.OK, osr.status);

        long count = workbookService.getCountUnfinishedTasks(workbook);
        assertEquals(1, count);


        osr = workbookService.addNewTasksToGraph(workbook.id,List.of(1L), List.of(21L, 22L));

        osr = workbookService.addNewTasksToGraph(workbook.id,List.of(21L), List.of(311L, 312L, 313L));

        osr = workbookService.addNewTasksToGraph(workbook.id,List.of(22L), List.of(321L, 322L, 323L));

        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        workbook = workbookCache.findById(workbook.id);

        count = workbookService.getCountUnfinishedTasks(workbook);
        assertEquals(9, count);

        List<TaskVertex> leafs = workbookService.findLeafs(workbook);

        assertEquals(6, leafs.size());
        assertTrue(leafs.contains(new TaskVertex(311L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(312L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(313L, EnumsApi.TaskExecState.NONE)));

        assertTrue(leafs.contains(new TaskVertex(321L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(322L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(323L, EnumsApi.TaskExecState.NONE)));


        Set<EnumsApi.TaskExecState> states;
        workbookService.updateGraphWithResettingAllChildrenTasks(workbook.id,1L);
        workbook = workbookCache.findById(workbook.id);

        // there is only 'NONE' exec state
        states = workbookService.findAll(workbook).stream().map(o -> o.execState).collect(Collectors.toSet());
        assertEquals(1, states.size());
        assertTrue(states.contains(EnumsApi.TaskExecState.NONE));

        List<WorkbookParamsYaml.TaskVertex> vertices = workbookService.findAllForAssigning(workbookRepository.findByIdForUpdate(workbook.id));

        assertEquals(1, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
        assertEquals(Long.valueOf(1L), vertices.get(0).taskId);

        OperationStatusRest status = workbookService.updateTaskExecStateByWorkbookId(workbook.id,1L, EnumsApi.TaskExecState.OK.value);

        assertEquals(EnumsApi.OperationStatus.OK, status.status);
        workbook = workbookCache.findById(workbook.id);

        vertices = workbookService.findAllForAssigning(workbookRepository.findByIdForUpdate(workbook.id));

        assertEquals(2, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
        assertTrue(Set.of(21L, 22L).contains(vertices.get(0).taskId));

        status = workbookService.updateTaskExecStateByWorkbookId(workbook.id,22L, EnumsApi.TaskExecState.IN_PROGRESS.value);
        workbook = workbookCache.findById(workbook.id);

        vertices = workbookService.findAllForAssigning(workbookRepository.findByIdForUpdate(workbook.id));

        assertEquals(1, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
        assertEquals(Long.valueOf(21L), vertices.get(0).taskId);


        status = workbookService.updateTaskExecStateByWorkbookId(workbook.id,22L, EnumsApi.TaskExecState.BROKEN.value);
        workbook = workbookCache.findById(workbook.id);

        vertices = workbookService.findAllForAssigning(workbookRepository.findByIdForUpdate(workbook.id));

        assertEquals(1, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
        assertEquals(Long.valueOf(21L), vertices.get(0).taskId);

        status = workbookService.updateTaskExecStateByWorkbookId(workbook.id,21L, EnumsApi.TaskExecState.OK.value);

        vertices = workbookService.findAllForAssigning(workbookRepository.findByIdForUpdate(workbook.id));

        assertEquals(3, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(0).taskId));
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(1).taskId));
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(2).taskId));

        status = workbookService.updateTaskExecStateByWorkbookId(workbook.id,22L, EnumsApi.TaskExecState.OK.value);
        workbook = workbookCache.findById(workbook.id);


        vertices = workbookService.findAllForAssigning(workbookRepository.findByIdForUpdate(workbook.id));

        assertEquals(6, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
        assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(0).taskId));
        assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(1).taskId));
        assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(2).taskId));
        assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(3).taskId));
        assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(4).taskId));
        assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(5).taskId));
    }

}
