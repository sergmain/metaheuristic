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
import ai.metaheuristic.commons.CommonConsts;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that mh.finish (leaf vertex) is NOT assigned when any of its parent tasks
 * is in a non-terminal state such as PRE_INIT or ERROR_WITH_RECOVERY.
 *
 * Reproduces the production bug where:
 * 1. ExecContext completes successfully, mh.finish set to OK
 * 2. Objective submitted → task branch reset to NONE (including mh.finish)
 * 3. Reset task fails → ERROR_WITH_RECOVERY, its child stays PRE_INIT
 * 4. Bug: mh.finish was assigned and completed as OK despite PRE_INIT parent
 *
 * DAG structure:
 *   task1(OK) -> task2(OK) -> task3(ERROR_WITH_RECOVERY) -> task4(PRE_INIT) -> taskFinish(mh.finish)
 *
 * After task3 is set to ERROR_WITH_RECOVERY, task4 remains PRE_INIT.
 * mh.finish must NOT be returned by findAllForAssigning() because task4 is not in a finished state.
 *
 * @author Sergio Lissner
 * Date: 3/4/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
@Slf4j
public class TestMhFinishNotAssignedWithPreInitParent extends PreparingSourceCode {

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
        ai.metaheuristic.ai.MhShutdown.cleanUp();
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
        ai.metaheuristic.ai.MhShutdown.cleanUp();
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private ExecContextGraphService execContextGraphService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    /**
     * task1(OK) -> task2(OK) -> task3(ERROR_WITH_RECOVERY) -> task4(PRE_INIT) -> taskFinish(mh.finish, NONE)
     *
     * mh.finish must NOT be assignable because task4 is PRE_INIT (non-terminal).
     */
    @Test
    public void test_mhFinish_notAssigned_when_parent_is_PRE_INIT() {
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        assertNotNull(getExecContextForTest());

        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId,
                                this::evaluate_PRE_INIT)));
    }

    private void evaluate_PRE_INIT() {
        final TaskApiData.TaskWithContext t1 = new TaskApiData.TaskWithContext(1L, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t2 = new TaskApiData.TaskWithContext(2L, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t3 = new TaskApiData.TaskWithContext(3L, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t4 = new TaskApiData.TaskWithContext(4L, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext tFinish = new TaskApiData.TaskWithContext(59L, CommonConsts.TOP_LEVEL_CONTEXT_ID);

        OperationStatusRest osr;

        // Build linear DAG: t1 -> t2 -> t3 -> t4 -> tFinish
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

        // task4 added with PRE_INIT state (simulates task created but not yet initialized)
        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(3L), List.of(t4), EnumsApi.TaskExecState.PRE_INIT);
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id,
                List.of(4L), List.of(tFinish));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Set task1 and task2 to OK
        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 1L, EnumsApi.TaskExecState.OK, t1.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 2L, EnumsApi.TaskExecState.OK, t2.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Set task3 to ERROR_WITH_RECOVERY
        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 3L, EnumsApi.TaskExecState.ERROR_WITH_RECOVERY, t3.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Verify states
        assertEquals(EnumsApi.TaskExecState.OK, preparingSourceCodeService.findTaskState(getExecContextForTest(), 1L));
        assertEquals(EnumsApi.TaskExecState.OK, preparingSourceCodeService.findTaskState(getExecContextForTest(), 2L));
        assertEquals(EnumsApi.TaskExecState.ERROR_WITH_RECOVERY, preparingSourceCodeService.findTaskState(getExecContextForTest(), 3L));
        assertEquals(EnumsApi.TaskExecState.PRE_INIT, preparingSourceCodeService.findTaskState(getExecContextForTest(), 4L));
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 59L));

        // THE KEY ASSERTION: mh.finish must NOT be assignable
        // because task4 (its parent) is in PRE_INIT state which is not a finished state
        List<ExecContextData.TaskVertex> vertices = execContextGraphService.findAllForAssigning(
                getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, false);
        assertTrue(vertices.stream().noneMatch(v -> v.taskId.equals(59L)),
                "mh.finish (task#59) must NOT be assignable when parent task#4 is in PRE_INIT state");
    }

    /**
     * Same DAG but task4 is in ERROR_WITH_RECOVERY state.
     * mh.finish must NOT be assignable because ERROR_WITH_RECOVERY is transient.
     */
    @Test
    public void test_mhFinish_notAssigned_when_parent_is_ERROR_WITH_RECOVERY() {
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        assertNotNull(getExecContextForTest());

        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId,
                                this::evaluate_ERROR_WITH_RECOVERY)));
    }

    private void evaluate_ERROR_WITH_RECOVERY() {
        final TaskApiData.TaskWithContext t1 = new TaskApiData.TaskWithContext(1L, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t2 = new TaskApiData.TaskWithContext(2L, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t3 = new TaskApiData.TaskWithContext(3L, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext tFinish = new TaskApiData.TaskWithContext(59L, CommonConsts.TOP_LEVEL_CONTEXT_ID);

        OperationStatusRest osr;

        // Build linear DAG: t1 -> t2 -> t3 -> tFinish
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
                List.of(3L), List.of(tFinish));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Set task1 and task2 to OK
        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 1L, EnumsApi.TaskExecState.OK, t1.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 2L, EnumsApi.TaskExecState.OK, t2.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Set task3 to ERROR_WITH_RECOVERY — it's a direct parent of mh.finish
        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 3L, EnumsApi.TaskExecState.ERROR_WITH_RECOVERY, t3.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Verify states
        assertEquals(EnumsApi.TaskExecState.OK, preparingSourceCodeService.findTaskState(getExecContextForTest(), 1L));
        assertEquals(EnumsApi.TaskExecState.OK, preparingSourceCodeService.findTaskState(getExecContextForTest(), 2L));
        assertEquals(EnumsApi.TaskExecState.ERROR_WITH_RECOVERY, preparingSourceCodeService.findTaskState(getExecContextForTest(), 3L));
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 59L));

        // THE KEY ASSERTION: mh.finish must NOT be assignable
        // because task3 (its parent) is in ERROR_WITH_RECOVERY which is a transient, non-terminal state
        List<ExecContextData.TaskVertex> vertices = execContextGraphService.findAllForAssigning(
                getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, false);
        assertTrue(vertices.stream().noneMatch(v -> v.taskId.equals(59L)),
                "mh.finish (task#59) must NOT be assignable when parent task#3 is in ERROR_WITH_RECOVERY state");
    }
}
