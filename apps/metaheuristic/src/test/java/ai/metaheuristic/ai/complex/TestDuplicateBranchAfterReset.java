/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.complex;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextFSM;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.repositories.VariableRepository;
import ai.metaheuristic.ai.dispatcher.task.*;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ch.qos.logback.classic.LoggerContext;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduces the duplicate branch / leftover task bug when a wrapper task (mh.nop)
 * with subProcesses is reset and re-executed.
 *
 * Scenario from MHDG-RG:
 * 1. First run: check-objectives produces hasObjectives=false, nop-objectives-wrapper
 *    creates nop-objectives child (SKIPPED because condition is false). ExecContext finishes.
 * 2. Reset: all tasks reset to NONE (simulating storeObjectiveAndResetTask flow).
 * 3. Second run: nop-objectives-wrapper re-executes processSubProcesses.
 *    BUG: a new nop-objectives task is created but the OLD one remains in the DAG as leftover.
 *    The test verifies that after Phase 3, the DAG contains leftover tasks that should
 *    have been removed during the reset.
 *
 * @author Sergio Lissner
 * Date: 3/7/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TestDuplicateBranchAfterReset extends PreparingSourceCode {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @BeforeAll
    static void setSystemProperties() {
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @AfterAll
    static void cleanupLogging() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskRepositoryForTest taskRepositoryForTest;
    @Autowired private ExecContextTaskStateService execContextTaskStateService;
    @Autowired private TaskFinishingTopLevelService taskFinishingTopLevelService;
    @Autowired private TaskFinishingTxService taskFinishingTxService;
    @Autowired private ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;
    @Autowired private TaskVariableTopLevelService taskVariableTopLevelService;
    @Autowired private VariableTxService variableService;
    @Autowired private VariableRepository variableRepository;
    @Autowired private ExecContextFSM execContextFSM;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private ExecContextGraphService execContextGraphService;
    @Autowired private ExecContextCache execContextCache;

    @Override
    @SneakyThrows
    public String getSourceCodeYamlAsString() {
        return IOUtils.resourceToString("/source_code/yaml/source-code-for-duplicate-branch-test.yaml", StandardCharsets.UTF_8);
    }

    @Override
    @SneakyThrows
    public void step_0_0_produceTasks() {
        System.out.println("start produceTasksForTest()");
        preparingSourceCodeService.produceTasksForTest(getSourceCodeYamlAsString(), preparingSourceCodeData);

        List<Object[]> tasks = taskRepositoryForTest.findByExecContextId(getExecContextForTest().getId());

        assertNotNull(getExecContextForTest());
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        // Allow async task state propagation to complete before verifying graph
        Thread.sleep(2_000);

        System.out.println("start verifyGraphIntegrity()");
        verifyGraphIntegrity();

        System.out.println("start taskProviderService.findTask()");
        DispatcherCommParamsYaml.AssignedTask simpleTask0 =
                taskProviderTopLevelService.findTask(getCore1().getId(), false);

        assertNull(simpleTask0);
    }

    @AfterEach
    public void afterTest() {
        System.out.println("Finished TestDuplicateBranchAfterReset.afterTest()");
        ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
            () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
    }

    @SneakyThrows
    @Test
    public void test() {
        System.out.println("=== Phase 1: Produce tasks and start ExecContext ===");
        step_0_0_produce_tasks_and_start();

        PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds = preparingSourceCodeService.step_1_0_init_session_id(preparingCodeData.processor.getId());
        preparingSourceCodeService.step_1_1_register_function_statuses(processorIdAndCoreIds, preparingSourceCodeData, preparingCodeData);
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());

        // Record the initial task count before executing anything
        List<ExecContextData.TaskVertex> initialVertices = execContextGraphService.findAll(getExecContextForTest().execContextGraphId);
        int initialTaskCount = initialVertices.size();
        System.out.println("Initial task count in DAG: " + initialTaskCount);
        for (ExecContextData.TaskVertex v : initialVertices) {
            System.out.println("  Task#" + v.taskId + " ctx=" + v.taskContextId);
        }

        // === Phase 1: Execute check-objectives (function-01:1.1) ===
        // This produces hasObjectives output — we set it to "false" so nop-objectives gets SKIPPED
        System.out.println("=== Executing check-objectives (function-01:1.1) ===");
        step_CheckObjectives(processorIdAndCoreIds);

        // Wait for internal functions (mh.nop wrapper and mh.nop conditional) to execute
        System.out.println("=== Waiting for internal functions to process ===");
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());
        Thread.sleep(3_000);
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());
        Thread.sleep(2_000);

        // After Phase 1, the ExecContext should be FINISHED (condition was false, nop-objectives SKIPPED,
        // no evaluate-objective created, mh.finish completes)
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));

        List<ExecContextData.TaskVertex> phase1Vertices = execContextGraphService.findAll(getExecContextForTest().execContextGraphId);
        int taskCountAfterPhase1 = phase1Vertices.size();
        System.out.println("Task count after Phase 1: " + taskCountAfterPhase1);
        for (ExecContextData.TaskVertex v : phase1Vertices) {
            TaskImpl task = taskRepository.findById(v.taskId).orElse(null);
            String state = task != null ? EnumsApi.TaskExecState.from(task.execState).toString() : "NOT_FOUND";
            System.out.println("  Task#" + v.taskId + " ctx=" + v.taskContextId + " state=" + state);
        }

        // Verify all tasks finished in Phase 1
        // The ExecContext should be FINISHED or at least all tasks should be in a finished state
        // (OK or SKIPPED)
        preparingSourceCodeService.findTaskForRegisteringInQueue(getExecContextForTest().id);
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));
        System.out.println("ExecContext state after Phase 1: " + EnumsApi.ExecContextState.toState(getExecContextForTest().getState()));

        // === Phase 2: Reset all tasks to NONE (simulating storeObjectiveAndResetTask flow) ===
        System.out.println("=== Phase 2: Resetting all tasks to NONE ===");
        resetAllTasksToNone();

        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));
        List<ExecContextData.TaskVertex> phase2Vertices = execContextGraphService.findAll(getExecContextForTest().execContextGraphId);
        int taskCountAfterReset = phase2Vertices.size();
        System.out.println("Task count after reset: " + taskCountAfterReset);
        for (ExecContextData.TaskVertex v : phase2Vertices) {
            TaskImpl task = taskRepository.findById(v.taskId).orElse(null);
            String state = task != null ? EnumsApi.TaskExecState.from(task.execState).toString() : "NOT_FOUND";
            System.out.println("  Task#" + v.taskId + " ctx=" + v.taskContextId + " state=" + state);
        }

        // Task count should be the same after reset — no tasks should have been removed yet
        assertEquals(taskCountAfterPhase1, taskCountAfterReset,
                "Task count in DAG should remain the same after reset (only states changed, not graph structure)");

        // === Phase 3: Re-execute — this triggers the bug ===
        System.out.println("=== Phase 3: Re-executing after reset ===");
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());

        // Re-execute check-objectives with hasObjectives=false again
        step_CheckObjectives(processorIdAndCoreIds);

        // Wait for internal functions to re-process
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());
        Thread.sleep(3_000);
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());
        Thread.sleep(2_000);

        // === Verify the bug: leftover tasks in the DAG ===
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));
        List<ExecContextData.TaskVertex> phase3Vertices = execContextGraphService.findAll(getExecContextForTest().execContextGraphId);
        int taskCountAfterPhase3 = phase3Vertices.size();
        System.out.println("Task count after Phase 3 (re-execution): " + taskCountAfterPhase3);
        for (ExecContextData.TaskVertex v : phase3Vertices) {
            TaskImpl task = taskRepository.findById(v.taskId).orElse(null);
            String state = task != null ? EnumsApi.TaskExecState.from(task.execState).toString() : "NOT_FOUND";
            TaskParamsYaml tpy = task != null ? task.getTaskParamsYaml() : null;
            String processCode = tpy != null ? tpy.task.processCode : "UNKNOWN";
            System.out.println("  Task#" + v.taskId + " ctx=" + v.taskContextId + " state=" + state + " process=" + processCode);
        }

        // BUG DETECTION: After re-execution, the DAG should have the same number of tasks as
        // after Phase 1 (since the condition is still false, the same structure should apply).
        // But the bug causes a NEW nopObjectives child to be created while the OLD one remains,
        // resulting in more tasks in the DAG — this is the duplicate branch.
        System.out.println("Expected task count (same as Phase 1): " + taskCountAfterPhase1);
        System.out.println("Actual task count after Phase 3: " + taskCountAfterPhase3);

        List<TaskImpl> dbTasks = taskRepositoryForTest.findByExecContextIdAsList(getExecContextForTest().id);
        int dbTaskCount = dbTasks.size();
        System.out.println("DB task count after Phase 3: " + dbTaskCount);
        System.out.println("Graph vertex count after Phase 3: " + taskCountAfterPhase3);
        for (TaskImpl t : dbTasks) {
            TaskParamsYaml tpy = t.getTaskParamsYaml();
            boolean inGraph = phase3Vertices.stream().anyMatch(v -> v.taskId.equals(t.id));
            System.out.println("  DB Task#" + t.id + " process=" + tpy.task.processCode
                    + " ctx=" + tpy.task.taskContextId + " state=" + EnumsApi.TaskExecState.from(t.execState)
                    + " inGraph=" + inGraph);
        }

        // This is the key assertion — after reset+re-execution with condition still false,
        // the DAG should have the same number of tasks as Phase 1.
        // If there are more tasks, the old dynamically-created child remained and a new one was added = duplicate branch.
        assertEquals(taskCountAfterPhase1, taskCountAfterPhase3,
                "BUG DETECTED: DAG has duplicate branch after reset+re-execution. " +
                "Expected " + taskCountAfterPhase1 + " tasks but found " + taskCountAfterPhase3 + ". " +
                "The old dynamically-created subprocess child was not removed during re-execution of the wrapper.");

        // Note: we intentionally do NOT call verifyGraphIntegrity() here because
        // removeOldSubProcessChildren removes old children from the graph but their Task records
        // remain in DB as orphans (cleaned by Scheduler asynchronously). This is by design.
    }

    /**
     * Execute the check-objectives task (function-01:1.1).
     * Sets hasObjectives output to "false" so nop-objectives will be SKIPPED.
     */
    private void step_CheckObjectives(PreparingData.ProcessorIdAndCoreIds processorIdAndCoreIds) {
        DispatcherCommParamsYaml.AssignedTask assignedTask =
                taskProviderTopLevelService.findTask(processorIdAndCoreIds.coreId1, false);
        assertNotNull(assignedTask);
        assertNotNull(assignedTask.getTaskId());

        TaskImpl task = taskRepository.findById(assignedTask.getTaskId()).orElse(null);
        assertNotNull(task);

        TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.UTILS.to(assignedTask.params);
        assertNotNull(taskParamsYaml.task.processCode);
        assertEquals("checkObjectives", taskParamsYaml.task.processCode);

        // Store hasObjectives=false — condition will evaluate to false, nop-objectives gets SKIPPED
        storeOutputVariable(assignedTask.taskId, "hasObjectives", "false", taskParamsYaml.task.processCode);
        storeExecResult(assignedTask);

        finishTask(task);
    }

    private void storeOutputVariable(Long taskId, String variableName, String variableData, String processCode) {
        Variable v = variableService.getVariable(variableName, processCode, getExecContextForTest());
        assertNotNull(v);

        Variable variable = variableRepository.findById(v.id).orElse(null);
        assertNotNull(variable);

        byte[] bytes = variableData.getBytes();
        VariableSyncService.getWithSyncVoidForCreation(variable.id,
                () -> variableService.updateWithTx(taskId, new ByteArrayInputStream(bytes), bytes.length, variable.id));

        v = variableService.getVariable(v.name, processCode, getExecContextForTest());
        assertNotNull(v);
        assertTrue(v.inited);
    }

    private void storeExecResult(DispatcherCommParamsYaml.AssignedTask assignedTask) {
        processScheduledTasks();

        ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r =
                new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
        r.setTaskId(assignedTask.getTaskId());
        r.setResult(getOKExecResult());

        ExecContextSyncService.getWithSyncVoid(getExecContextForTest().id,
                () -> execContextFSM.storeExecResultWithTx(r));

        TaskImpl task = taskRepository.findById(assignedTask.taskId).orElse(null);
        assertNotNull(task);

        TaskParamsYaml tpy = task.getTaskParamsYaml();
        for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
            Enums.UploadVariableStatus status = TaskSyncService.getWithSyncNullable(task.id,
                    () -> taskVariableTopLevelService.updateStatusOfVariable(task.id, output.id).status);
            assertEquals(Enums.UploadVariableStatus.OK, status);
        }

        processScheduledTasks();
    }

    private void finishTask(TaskImpl task) {
        taskFinishingTopLevelService.checkTaskCanBeFinished(task.id);
        processScheduledTasks();

        assertNotNull(getExecContextForTest().execContextTaskStateId);
        ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId,
                () -> execContextTaskStateService.transferStateFromTaskQueueToExecContext(
                        getExecContextForTest().id, getExecContextForTest().execContextTaskStateId));
        processScheduledTasks();
    }

    private void processScheduledTasks() {
        execContextTaskStateService.processUpdateTaskExecStatesInGraph();
        execContextVariableStateTopLevelService.processFlushing();
    }

    /**
     * Reset the first task (checkObjectives) and all its descendants to NONE state.
     * Uses TaskResetTxService.resetTaskAndExecContext which is the production reset logic.
     */
    private void resetAllTasksToNone() {
        ExecContextImpl ec = Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id));

        // Find the first task (root of the DAG) — checkObjectives (Task#1)
        List<ExecContextData.TaskVertex> rootVertices = execContextGraphService.findAllRootVertices(ec.execContextGraphId);
        assertFalse(rootVertices.isEmpty(), "DAG must have at least one root vertex");
        Long firstTaskId = rootVertices.getFirst().taskId;

        ExecContextSyncService.getWithSyncVoid(ec.id, () ->
            ExecContextTaskStateSyncService.getWithSyncVoid(ec.execContextTaskStateId, () ->
                txSupportForTestingService.resetTaskAndDescendants(ec.id, firstTaskId)));

        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id)));
    }

    private static String getOKExecResult() {
        FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec(
                new FunctionApiData.SystemExecResult("output-of-a-function", true, 0, "Everything is Ok."),
                null, null, null);
        return FunctionExecUtils.toString(functionExec);
    }
}
