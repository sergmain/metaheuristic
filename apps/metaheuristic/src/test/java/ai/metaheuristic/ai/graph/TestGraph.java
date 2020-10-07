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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData.TaskVertex;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskApiData;
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

    @Autowired
    public TaskTransactionalService taskTransactionalService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    private void updateGraphWithSettingAllChildrenTasksAsError(ExecContextImpl execContext, Long taskId) {
        execContextSyncService.getWithSync(execContext.id,
                () -> execContextTaskFinishingService.finishWithErrorWithTx(taskId, "to finish", execContext.id, null));
        // execContextGraphService.updateTaskExecState(execContext, taskId, EnumsApi.ExecContextState.FINISHED.code, null)
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

        execContextSyncService.getWithSyncNullable(execContextForTest.id, () -> {
            OperationStatusRest osr = execContextFSM.addTasksToGraph(execContextService.findById(execContextForTest.id), List.of(),
                    List.of(new TaskApiData.TaskWithContext(1L, "123###1")));
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            assertEquals(EnumsApi.OperationStatus.OK, osr.status);

            long count = execContextGraphTopLevelService.getCountUnfinishedTasks(execContextForTest);
            assertEquals(1, count);


            osr = execContextFSM.addTasksToGraph(execContextService.findById(execContextForTest.id), List.of(1L),
                    List.of(new TaskApiData.TaskWithContext(2L, "123###1"), new TaskApiData.TaskWithContext(3L, "123###1")));
            assertEquals(EnumsApi.OperationStatus.OK, osr.status, osr.getErrorMessagesAsStr());
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            count = execContextGraphTopLevelService.getCountUnfinishedTasks(execContextForTest);
            assertEquals(3, count);

            List<TaskVertex> leafs = execContextGraphTopLevelService.findLeafs(execContextForTest);

            assertEquals(2, leafs.size());
            assertTrue(leafs.contains(new TaskVertex(2L, "2L", EnumsApi.TaskExecState.NONE, "123###1")));
            assertTrue(leafs.contains(new TaskVertex(3L, "3L", EnumsApi.TaskExecState.NONE, "123###1")));

            setExecStateError(execContextForTest.id, 1L, "An error");
            setExecState(execContextForTest, 2L, EnumsApi.TaskExecState.NONE);
            setExecState(execContextForTest, 3L, EnumsApi.TaskExecState.NONE);
            return null;
        });

        updateGraphWithSettingAllChildrenTasksAsError( Objects.requireNonNull(execContextService.findById(execContextForTest.id)), 1L);

        execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

        execContextSyncService.getWithSyncNullable(execContextForTest.id, () -> {
            Set<EnumsApi.TaskExecState> states = execContextGraphTopLevelService.findAll(execContextForTest).stream().map(o -> o.execState).collect(Collectors.toSet());
            assertEquals(2, states.size());
            // there are 'ERROR' state for 1st task and NONE for the last one
            assertTrue(states.contains(EnumsApi.TaskExecState.ERROR));
            assertTrue(states.contains(EnumsApi.TaskExecState.NONE));

            long count = execContextGraphTopLevelService.getCountUnfinishedTasks(execContextForTest);
            // there is one unfinished task which is mh.finish and which must me invoked in any case
            assertEquals(2, count);


            setExecState(execContextForTest, 1L, EnumsApi.TaskExecState.NONE);
            execContextGraphService.updateGraphWithResettingAllChildrenTasksWithTx(execContextService.findById(execContextForTest.id), 1L);
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            // there is only 'NONE' exec state
            states = execContextGraphTopLevelService.findAllWithTx(execContextForTest).stream().map(o -> o.execState).collect(Collectors.toSet());
            assertEquals(1, states.size());
            assertTrue(states.contains(EnumsApi.TaskExecState.NONE));
            return null;
        });
    }

    private void setExecState(ExecContextImpl workbook, Long id, EnumsApi.TaskExecState execState) {
        execContextTaskStateService.updateTaskExecStatesWithTx(execContextService.findById(workbook.id), id, execState, "123###1");
    }

    private void setExecStateError(Long execContextId, Long taskId, String console) {
        execContextTaskFinishingService.finishWithErrorWithTx(taskId, console, execContextId, null);
    }

}
