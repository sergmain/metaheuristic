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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
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

import static ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml.TaskVertex;
import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 7/7/2019
 * Time: 3:50 PM
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
public class TestGraph extends PreparingPlan {

    @Autowired
    public ExecContextCache execContextCache;

    @Override
    public String getPlanYamlAsString() {
        return getPlanParamsYamlAsString_Simple();
    }

    @Test
    public void test() {

        SourceCodeApiData.TaskProducingResultComplex result = execContextService.createExecContext(plan.getId(), execContextYaml);
        workbook = (ExecContextImpl)result.execContext;

        assertNotNull(workbook);

        OperationStatusRest osr = execContextGraphTopLevelService.addNewTasksToGraph(workbook.id, List.of(), List.of(1L));
        workbook = execContextCache.findById(workbook.id);

        assertEquals(EnumsApi.OperationStatus.OK, osr.status);

        long count = execContextService.getCountUnfinishedTasks(workbook);
        assertEquals(1, count);


        osr = execContextGraphTopLevelService.addNewTasksToGraph(workbook.id,List.of(1L), List.of(2L, 3L));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        workbook = execContextCache.findById(workbook.id);

        count = execContextService.getCountUnfinishedTasks(workbook);
        assertEquals(3, count);

        List<TaskVertex> leafs = execContextGraphTopLevelService.findLeafs(workbook);

        assertEquals(2, leafs.size());
        assertTrue(leafs.contains(new TaskVertex(2L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(3L, EnumsApi.TaskExecState.NONE)));

        setExecState(workbook, 1L, EnumsApi.TaskExecState.BROKEN);
        setExecState(workbook, 2L, EnumsApi.TaskExecState.NONE);
        setExecState(workbook, 3L, EnumsApi.TaskExecState.NONE);

        ExecContextOperationStatusWithTaskList status =
                execContextGraphTopLevelService.updateGraphWithSettingAllChildrenTasksAsBroken(workbook.id,1L);
        assertEquals(EnumsApi.OperationStatus.OK, status.getStatus().status);
        workbook = execContextCache.findById(workbook.id);

        // there is only 'BROKEN' exec state
        Set<EnumsApi.TaskExecState> states = execContextGraphTopLevelService.findAll(workbook).stream().map(o -> o.execState).collect(Collectors.toSet());
        assertEquals(1, states.size());
        assertTrue(states.contains(EnumsApi.TaskExecState.BROKEN));

        count = execContextService.getCountUnfinishedTasks(workbook);
        assertEquals(0, count);


        setExecState(workbook, 1L, EnumsApi.TaskExecState.NONE);
        execContextGraphTopLevelService.updateGraphWithResettingAllChildrenTasks(workbook.id,1L);
        workbook = execContextCache.findById(workbook.id);

        // there is only 'NONE' exec state
        states = execContextGraphTopLevelService.findAll(workbook).stream().map(o -> o.execState).collect(Collectors.toSet());
        assertEquals(1, states.size());
        assertTrue(states.contains(EnumsApi.TaskExecState.NONE));
    }

    public void setExecState(ExecContextImpl workbook, Long id, EnumsApi.TaskExecState execState) {
        TaskImpl t1 = new TaskImpl();
        t1.id = id;
        t1.execState = execState.value;
        execContextGraphTopLevelService.updateTaskExecStateByExecContextId(workbook.id, t1.id, t1.execState );
    }

}
