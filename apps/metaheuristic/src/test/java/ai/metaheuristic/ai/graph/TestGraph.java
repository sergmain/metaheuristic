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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData.TaskVertex;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskApiData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
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
@DirtiesContext
@AutoConfigureCache
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
                () -> txSupportForTestingService.finishWithErrorWithTx(taskId, "to finish", null));
    }

    @Test
    public void test() {

        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(sourceCode, company.getUniqueId());
        execContextForTest = result.execContext;
        assertNotNull(execContextForTest);

        execContextSyncService.getWithSyncNullable(execContextForTest.id, () -> {
            OperationStatusRest osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id, List.of(),
                    List.of(new TaskApiData.TaskWithContext(1L, Consts.TOP_LEVEL_CONTEXT_ID)));
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            assertEquals(EnumsApi.OperationStatus.OK, osr.status);

            long count = execContextGraphTopLevelService.getCountUnfinishedTasks(execContextForTest);
            assertEquals(1, count);


            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id, List.of(1L),
                    List.of(new TaskApiData.TaskWithContext(2L, "123###1"), new TaskApiData.TaskWithContext(3L, "123###2")));
            assertEquals(EnumsApi.OperationStatus.OK, osr.status, osr.getErrorMessagesAsStr());
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id, List.of(1L, 2L, 3L),
                    List.of(new TaskApiData.TaskWithContext(4L, Consts.TOP_LEVEL_CONTEXT_ID)));
            assertEquals(EnumsApi.OperationStatus.OK, osr.status, osr.getErrorMessagesAsStr());
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            count = execContextGraphTopLevelService.getCountUnfinishedTasks(execContextForTest);
            assertEquals(4, count);

            List<TaskVertex> leafs = execContextGraphTopLevelService.findLeafs(execContextForTest);

            assertEquals(1, leafs.size());
            assertTrue(leafs.contains(new TaskVertex(4L, "4L", EnumsApi.TaskExecState.NONE, Consts.TOP_LEVEL_CONTEXT_ID)));
//            assertTrue(leafs.contains(new TaskVertex(3L, "3L", EnumsApi.TaskExecState.NONE, "123###2")));

//            txSupportForTestingService.finishWithErrorWithTx(1L, "An error", null);

            txSupportForTestingService.updateTaskExecState(execContextForTest.id, 1L, EnumsApi.TaskExecState.ERROR, null);
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));
            checkState(1L, EnumsApi.TaskExecState.ERROR);
            checkState(2L, EnumsApi.TaskExecState.SKIPPED);
            checkState(3L, EnumsApi.TaskExecState.SKIPPED);

//            execContextTaskStateService.updateTaskExecStatesWithTx(execContextForTest.id, 2L, EnumsApi.TaskExecState.NONE, "123###1");
//            execContextTaskStateService.updateTaskExecStatesWithTx(execContextForTest.id, 3L, EnumsApi.TaskExecState.NONE, "123###1");

//        updateGraphWithSettingAllChildrenTasksAsError( Objects.requireNonNull(execContextService.findById(execContextForTest.id)), 1L);

            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            Set<EnumsApi.TaskExecState> states = execContextGraphTopLevelService.findAll(execContextForTest).stream().map(o -> o.execState).collect(Collectors.toSet());
            assertEquals(3, states.size());
            // there are 'ERROR' state for 1st task and NONE for the other two
            assertTrue(states.contains(EnumsApi.TaskExecState.ERROR));
            assertTrue(states.contains(EnumsApi.TaskExecState.SKIPPED));
            assertTrue(states.contains(EnumsApi.TaskExecState.NONE));

            count = execContextGraphTopLevelService.getCountUnfinishedTasks(execContextForTest);
            // there is one unfinished task which is mh.finish and which must me invoked in any case
            assertEquals(1, count);


            // reset all graph
            txSupportForTestingService.updateTaskExecState(execContextForTest.id, 1L, EnumsApi.TaskExecState.NONE, Consts.TOP_LEVEL_CONTEXT_ID);
            txSupportForTestingService.updateGraphWithResettingAllChildrenTasksWithTx(execContextForTest.id, 1L);
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            // there is only 'NONE' exec state
            states = txSupportForTestingService.findAllWithTx(execContextForTest).stream().map(o -> o.execState).collect(Collectors.toSet());
            assertEquals(1, states.size());
            assertTrue(states.contains(EnumsApi.TaskExecState.NONE));
            return null;
        });
    }

    private void checkState(long id, EnumsApi.TaskExecState state) {
        TaskVertex v = execContextGraphService.findVertex(execContextForTest, id);
        assertNotNull(v);
        assertEquals(state, v.execState);
    }

}
