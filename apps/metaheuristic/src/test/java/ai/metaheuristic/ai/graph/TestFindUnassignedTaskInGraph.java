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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
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
 * Date: 7/25/2019
 * Time: 3:50 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
public class TestFindUnassignedTaskInGraph extends PreparingSourceCode {

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

        ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorService.createExecContext(sourceCode, company.getUniqueId());
        execContextForTest = result.execContext;
        assertNotNull(execContextForTest);

        execContextSyncService.getWithSyncNullable(execContextForTest.id, () -> {


            OperationStatusRest osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id,
                    List.of(), List.of(new TaskApiData.TaskWithContext(1L, Consts.TOP_LEVEL_CONTEXT_ID)));
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            assertEquals(EnumsApi.OperationStatus.OK, osr.status);

            long count = execContextGraphTopLevelService.getCountUnfinishedTasks(execContextForTest);
            assertEquals(1, count);


            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id, List.of(1L),
                    List.of(new TaskApiData.TaskWithContext(21L, "12###1"), new TaskApiData.TaskWithContext(22L, "12###2")));

            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id, List.of(21L),
                    List.of(new TaskApiData.TaskWithContext(311L, "123###1"),
                            new TaskApiData.TaskWithContext(312L, "123###2"),
                            new TaskApiData.TaskWithContext(313L, "123###3")));

            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id, List.of(22L),
                    List.of(new TaskApiData.TaskWithContext(321L, "123###4"),
                            new TaskApiData.TaskWithContext(322L, "123###5"),
                            new TaskApiData.TaskWithContext(323L, "123###6")));

            // 999L is mh.finish task
            osr = txSupportForTestingService.addTasksToGraphWithTx(execContextForTest.id, List.of(1L, 21L, 22L, 311L, 312L, 313L, 321L, 322L, 323L),
                    List.of(new TaskApiData.TaskWithContext(999L, Consts.TOP_LEVEL_CONTEXT_ID)));

            assertEquals(EnumsApi.OperationStatus.OK, osr.status);
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            count = execContextGraphTopLevelService.getCountUnfinishedTasks(execContextForTest);
            assertEquals(10, count);

            List<ExecContextData.TaskVertex> leafs = execContextGraphTopLevelService.findLeafs(execContextForTest);


            assertEquals(1, leafs.size());

            Set<ExecContextData.TaskVertex> ancestors = execContextGraphTopLevelService.findDirectAncestors(execContextForTest, leafs.get(0));

            assertEquals(9, ancestors.size());
            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(1L, 1L, EnumsApi.TaskExecState.NONE, Consts.TOP_LEVEL_CONTEXT_ID)));
            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(21L, 21L, EnumsApi.TaskExecState.NONE, "12###1")));
            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(22L, 22L, EnumsApi.TaskExecState.NONE, "12###2")));

            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(311L, 311L, EnumsApi.TaskExecState.NONE, "123###1")));
            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(312L, 312L, EnumsApi.TaskExecState.NONE, "123###2")));
            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(313L, 313L, EnumsApi.TaskExecState.NONE, "123###3")));

            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(321L, 321L, EnumsApi.TaskExecState.NONE, "123###4")));
            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(322L, 322L, EnumsApi.TaskExecState.NONE, "123###5")));
            assertTrue(ancestors.contains(new ExecContextData.TaskVertex(323L, 323L, EnumsApi.TaskExecState.NONE, "123###6")));

            Set<EnumsApi.TaskExecState> states;
            txSupportForTestingService.updateGraphWithResettingAllChildrenTasksWithTx(execContextForTest.id, 1L);
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            // there is only 'NONE' exec state
            states = execContextGraphTopLevelService.findAll(execContextForTest).stream().map(o -> o.execState).collect(Collectors.toSet());
            assertEquals(1, states.size());
            assertTrue(states.contains(EnumsApi.TaskExecState.NONE));

            List<ExecContextData.TaskVertex> vertices = execContextGraphTopLevelService.findAllForAssigning(
                    Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

            assertEquals(1, vertices.size());
            assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
            assertEquals(Long.valueOf(1L), vertices.get(0).taskId);

            ExecContextOperationStatusWithTaskList status = txSupportForTestingService.updateTaskExecState(
                    execContextForTest.id,1L, EnumsApi.TaskExecState.OK, "123###1");

            // !!! TODO 2020-10-06 need to rewrite with using real Tasks

            assertEquals(EnumsApi.OperationStatus.OK, status.status.status);
            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            vertices = execContextGraphTopLevelService.findAllForAssigning(
                    Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

            assertEquals(2, vertices.size());
            assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
            assertTrue(Set.of(21L, 22L).contains(vertices.get(0).taskId));

            status = txSupportForTestingService.updateTaskExecState(
                    execContextForTest.id,22L, EnumsApi.TaskExecState.IN_PROGRESS, null);

            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            vertices = txSupportForTestingService.findAllForAssigningWithTx(
                    Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

            assertEquals(1, vertices.size());
            assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
            assertEquals(Long.valueOf(21L), vertices.get(0).taskId);

            txSupportForTestingService.updateTaskExecState(
                    execContextForTest.id, 22L, EnumsApi.TaskExecState.ERROR, null);

//            txSupportForTestingService.finishWithErrorWithTx(22L, "An error", null);

            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            vertices = txSupportForTestingService.findAllForAssigningWithTx(Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

            assertEquals(1, vertices.size());
            assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
            assertEquals(Long.valueOf(21L), vertices.get(0).taskId);

            status = txSupportForTestingService.updateTaskExecState(
                    execContextForTest.id,21L, EnumsApi.TaskExecState.OK, "123###1");

            vertices = txSupportForTestingService.findAllForAssigningWithTx(Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

            assertEquals(3, vertices.size());
            assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
            assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(0).taskId));
            assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(1).taskId));
            assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(2).taskId));

            // in production code this will never happened, i.e. switching from ERROR state to OK state
            status = txSupportForTestingService.updateTaskExecState(
                    execContextForTest.id,22L, EnumsApi.TaskExecState.OK, "123###1");
            // so we update children manually
            txSupportForTestingService.setStateForAllChildrenTasksInternal(execContextForTest.id, 22L, status, EnumsApi.TaskExecState.NONE);


            execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));

            vertices = txSupportForTestingService.findAllForAssigningWithTx(
                    Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

            assertEquals(6, vertices.size());
            assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(0).taskId));
            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(1).taskId));
            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(2).taskId));
            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(3).taskId));
            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(4).taskId));
            assertTrue(Set.of(311L, 312L, 313L, 321L, 322L, 323L).contains(vertices.get(5).taskId));
            return null;
        });
    }

}
