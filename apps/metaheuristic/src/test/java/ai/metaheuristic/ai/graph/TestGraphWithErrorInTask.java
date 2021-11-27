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
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Serge
 * Date: 3/26/2021
 * Time: 4:15 PM
 */
@SuppressWarnings("UnusedAssignment")
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
@DirtiesContext
@AutoConfigureCache
public class TestGraphWithErrorInTask extends PreparingSourceCode {

    @Autowired
    public ExecContextCache execContextCache;

    @Autowired
    public TaskTransactionalService taskTransactionalService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void test() {

        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(sourceCode, company.getUniqueId());
        execContextForTest = result.execContext;
        assertNotNull(execContextForTest);

        ExecContextSyncService.getWithSync(execContextForTest.id, ()->
                ExecContextGraphSyncService.getWithSync(execContextForTest.execContextGraphId, ()->
                        ExecContextTaskStateSyncService.getWithSync(execContextForTest.execContextTaskStateId, ()-> {


                            final TaskApiData.TaskWithContext t1 = new TaskApiData.TaskWithContext(1L, Consts.TOP_LEVEL_CONTEXT_ID);
                            OperationStatusRest osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id,
                                    List.of(), List.of(t1));
                            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

                            assertEquals(EnumsApi.OperationStatus.OK, osr.status);

                            long count = getCountUnfinishedTasks(execContextForTest);
                            assertEquals(1, count);


                            final TaskApiData.TaskWithContext t1211 = new TaskApiData.TaskWithContext(1211L, "1,2#1");
                            final TaskApiData.TaskWithContext t1221 = new TaskApiData.TaskWithContext(1221L, "1,2#2");
                            final TaskApiData.TaskWithContext t1231 = new TaskApiData.TaskWithContext(1231L, "1,2#3");
                            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id,
                                    List.of(1L), List.of(t1211, t1221, t1231));

                            final TaskApiData.TaskWithContext t1212 = new TaskApiData.TaskWithContext(1212L, "1,2#1");
                            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id,
                                    List.of(1211L), List.of(t1212));
                            final TaskApiData.TaskWithContext t1213 = new TaskApiData.TaskWithContext(1213L, "1,2#1");
                            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id,
                                    List.of(1212L), List.of(t1213));

                            final TaskApiData.TaskWithContext t1222 = new TaskApiData.TaskWithContext(1222L, "1,2#1");
                            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id,
                                    List.of(1221L), List.of(t1222));
                            final TaskApiData.TaskWithContext t1223 = new TaskApiData.TaskWithContext(1223L, "1,2#1");
                            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id,
                                    List.of(1222L), List.of(t1223));

                            final TaskApiData.TaskWithContext t1232 = new TaskApiData.TaskWithContext(1232L, "1,2#1");
                            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id,
                                    List.of(1231L), List.of(t1232));
                            final TaskApiData.TaskWithContext t1233 = new TaskApiData.TaskWithContext(1233L, "1,2#1");
                            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id,
                                    List.of(1232L), List.of(t1233));

                            // 999L is mh.finish task
                            final TaskApiData.TaskWithContext t999 = new TaskApiData.TaskWithContext(999L, Consts.TOP_LEVEL_CONTEXT_ID);
                            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id,
                                    List.of(1L, 1213L, 1223L, 1233L), List.of(t999));

                            assertEquals(EnumsApi.OperationStatus.OK, osr.status);
                            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

                            count = getCountUnfinishedTasks(execContextForTest);
                            assertEquals(11, count);

                            List<ExecContextData.TaskVertex> leafs = findLeafs(execContextForTest);


                            assertEquals(1, leafs.size());
                            assertEquals(t999.taskId, leafs.get(0).taskId);

                            Set<ExecContextData.TaskVertex> ancestors = findDirectAncestors(execContextForTest, to(t999));

                            assertEquals(4, ancestors.size());
                            assertTrue(ancestors.contains(to(t1213)));
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, t1213.taskId));

                            assertTrue(ancestors.contains(to(t1223)));
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, t1223.taskId));

                            assertTrue(ancestors.contains(to(t1233)));
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, t1233.taskId));


                            assertTrue(ancestors.contains(to(t1)));
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, t1.taskId));


                            Set<EnumsApi.TaskExecState> states;
                            txSupportForTestingService.updateGraphWithResettingAllChildrenTasksWithTx(
                                    execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId, t1.taskId);
                            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

                            // there is only 'NONE' exec state
                            states = execContextGraphTopLevelService.findAll(execContextForTest.execContextGraphId).stream()
                                    .map(o -> findTaskState(execContextForTest, o.taskId))
                                    .collect(Collectors.toSet());

                            assertEquals(1, states.size());
                            assertTrue(states.contains(EnumsApi.TaskExecState.NONE));

                            List<ExecContextData.TaskVertex> vertices = execContextGraphTopLevelService.findAllForAssigning(
                                    execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId);

                            assertEquals(1, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, vertices.get(0).taskId));
                            assertEquals(t1.taskId, vertices.get(0).taskId);

                            ExecContextOperationStatusWithTaskList status = txSupportForTestingService.updateTaskExecState(
                                    execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId,t1.taskId, EnumsApi.TaskExecState.OK, t1.taskContextId);

                            assertEquals(EnumsApi.OperationStatus.OK, status.status.status);
                            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

                            vertices = execContextGraphTopLevelService.findAllForAssigning(
                                    execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId);

                            assertEquals(3, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, vertices.get(0).taskId));
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, vertices.get(1).taskId));
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, vertices.get(2).taskId));
                            assertTrue(Set.of(t1211.taskId, t1221.taskId, t1231.taskId).contains(vertices.get(0).taskId));
                            assertTrue(Set.of(t1211.taskId, t1221.taskId, t1231.taskId).contains(vertices.get(1).taskId));
                            assertTrue(Set.of(t1211.taskId, t1221.taskId, t1231.taskId).contains(vertices.get(2).taskId));

                            status = txSupportForTestingService.updateTaskExecState(
                                    execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId, t1211.taskId, EnumsApi.TaskExecState.IN_PROGRESS, t1211.taskContextId);

                            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

                            vertices = txSupportForTestingService.findAllForAssigningWithTx(execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId);

                            assertEquals(2, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, vertices.get(0).taskId));
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, vertices.get(1).taskId));
                            assertTrue(Set.of(t1221.taskId, t1231.taskId).contains(vertices.get(0).taskId));
                            assertTrue(Set.of(t1221.taskId, t1231.taskId).contains(vertices.get(1).taskId));

                            status = txSupportForTestingService.updateTaskExecState(
                                    execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId, t1211.taskId, EnumsApi.TaskExecState.ERROR, t1211.taskContextId);

                            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

                            assertTrue(status.childrenTasks.stream().noneMatch(o->o.taskId.equals(t1211.taskId)));
                            assertTrue(status.childrenTasks.stream().anyMatch(o->o.taskId.equals(t1212.taskId)));
                            assertTrue(status.childrenTasks.stream().anyMatch(o->o.taskId.equals(t1213.taskId)));


                            vertices = txSupportForTestingService.findAllForAssigningWithTx(execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId);

                            assertEquals(2, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, vertices.get(0).taskId));
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, vertices.get(1).taskId));
                            assertTrue(Set.of(t1221.taskId, t1231.taskId).contains(vertices.get(0).taskId));
                            assertTrue(Set.of(t1221.taskId, t1231.taskId).contains(vertices.get(1).taskId));

                            assertEquals(EnumsApi.TaskExecState.SKIPPED, findTaskState(execContextForTest, t1212.taskId));
                            assertEquals(EnumsApi.TaskExecState.SKIPPED, findTaskState(execContextForTest, t1213.taskId));

                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, t1222.taskId));
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, t1223.taskId));
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, t1232.taskId));
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, t1233.taskId));

/*
                            status = txSupportForTestingService.updateTaskExecState(
                                    execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId,21L, EnumsApi.TaskExecState.OK, "123###1");

                            vertices = txSupportForTestingService.findAllForAssigningWithTx(execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId);

                            assertEquals(3, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, vertices.get(0).taskId));
                            assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(0).taskId));
                            assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(1).taskId));
                            assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(2).taskId));

                            // in production code this will never happened, i.e. switching from ERROR state to OK state
                            status = txSupportForTestingService.updateTaskExecState(
                                    execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId,22L, EnumsApi.TaskExecState.OK, "123###1");
                            // so we update children manually
                            txSupportForTestingService.setStateForAllChildrenTasksInternal(
                                    execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId, 22L, status, EnumsApi.TaskExecState.NONE);


                            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

                            vertices = txSupportForTestingService.findAllForAssigningWithTx(execContextForTest.execContextGraphId, execContextForTest.execContextTaskStateId);

                            assertEquals(6, vertices.size());
                            assertEquals(EnumsApi.TaskExecState.NONE, findTaskState(execContextForTest, vertices.get(0).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(0).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(1).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(2).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(3).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(4).taskId));
                            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(5).taskId));
*/
                            return null;
                        })));
    }

    private ExecContextData.TaskVertex to(TaskApiData.TaskWithContext taskWithContext) {
        return new ExecContextData.TaskVertex(taskWithContext.taskId, taskWithContext.taskContextId);
    }

}
