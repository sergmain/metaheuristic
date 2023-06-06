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

package ai.metaheuristic.ai.graph;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 7/25/2019
 * Time: 3:50 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles({"dispatcher", "mysql"})
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TestFindUnassignedTaskInGraph extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private ExecContextGraphTopLevelService execContextGraphTopLevelService;
    @Autowired private TestGraphService testGraphService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void test() {

        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), getCompany().getUniqueId());
        setExecContextForTest(result.execContext);
        assertNotNull(getExecContextForTest());

        ExecContextSyncService.getWithSync(getExecContextForTest().id, ()->
                ExecContextGraphSyncService.getWithSync(getExecContextForTest().execContextGraphId, ()->
                        ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId, ()-> {

                            OperationStatusRest osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                                    List.of(), List.of(new TaskApiData.TaskWithContext(1L, Consts.TOP_LEVEL_CONTEXT_ID)));
                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            assertEquals(EnumsApi.OperationStatus.OK, osr.status);

                            long count = preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest());
                            assertEquals(1, count);


                            osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(1L),
                                    List.of(new TaskApiData.TaskWithContext(21L, "12#1"), new TaskApiData.TaskWithContext(22L, "12#2")));

                            osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(21L),
                                    List.of(new TaskApiData.TaskWithContext(311L, "123#1"),
                                            new TaskApiData.TaskWithContext(312L, "123#2"),
                                            new TaskApiData.TaskWithContext(313L, "123#3")));

                            osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(22L),
                                    List.of(new TaskApiData.TaskWithContext(321L, "123#4"),
                                            new TaskApiData.TaskWithContext(322L, "123#5"),
                                            new TaskApiData.TaskWithContext(323L, "123#6")));

                            // 999L is mh.finish task
                            osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(1L, 21L, 22L, 311L, 312L, 313L, 321L, 322L, 323L),
                                    List.of(new TaskApiData.TaskWithContext(999L, Consts.TOP_LEVEL_CONTEXT_ID)));

                            assertEquals(EnumsApi.OperationStatus.OK, osr.status);
                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            count = preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest());
                            assertEquals(10, count);

                            List<ExecContextData.TaskVertex> leafs = testGraphService.findLeafs(getExecContextForTest());


                            assertEquals(1, leafs.size());

                            Set<ExecContextData.TaskVertex> ancestors = testGraphService.findDirectAncestors(getExecContextForTest(), leafs.get(0));

                            assertEquals(9, ancestors.size());
                            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(1L, Consts.TOP_LEVEL_CONTEXT_ID)));
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 1L));

                            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(21L, "12#1")));
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 21L));

                            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(22L, "12#2")));
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 22L));

                            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(311L, "123#1")));
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 311L));
                            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(312L, "123#2")));
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 312L));
                            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(313L, "123#3")));
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 313L));

                            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(321L, "123#4")));
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 321L));
                            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(322L, "123#5")));
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 322L));
                            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(323L, "123#6")));
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 323L));


                            Set<EnumsApi.TaskExecState> states;
                            txSupportForTestingService.updateGraphWithResettingAllChildrenTasksWithTx(
                                    getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, 1L);
                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            // there is only 'NONE' exec state
                            states = execContextGraphTopLevelService.findAll(getExecContextForTest().execContextGraphId).stream()
                                    .map(o -> preparingSourceCodeService.findTaskState(getExecContextForTest(), o.taskId))
                                    .collect(Collectors.toSet());

                            assertEquals(1, states.size());
                            assertTrue(states.contains(EnumsApi.TaskExecState.NONE));

                            List<ExecContextData.TaskVertex> vertices = execContextGraphTopLevelService.findAllForAssigning(
                                    getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId);

                            assertEquals(1, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
                            assertEquals(Long.valueOf(1L), vertices.get(0).taskId);

                            ExecContextOperationStatusWithTaskList status = txSupportForTestingService.updateTaskExecState(
                                    getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId,1L, EnumsApi.TaskExecState.OK, Consts.TOP_LEVEL_CONTEXT_ID);

                            // !!! TODO 2020-10-06 need to rewrite with using real Tasks

                            assertEquals(EnumsApi.OperationStatus.OK, status.status.status);
                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            vertices = execContextGraphTopLevelService.findAllForAssigning(
                                    getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId);

                            assertEquals(2, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
                            assertTrue(Set.of(21L, 22L).contains(vertices.get(0).taskId));

                            status = txSupportForTestingService.updateTaskExecState(
                                    getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId,22L, EnumsApi.TaskExecState.IN_PROGRESS, "12#2");

                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            vertices = txSupportForTestingService.findAllForAssigningWithTx(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId);

                            assertEquals(1, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
                            assertEquals(Long.valueOf(21L), vertices.get(0).taskId);

                            txSupportForTestingService.updateTaskExecState(
                                    getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, 22L, EnumsApi.TaskExecState.ERROR, "12#2");

                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            vertices = txSupportForTestingService.findAllForAssigningWithTx(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId);

                            assertEquals(1, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
                            assertEquals(Long.valueOf(21L), vertices.get(0).taskId);

                            status = txSupportForTestingService.updateTaskExecState(
                                    getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId,21L, EnumsApi.TaskExecState.OK, "123#1");

                            vertices = txSupportForTestingService.findAllForAssigningWithTx(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId);

                            assertEquals(3, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
                            assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(0).taskId));
                            assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(1).taskId));
                            assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(2).taskId));

                            // in production code this will never happened, i.e. switching from ERROR state to OK state
                            status = txSupportForTestingService.updateTaskExecState(
                                    getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId,22L, EnumsApi.TaskExecState.OK, "123#1");
                            // so we update children manually
                            txSupportForTestingService.setStateForAllChildrenTasksInternal(
                                    getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, 22L, status, EnumsApi.TaskExecState.NONE);


                            setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

                            vertices = txSupportForTestingService.findAllForAssigningWithTx(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId);

                            assertEquals(6, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(0).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(1).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(2).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(3).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(4).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(5).taskId));
                            return null;
                        })));
    }

}
