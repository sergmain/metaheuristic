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
 * Tests for SKIPPED status propagation in the task DAG.
 *
 * Key behaviors verified:
 * 1. Linear cascade: SKIPPED at ctx="1" propagates to all non-mh.finish descendants
 * 2. Sub-process isolation: SKIPPED at ctx="1,2,N" only affects deeper context, main flow continues
 * 3. Leaf exclusion: mh.finish (leaf node, no outgoing edges) is never marked SKIPPED
 *
 * Design note: in the RG pipeline, the conditional mh.nop must be wrapped inside a plain mh.nop
 * (option 1) so that the SKIP happens at a deeper context level and does not cascade to sibling
 * processes in the main flow.
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
     * Test 1: Linear DAG, all tasks at same context.
     *
     * task1 -> task2 -> task3 -> task4 -> task5(mh.finish)
     *
     * task2 SKIPPED -> task3, task4 SKIPPED, task5 (mh.finish, leaf) stays NONE and assignable.
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
        final TaskApiData.TaskWithContext t1 = new TaskApiData.TaskWithContext(1L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t2 = new TaskApiData.TaskWithContext(2L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t3 = new TaskApiData.TaskWithContext(3L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t4 = new TaskApiData.TaskWithContext(4L, Consts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext t5 = new TaskApiData.TaskWithContext(5L, Consts.TOP_LEVEL_CONTEXT_ID);

        OperationStatusRest osr;

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(), List.of(t1));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(1L), List.of(t2));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(2L), List.of(t3));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(3L), List.of(t4));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(4L), List.of(t5));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Verify initial state
        for (long id = 1; id <= 5; id++) {
            assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), id));
        }

        // task1 OK
        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 1L, EnumsApi.TaskExecState.OK, t1.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // task2 SKIPPED
        ExecContextOperationStatusWithTaskList status = txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 2L, EnumsApi.TaskExecState.SKIPPED, t2.taskContextId);
        assertEquals(EnumsApi.OperationStatus.OK, status.status.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        assertEquals(EnumsApi.TaskExecState.OK, preparingSourceCodeService.findTaskState(getExecContextForTest(), 1L));
        assertEquals(EnumsApi.TaskExecState.SKIPPED, preparingSourceCodeService.findTaskState(getExecContextForTest(), 2L));
        assertEquals(EnumsApi.TaskExecState.SKIPPED, preparingSourceCodeService.findTaskState(getExecContextForTest(), 3L),
                "task3 (child of skipped task) must be SKIPPED");
        assertEquals(EnumsApi.TaskExecState.SKIPPED, preparingSourceCodeService.findTaskState(getExecContextForTest(), 4L),
                "task4 (grandchild of skipped task) must be SKIPPED");
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 5L),
                "task5 (mh.finish/leaf) must remain NONE");

        assertTrue(status.childrenTasks.stream().anyMatch(o -> o.taskId.equals(3L)));
        assertTrue(status.childrenTasks.stream().anyMatch(o -> o.taskId.equals(4L)));
        assertTrue(status.childrenTasks.stream().noneMatch(o -> o.taskId.equals(5L)),
                "task5 (mh.finish/leaf) should NOT be in childrenTasks");

        // mh.finish must be assignable
        List<ExecContextData.TaskVertex> vertices = execContextGraphService.findAllForAssigning(
                getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, false);
        assertEquals(1, vertices.size());
        assertEquals(5L, vertices.get(0).taskId);

        assertEquals(1, preparingSourceCodeService.getCountUnfinishedTasks(getExecContextForTest()));
    }

    /**
     * Test 2: Sub-process context isolation — models actual RG architecture (option 1).
     *
     * The conditional mh.nop is wrapped inside a plain mh.nop-wrapper.
     * The wrapper always executes at ctx="1,2" (batch-line-splitter sub-process).
     * Inside the wrapper, a deeper sub-process exists at ctx="1,2,N".
     * The conditional mh.nop at ctx="1,2,N" may be SKIPPED.
     * SKIPPED cascade only affects ctx="1,2,N" descendants.
     * read-req at ctx="1,2" continues normally.
     *
     * ctx="1,2":  check-obj -> nop-wrapper -> read-req -> decompose -> store-reqs
     *                              |
     * ctx="1,2,3":           nop-cond -> eval-obj -> store-obj-result
     *
     * nop-wrapper(OK) creates sub-process. nop-cond(SKIPPED at ctx="1,2,3").
     * eval-obj gets SKIPPED. read-req proceeds (parent nop-wrapper is OK).
     */
    @Test
    public void testSkippedPropagationWithSubProcessContext() {
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result = txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        assertNotNull(getExecContextForTest());

        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId,
                                this::evaluateSkippedPropagationWithSubProcessContext)));
    }

    private void evaluateSkippedPropagationWithSubProcessContext() {
        // batch-line-splitter sub-process level, ctx="1,2"
        final TaskApiData.TaskWithContext tCheckObj  = new TaskApiData.TaskWithContext(10L, "1,2");
        final TaskApiData.TaskWithContext tWrapper   = new TaskApiData.TaskWithContext(20L, "1,2");
        final TaskApiData.TaskWithContext tReadReq   = new TaskApiData.TaskWithContext(30L, "1,2");
        final TaskApiData.TaskWithContext tDecompose = new TaskApiData.TaskWithContext(40L, "1,2");
        // wrapper's sub-process, ctx="1,2,3"
        final TaskApiData.TaskWithContext tNopCond   = new TaskApiData.TaskWithContext(21L, "1,2,3");
        final TaskApiData.TaskWithContext tEvalObj   = new TaskApiData.TaskWithContext(22L, "1,2,3");
        final TaskApiData.TaskWithContext tStoreObj  = new TaskApiData.TaskWithContext(23L, "1,2,3");

        OperationStatusRest osr;

        // Build the graph at ctx="1,2" level: check-obj -> wrapper -> read-req -> decompose
        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(), List.of(tCheckObj));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(10L), List.of(tWrapper));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(20L), List.of(tReadReq));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(30L), List.of(tDecompose));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Build wrapper's sub-process at ctx="1,2,3": nop-cond -> eval-obj -> store-obj
        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(20L), List.of(tNopCond));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(21L), List.of(tEvalObj));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(22L), List.of(tStoreObj));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // Execute: check-obj OK, wrapper OK
        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 10L, EnumsApi.TaskExecState.OK, tCheckObj.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 20L, EnumsApi.TaskExecState.OK, tWrapper.taskContextId);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // nop-cond SKIPPED at ctx="1,2,3"
        ExecContextOperationStatusWithTaskList status = txSupportForTestingService.updateTaskExecState(
                execContextGraphService.getExecContextDAC(getExecContextForTest().id, getExecContextForTest().execContextGraphId),
                getExecContextForTest().execContextTaskStateId, 21L, EnumsApi.TaskExecState.SKIPPED, tNopCond.taskContextId);
        assertEquals(EnumsApi.OperationStatus.OK, status.status.status);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        // eval-obj must be SKIPPED (ctx="1,2,3", descendant of nop-cond, non-leaf)
        assertEquals(EnumsApi.TaskExecState.SKIPPED, preparingSourceCodeService.findTaskState(getExecContextForTest(), 22L),
                "eval-obj (ctx='1,2,3') must be SKIPPED");

        // read-req must NOT be SKIPPED — ctx="1,2", not a descendant of nop-cond in graph
        // (read-req's parent is wrapper(OK), not nop-cond)
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 30L),
                "read-req (ctx='1,2') must NOT be SKIPPED");

        // decompose must NOT be SKIPPED
        assertEquals(EnumsApi.TaskExecState.NONE, preparingSourceCodeService.findTaskState(getExecContextForTest(), 40L),
                "decompose (ctx='1,2') must NOT be SKIPPED");

        // read-req should be assignable (parent wrapper is OK)
        List<ExecContextData.TaskVertex> assignable = execContextGraphService.findAllForAssigning(
                getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, false);
        assertTrue(assignable.stream().anyMatch(v -> v.taskId == 30L),
                "read-req must be assignable (parent wrapper is OK)");
    }
}
