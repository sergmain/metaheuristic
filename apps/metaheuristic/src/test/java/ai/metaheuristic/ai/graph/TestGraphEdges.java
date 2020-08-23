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

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 7/16/2019
 * Time: 8:53 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
public class TestGraphEdges extends PreparingSourceCode {

    @Autowired
    public ExecContextCache execContextCache;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void test() {

        ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorService.createExecContext(sourceCode, company.getUniqueId());
        execContextForTest = result.execContext;

        assertNotNull(execContextForTest);

        OperationStatusRest osr = execContextGraphTopLevelService.addNewTasksToGraph(execContextForTest.id, List.of(),
                List.of(new TaskApiData.TaskWithContext(1L, "123###1")));
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        assertEquals(EnumsApi.OperationStatus.OK, osr.status);

        long count = execContextService.getCountUnfinishedTasks(execContextForTest);
        assertEquals(1, count);


        osr = execContextGraphTopLevelService.addNewTasksToGraph(execContextForTest.id,List.of(1L),
                List.of(new TaskApiData.TaskWithContext(21L, "123###1"),
                        new TaskApiData.TaskWithContext(22L, "123###1"),
                        new TaskApiData.TaskWithContext(23L, "123###1")));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        List<ExecContextData.TaskVertex> leafs = execContextGraphTopLevelService.findLeafs(execContextForTest);

        assertEquals(3, leafs.size());

        assertTrue(leafs.contains(new ExecContextData.TaskVertex(21L, 21L, EnumsApi.TaskExecState.NONE, "123###1")));
        assertTrue(leafs.contains(new ExecContextData.TaskVertex(22L, 22L, EnumsApi.TaskExecState.NONE, "123###1")));
        assertTrue(leafs.contains(new ExecContextData.TaskVertex(23L, 23L, EnumsApi.TaskExecState.NONE, "123###1")));

        osr = execContextGraphTopLevelService.addNewTasksToGraph( execContextForTest.id,List.of(21L),
                List.of(new TaskApiData.TaskWithContext(311L, "123###1"),
                        new TaskApiData.TaskWithContext(312L, "123###1"),
                        new TaskApiData.TaskWithContext(313L, "123###1")));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.id));

        Set<ExecContextData.TaskVertex> descendands = execContextGraphTopLevelService.findDescendants(execContextForTest, 1L);
        assertEquals(6, descendands.size());

        descendands = execContextGraphTopLevelService.findDescendants(execContextForTest, 21L);
        assertEquals(3, descendands.size());

        leafs = execContextGraphTopLevelService.findLeafs(execContextForTest);
        assertEquals(5, leafs.size());
    }
}
