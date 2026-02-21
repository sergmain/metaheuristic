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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
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
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that verifies correct SKIPPED propagation in a linear DAG when a task fails with ERROR.
 *
 * DAG structure: task1(read) -> task2(process) -> task3(store) -> task4(mh.finish)
 *
 * When task2 finishes with ERROR:
 * - task3 must be marked as SKIPPED (non-leaf descendant of errored task)
 * - task4 (mh.finish, leaf node) must NOT be marked as SKIPPED, it should remain
 *   assignable so the ExecContext can complete
 *
 * This tests the fix for the flaky bug where a race condition between
 * ChangeTaskStateToInitForChildrenTasksEvent and the graph update could cause
 * children of errored tasks to not be properly marked as SKIPPED.
 *
 * @see ai.metaheuristic.ai.dispatcher.task.TaskStateService#changeTaskStateToInitForChildrenTasksTxEvent
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles({"dispatcher"})
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestGraphSkipOnErrorWithMhFinish extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private TestGraphService testGraphService;
    @Autowired private ExecContextGraphService execContextGraphService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    /**
     * Test linear DAG: task1 -> task2 -> task3 -> task4(mh.finish)
     * When task2 is ERROR, task3 should be SKIPPED and task4(mh.finish) should remain NONE.
     */
    @Test
    public void testLinearDagWithErrorSkipsNonLeafButNotMhFinish() {
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        assertNotNull(getExecContextForTest());

        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId,
                                this::evaluateLinearDag)));
    }

    private void evaluateLinearDag() {
        // Build linear DAG: task1 -> task2 -> task3 -> task4(mh.finish)
        final TaskApiData.TaskWithContext t1 = new TaskApiData.TaskWithContext(1L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t2 = new TaskApiData.TaskWithContext(2L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t3 = new TaskApiData.TaskWithContext(3L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t4 = new TaskApiData.TaskWithContext(4L, Consts.TOP_LEVEL_CONTEXT_ID);

        OperationStatusRest osr;

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(), List.of(t1));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(1L), List.of(t2));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(2L), List.of(t3));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(3L), List.of(t4));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Verify initial state
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 1L));
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 2L));
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 3L));
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 4L));

        // Verify task4 is a leaf
        List<ExecContextData.TaskVertex> leafs = testGraphService.findLeafs(getExecContextForTest());
        assertEquals(1, leafs.size());
        assertEquals(4L, leafs.get(0).taskId);

        // Set task1 to OK
        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 1L, EnumsApi.TaskExecState.OK, t1.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Set task2 to ERROR
        ExecContextOperationStatusWithTaskList status = txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 2L, EnumsApi.TaskExecState.ERROR, t2.taskContextId);
        assertEquals(EnumsApi.OperationStatus.OK, status.status.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // task3 must be SKIPPED (non-leaf descendant of errored task)
        assertEquals(EnumsApi.TaskExecState.OK, preparingSourceCodeService.findTaskState(getExecContextForTest(), 1L));
        assertEquals(EnumsApi.TaskExecState.ERROR, preparingSourceCodeService.findTaskState(getExecContextForTest(), 2L));
        assertEquals(EnumsApi.TaskExecState.SKIPPED, preparingSourceCodeService.findTaskState(getExecContextForTest(), 3L));

        // task4 (mh.finish/leaf) must NOT be SKIPPED
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 4L));

        // task3 should be in childrenTasks as SKIPPED
        assertTrue(status.childrenTasks.stream().anyMatch(o -> o.taskId.equals(3L)),
                "task3 should be in childrenTasks as SKIPPED");
        // task4 should NOT be in childrenTasks (leaf node excluded)
        assertTrue(status.childrenTasks.stream().noneMatch(o -> o.taskId.equals(4L)),
                "task4 (mh.finish/leaf) should NOT be in childrenTasks");

        // mh.finish task should be assignable
        List<ExecContextData.TaskVertex> vertices = execContextGraphService.findAllForAssigning(
                getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, false);
        assertEquals(1, vertices.size());
        assertEquals(4L, vertices.get(0).taskId);

        // Only mh.finish is unfinished
        long count = preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest());
        assertEquals(1, count);
    }

    /**
     * Test branching DAG where one branch errors:
     *
     *         +-> task2a -> task3a -+
     * task1 --+                     +--> taskFinish(mh.finish)
     *         +-> task2b -> task3b -+
     *
     * When task2a errors, task3a should be SKIPPED but task2b/task3b should continue.
     */
    @Test
    public void testBranchingDagWithErrorInOneBranch() {
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        assertNotNull(getExecContextForTest());

        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId,
                                this::evaluateBranchingDag)));
    }

    private void evaluateBranchingDag() {
        final TaskApiData.TaskWithContext t1 = new TaskApiData.TaskWithContext(1L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t2a = new TaskApiData.TaskWithContext(21L, "1,2#1");
        final TaskApiData.TaskWithContext t2b = new TaskApiData.TaskWithContext(22L, "1,2#2");
        final TaskApiData.TaskWithContext t3a = new TaskApiData.TaskWithContext(31L, "1,2#1");
        final TaskApiData.TaskWithContext t3b = new TaskApiData.TaskWithContext(32L, "1,2#2");
        final TaskApiData.TaskWithContext tFinish = new TaskApiData.TaskWithContext(99L, Consts.TOP_LEVEL_CONTEXT_ID);

        OperationStatusRest osr;

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(), List.of(t1));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(1L), List.of(t2a, t2b));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(21L), List.of(t3a));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(22L), List.of(t3b));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(1L, 31L, 32L), List.of(tFinish));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        assertEquals(6, preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest()));

        // Set task1 to OK
        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 1L, EnumsApi.TaskExecState.OK, t1.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Set task2a to ERROR (branch A fails)
        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 21L, EnumsApi.TaskExecState.ERROR, t2a.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // task3a should be SKIPPED
        assertEquals(EnumsApi.TaskExecState.SKIPPED, preparingSourceCodeService.findTaskState(getExecContextForTest(), 31L));

        // Branch B unaffected
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 22L));
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 32L));

        // mh.finish should be NONE (branch B not yet complete)
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 99L));

        // Complete branch B
        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 22L, EnumsApi.TaskExecState.OK, t2b.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 32L, EnumsApi.TaskExecState.OK, t3b.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // mh.finish should now be assignable
        List<ExecContextData.TaskVertex> vertices = execContextGraphService.findAllForAssigning(
                getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, false);
        assertEquals(1, vertices.size());
        assertEquals(99L, vertices.get(0).taskId);

        long count = preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest());
        assertEquals(1, count);
    }
}
