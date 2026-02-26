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
import ai.metaheuristic.ai.MhComplexTestConfig;
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
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that verifies SKIPPED status propagation to all children tasks in a sub-process.
 *
 * When a condition-gated task (mh.nop) evaluates to false, the task is marked SKIPPED.
 * All descendant tasks in its sub-process must also be marked SKIPPED.
 *
 * DAG structure simulating the objective processing block:
 *
 * task1(check-objectives) -> task2(mh.nop, condition gate) -> task3(call-cc) -> task4(store-result) -> task5(mh.finish)
 *
 * When task2 is SKIPPED (condition=false):
 * - task3 and task4 must be marked as SKIPPED (descendants of skipped task)
 * - task5 (mh.finish, leaf node) must NOT be marked as SKIPPED
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@Slf4j
@ActiveProfiles({"dispatcher", "h2", "test"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestGraphSkipPropagation extends PreparingSourceCode {


    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @BeforeAll
    static void setSystemProperties() {
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

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
     * Test linear DAG: task1 -> task2 -> task3 -> task4 -> task5(mh.finish)
     * When task2 is SKIPPED (condition gate), task3 and task4 should also be SKIPPED.
     * task5 (mh.finish/leaf) should remain NONE and be assignable.
     */
    @Test
    public void testSkippedPropagationToSubProcessTasks() {
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        assertNotNull(getExecContextForTest());

        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId,
                                this::evaluateSkippedPropagation)));
    }

    private void evaluateSkippedPropagation() {
        // Build linear DAG: task1 -> task2 -> task3 -> task4 -> task5(mh.finish)
        // All tasks in the same taskContextId to simulate sub-process without context increment
        final TaskApiData.TaskWithContext t1 = new TaskApiData.TaskWithContext(1L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t2 = new TaskApiData.TaskWithContext(2L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t3 = new TaskApiData.TaskWithContext(3L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t4 = new TaskApiData.TaskWithContext(4L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t5 = new TaskApiData.TaskWithContext(5L, Consts.TOP_LEVEL_CONTEXT_ID);

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

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(4L), List.of(t5));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Verify initial state - all NONE
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 1L));
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 2L));
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 3L));
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 4L));
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 5L));

        // Set task1 to OK (check-objectives completed)
        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 1L, EnumsApi.TaskExecState.OK, t1.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Set task2 to SKIPPED (mh.nop condition evaluated to false)
        ExecContextOperationStatusWithTaskList status = txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 2L, EnumsApi.TaskExecState.SKIPPED, t2.taskContextId);
        assertEquals(EnumsApi.OperationStatus.OK, status.status.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Verify states after SKIPPED propagation
        assertEquals(EnumsApi.TaskExecState.OK, preparingSourceCodeService.findTaskState(getExecContextForTest(), 1L));
        assertEquals(EnumsApi.TaskExecState.SKIPPED, preparingSourceCodeService.findTaskState(getExecContextForTest(), 2L));

        // task3 and task4 must be SKIPPED (descendants of skipped task, non-leaf)
        assertEquals(EnumsApi.TaskExecState.SKIPPED, preparingSourceCodeService.findTaskState(getExecContextForTest(), 3L),
                "Task 3 (child of skipped task) must be SKIPPED");
        assertEquals(EnumsApi.TaskExecState.SKIPPED, preparingSourceCodeService.findTaskState(getExecContextForTest(), 4L),
                "Task 4 (grandchild of skipped task) must be SKIPPED");

        // task5 (mh.finish/leaf) must NOT be SKIPPED
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 5L),
                "Task 5 (mh.finish/leaf) must remain NONE");

        // task3 and task4 should be in childrenTasks as SKIPPED
        assertTrue(status.childrenTasks.stream().anyMatch(o -> o.taskId.equals(3L)),
                "task3 should be in childrenTasks as SKIPPED");
        assertTrue(status.childrenTasks.stream().anyMatch(o -> o.taskId.equals(4L)),
                "task4 should be in childrenTasks as SKIPPED");
        // task5 should NOT be in childrenTasks (leaf node excluded)
        assertTrue(status.childrenTasks.stream().noneMatch(o -> o.taskId.equals(5L)),
                "task5 (mh.finish/leaf) should NOT be in childrenTasks");

        // mh.finish task should be assignable
        List<ExecContextData.TaskVertex> vertices = execContextGraphService.findAllForAssigning(
                getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, false);
        assertEquals(1, vertices.size());
        assertEquals(5L, vertices.get(0).taskId);

        // Only mh.finish is unfinished
        long count = preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest());
        assertEquals(1, count);
    }
}
