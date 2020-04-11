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

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
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

import static ai.metaheuristic.ai.dispatcher.data.ExecContextData.TaskVertex;
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

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void test() {

        ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorService.createExecContext(sourceCode);
        execContextForTest = result.execContext;

        assertNotNull(execContextForTest);

        OperationStatusRest osr = execContextGraphTopLevelService.addNewTasksToGraph(execContextForTest.id, List.of(), List.of(1L));
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        assertEquals(EnumsApi.OperationStatus.OK, osr.status);

        long count = execContextService.getCountUnfinishedTasks(execContextForTest);
        assertEquals(1, count);


        osr = execContextGraphTopLevelService.addNewTasksToGraph(execContextForTest.id,List.of(1L), List.of(21L, 22L));

        osr = execContextGraphTopLevelService.addNewTasksToGraph(execContextForTest.id,List.of(21L), List.of(311L, 312L, 313L));

        osr = execContextGraphTopLevelService.addNewTasksToGraph(execContextForTest.id,List.of(22L), List.of(321L, 322L, 323L));

        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        count = execContextService.getCountUnfinishedTasks(execContextForTest);
        assertEquals(9, count);

        List<TaskVertex> leafs = execContextGraphTopLevelService.findLeafs(execContextForTest);

        assertEquals(6, leafs.size());
        assertTrue(leafs.contains(new TaskVertex(311L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(312L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(313L, EnumsApi.TaskExecState.NONE)));

        assertTrue(leafs.contains(new TaskVertex(321L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(322L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(323L, EnumsApi.TaskExecState.NONE)));

/*
        // value of id field doesn't matter because isn't included in "@EqualsAndHashCode"
        assertTrue(leafs.contains(new TaskVertex(1L, 311L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(1L, 312L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(1L, 313L, EnumsApi.TaskExecState.NONE)));

        assertTrue(leafs.contains(new TaskVertex(1L, 321L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(1L, 322L, EnumsApi.TaskExecState.NONE)));
        assertTrue(leafs.contains(new TaskVertex(1L, 323L, EnumsApi.TaskExecState.NONE)));

*/

        Set<EnumsApi.TaskExecState> states;
        execContextGraphTopLevelService.updateGraphWithResettingAllChildrenTasks(execContextForTest.id,1L);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        // there is only 'NONE' exec state
        states = execContextGraphTopLevelService.findAll(execContextForTest).stream().map(o -> o.execState).collect(Collectors.toSet());
        assertEquals(1, states.size());
        assertTrue(states.contains(EnumsApi.TaskExecState.NONE));

        List<TaskVertex> vertices = execContextGraphTopLevelService.findAllForAssigning(
                Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

        assertEquals(1, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
        assertEquals(Long.valueOf(1L), vertices.get(0).taskId);

        OperationStatusRest status = execContextGraphTopLevelService.updateTaskExecStateByExecContextId(execContextForTest.id,1L, EnumsApi.TaskExecState.OK.value);

        assertEquals(EnumsApi.OperationStatus.OK, status.status);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        vertices = execContextGraphTopLevelService.findAllForAssigning(
                Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

        assertEquals(2, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
        assertTrue(Set.of(21L, 22L).contains(vertices.get(0).taskId));

        status = execContextGraphTopLevelService.updateTaskExecStateByExecContextId(execContextForTest.id,22L, EnumsApi.TaskExecState.IN_PROGRESS.value);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        vertices = execContextGraphTopLevelService.findAllForAssigning(
                Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

        assertEquals(1, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
        assertEquals(Long.valueOf(21L), vertices.get(0).taskId);


        status = execContextGraphTopLevelService.updateTaskExecStateByExecContextId(execContextForTest.id,22L, EnumsApi.TaskExecState.BROKEN.value);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        vertices = execContextGraphTopLevelService.findAllForAssigning(Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

        assertEquals(1, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
        assertEquals(Long.valueOf(21L), vertices.get(0).taskId);

        status = execContextGraphTopLevelService.updateTaskExecStateByExecContextId(execContextForTest.id,21L, EnumsApi.TaskExecState.OK.value);

        vertices = execContextGraphTopLevelService.findAllForAssigning(Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

        assertEquals(3, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, vertices.get(0).execState);
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(0).taskId));
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(1).taskId));
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(2).taskId));

        status = execContextGraphTopLevelService.updateTaskExecStateByExecContextId(execContextForTest.id,22L, EnumsApi.TaskExecState.OK.value);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));


        vertices = execContextGraphTopLevelService.findAllForAssigning(
                Objects.requireNonNull(execContextRepository.findByIdForUpdate(execContextForTest.id)));

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
