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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData.TaskVertex_140;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 7/7/2019
 * Time: 3:50 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
public class TestGraph extends PreparingSourceCode {

    @Autowired
    public ExecContextCache execContextCache;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    /**
     * this test will produce warnings in log such:
     *      #306.010 Can't find Task for Id: 1
     *
     *  this is a normal situation
     */
    @Test
    public void test() {

        ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorService.createExecContext(sourceCode, company.getUniqueId());
        execContextForTest = result.execContext;

        assertNotNull(execContextForTest);

        OperationStatusRest osr = execContextGraphTopLevelService.addNewTasksToGraph(execContextForTest.id, List.of(), List.of(1L));
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        assertEquals(EnumsApi.OperationStatus.OK, osr.status);

        long count = execContextService.getCountUnfinishedTasks(execContextForTest);
        assertEquals(1, count);


        osr = execContextGraphTopLevelService.addNewTasksToGraph(execContextForTest.id, List.of(1L), List.of(2L, 3L));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status, osr.getErrorMessagesAsStr());
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        count = execContextService.getCountUnfinishedTasks(execContextForTest);
        assertEquals(3, count);

        List<TaskVertex_140> leafs = execContextGraphTopLevelService.findLeafs(execContextForTest);

        assertEquals(2, leafs.size());
        assertTrue(leafs.contains(new TaskVertex_140(2L, "2L", EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex_140(3L, "3L", EnumsApi.TaskExecState.NONE)));

        setExecState(execContextForTest, 1L, EnumsApi.TaskExecState.BROKEN);
        setExecState(execContextForTest, 2L, EnumsApi.TaskExecState.NONE);
        setExecState(execContextForTest, 3L, EnumsApi.TaskExecState.NONE);

        ExecContextOperationStatusWithTaskList status =
                execContextGraphTopLevelService.updateGraphWithSettingAllChildrenTasksAsBroken(execContextForTest.id,1L);
        assertEquals(EnumsApi.OperationStatus.OK, status.getStatus().status);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        Set<EnumsApi.TaskExecState> states = execContextGraphTopLevelService.findAll(execContextForTest).stream().map(o -> o.execState).collect(Collectors.toSet());
        assertEquals(2, states.size());
        // there are o'BROKEN' state for 1st task and NONE for the last one
        assertTrue(states.contains(EnumsApi.TaskExecState.BROKEN));
        assertTrue(states.contains(EnumsApi.TaskExecState.NONE));

        count = execContextService.getCountUnfinishedTasks(execContextForTest);
        // there is one unfinished task which is mh.finish and which must me invoked in any case
        assertEquals(2, count);


        setExecState(execContextForTest, 1L, EnumsApi.TaskExecState.NONE);
        execContextGraphTopLevelService.updateGraphWithResettingAllChildrenTasks(execContextForTest.id,1L);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        // there is only 'NONE' exec state
        states = execContextGraphTopLevelService.findAll(execContextForTest).stream().map(o -> o.execState).collect(Collectors.toSet());
        assertEquals(1, states.size());
        assertTrue(states.contains(EnumsApi.TaskExecState.NONE));
    }

    private void setExecState(ExecContextImpl workbook, Long id, EnumsApi.TaskExecState execState) {
        TaskImpl t1 = new TaskImpl();
        t1.id = id;
        t1.execState = execState.value;
        execContextGraphTopLevelService.updateTaskExecStateByExecContextId(workbook.id, t1.id, t1.execState );
    }

}
