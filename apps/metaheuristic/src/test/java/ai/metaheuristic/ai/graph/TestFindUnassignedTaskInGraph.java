/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.commons.CommonConsts;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
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
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TestFindUnassignedTaskInGraph extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TestGraphService testGraphService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private ExecContextGraphService execContextGraphService;

        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test() {
        ExecContextApiData.UserExecContext context = new ExecContextApiData.UserExecContext(getAccount().id, getCompany().getUniqueId());
        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), context);
        setExecContextForTest(result.execContext);
        assertNotNull(getExecContextForTest());

        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, ()->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, ()->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId, this::evaluate)));
    }

    private void evaluate() {
        OperationStatusRest osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(), List.of(new TaskApiData.TaskWithContext(1L, CommonConsts.TOP_LEVEL_CONTEXT_ID)));
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));

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
                List.of(new TaskApiData.TaskWithContext(999L, CommonConsts.TOP_LEVEL_CONTEXT_ID)));

        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));

        count = preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest());
        assertEquals(10, count);

        List<ExecContextData.TaskVertex> leafs = testGraphService.findLeafs(getExecContextForTest());


        assertEquals(1, leafs.size());

        Set<ExecContextData.TaskVertex> ancestors = testGraphService.findDirectAncestors(getExecContextForTest(), leafs.get(0));

        assertEquals(9, ancestors.size());
        assertTrue(ancestors.contains(new ExecContextData.TaskVertex(1L, CommonConsts.TOP_LEVEL_CONTEXT_ID)));
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
            execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
            getExecContextForTest().execContextTaskStateId, 1L);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));

        // there is only 'NONE' exec state
        states = execContextGraphService.findAll(getExecContextForTest().execContextGraphId).stream()
                .map(o -> preparingSourceCodeService.findTaskState(getExecContextForTest(), o.taskId))
                .collect(Collectors.toSet());

        assertEquals(1, states.size());
        assertTrue(states.contains(EnumsApi.TaskExecState.NONE));

        List<ExecContextData.TaskVertex> vertices = execContextGraphService.findAllForAssigning(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, false);

        assertEquals(1, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
        assertEquals(Long.valueOf(1L), vertices.get(0).taskId);

        ExecContextOperationStatusWithTaskList status = txSupportForTestingService.updateTaskExecState(
            execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
            getExecContextForTest().execContextTaskStateId,1L, EnumsApi.TaskExecState.OK, CommonConsts.TOP_LEVEL_CONTEXT_ID);

        // !!! TODO 2020-10-06 need to rewrite with using real Tasks

        assertEquals(EnumsApi.OperationStatus.OK, status.status.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));

        vertices = execContextGraphService.findAllForAssigning(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, false);

        assertEquals(2, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
        assertTrue(Set.of(21L, 22L).contains(vertices.get(0).taskId));

        status = txSupportForTestingService.updateTaskExecState(
            execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
            getExecContextForTest().execContextTaskStateId,22L, EnumsApi.TaskExecState.IN_PROGRESS, "12#2");

        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));

        vertices = execContextGraphService.findAllForAssigning(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, true);

        assertEquals(1, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
        assertEquals(Long.valueOf(21L), vertices.get(0).taskId);

        txSupportForTestingService.updateTaskExecState(
            execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
            getExecContextForTest().execContextTaskStateId, 22L, EnumsApi.TaskExecState.ERROR, "12#2");

        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));

        vertices = execContextGraphService.findAllForAssigning(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, true);

        assertEquals(1, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
        assertEquals(Long.valueOf(21L), vertices.get(0).taskId);

        status = txSupportForTestingService.updateTaskExecState(
            execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
            getExecContextForTest().execContextTaskStateId,21L, EnumsApi.TaskExecState.OK, "123#1");

        vertices = execContextGraphService.findAllForAssigning(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, true);

        assertEquals(3, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(0).taskId));
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(1).taskId));
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(2).taskId));

        // In production a task never transitions from ERROR back to OK, but this test
        // exercises that hypothetical to verify the lower-level helper's contract.
        ExecContextImpl ec1 = getExecContextForTest();
        status = txSupportForTestingService.updateTaskExecState(
            execContextGraphService.getExecContextDAC(ec1.id, ec1.execContextGraphId), getExecContextForTest().execContextTaskStateId,22L, EnumsApi.TaskExecState.OK, "123#1");

        // Attempt to manually reset 22's children (321/322/323) from SKIPPED → NONE.
        // This is a no-op under the current contract of setStateForAllChildrenTasksInternal:
        // the helper now only propagates a state to descendants whose ALL parents are in
        // ERROR or SKIPPED. Since 22 is now OK (above), the gate filters out 321/322/323
        // and they stay SKIPPED. The call is left in place to document that the helper
        // CANNOT be used to undo a SKIPPED-cascade after-the-fact.
        ExecContextImpl ec = getExecContextForTest();
        txSupportForTestingService.setStateForAllChildrenTasksInternal(
            execContextGraphService.getExecContextDAC(ec.id, ec.execContextGraphId), getExecContextForTest().execContextTaskStateId, 22L, status, EnumsApi.TaskExecState.NONE);


        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));

        vertices = execContextGraphService.findAllForAssigning(getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, true);

        // Only 311/312/313 are NONE-and-assignable. 321/322/323 remain SKIPPED from the
        // earlier 22→ERROR cascade and are NOT re-eligible just because 22 was flipped
        // back to OK. This is the load-bearing invariant: a SKIPPED-cascade is sticky.
        assertEquals(3, vertices.size());
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), vertices.get(0).taskId));
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(0).taskId));
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(1).taskId));
        assertTrue(Set.of(311L, 312L, 313L).contains(vertices.get(2).taskId));
    }

}
