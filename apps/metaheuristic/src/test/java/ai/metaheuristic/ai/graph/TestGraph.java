/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData.TaskVertex;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskApiData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 7/7/2019
 * Time: 3:50 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles({"dispatcher", "mysql"})
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TestGraph extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private TestGraphService testGraphService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void test() {

        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), getCompany().getUniqueId());
        setExecContextForTest(result.execContext);
        assertNotNull(getExecContextForTest());

        ExecContextSyncService.getWithSync(getExecContextForTest().id, () ->
                ExecContextGraphSyncService.getWithSync(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId, () -> {
                            OperationStatusRest osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(),
                                    List.of(new TaskApiData.TaskWithContext(1L, Consts.TOP_LEVEL_CONTEXT_ID)));

                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            assertEquals(EnumsApi.OperationStatus.OK, osr.status);

                            long count = preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest());
                            assertEquals(1, count);


                            osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(1L),
                                    List.of(new TaskApiData.TaskWithContext(2L, "123###1"), new TaskApiData.TaskWithContext(3L, "123###2")));
                            assertEquals(EnumsApi.OperationStatus.OK, osr.status, osr.getErrorMessagesAsStr());
                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(1L, 2L, 3L),
                                    List.of(new TaskApiData.TaskWithContext(4L, Consts.TOP_LEVEL_CONTEXT_ID)));
                            assertEquals(EnumsApi.OperationStatus.OK, osr.status, osr.getErrorMessagesAsStr());
                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            count = preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest());
                            assertEquals(4, count);

                            List<TaskVertex> leafs = testGraphService.findLeafs(getExecContextForTest());

                            assertEquals(1, leafs.size());
                            assertTrue(leafs.contains(new TaskVertex(4L, Consts.TOP_LEVEL_CONTEXT_ID)));

                            txSupportForTestingService.updateTaskExecState(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, 1L, EnumsApi.TaskExecState.ERROR, Consts.TOP_LEVEL_CONTEXT_ID);
                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));
                            checkState(1L, EnumsApi.TaskExecState.ERROR);
                            checkState(2L, EnumsApi.TaskExecState.SKIPPED);
                            checkState(3L, EnumsApi.TaskExecState.SKIPPED);

                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            Set<EnumsApi.TaskExecState> states = testGraphService.findTaskStates(getExecContextForTest());
                            assertEquals(3, states.size());
                            // there are 'ERROR' state for 1st task and NONE for the other two
                            assertTrue(states.contains(EnumsApi.TaskExecState.ERROR));
                            assertTrue(states.contains(EnumsApi.TaskExecState.SKIPPED));
                            assertTrue(states.contains(EnumsApi.TaskExecState.NONE));

                            count = preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest());
                            // there is one unfinished task which is mh.finish and which must me invoked in any case
                            assertEquals(1, count);


                            // reset all graph
                            txSupportForTestingService.updateTaskExecState(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, 1L, EnumsApi.TaskExecState.NONE, Consts.TOP_LEVEL_CONTEXT_ID);
                            txSupportForTestingService.updateGraphWithResettingAllChildrenTasksWithTx(
                                    getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, 1L);

                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            // there is only 'NONE' exec state
                            states = testGraphService.findTaskStates(getExecContextForTest());
                            assertEquals(1, states.size());
                            assertTrue(states.contains(EnumsApi.TaskExecState.NONE));
                            return null;
                        })));
    }

    private void checkState(Long id, EnumsApi.TaskExecState state) {
        EnumsApi.TaskExecState v = preparingSourceCodeService.findTaskState(getExecContextForTest(), id);
        assertNotNull(v);
        assertEquals(state, v);
    }

}
