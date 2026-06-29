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

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.task.TaskResetService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.MhInternalTaskPipelineRunner;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.EnumsApi;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard for the duplicate-branch / orphaned-task bug after reset of a
 * subprocess wrapper task. The bug itself was fixed across multiple commits
 * (most relevantly: "set Task.execState=SKIPPED when task deleted from DAG,
 * deregister from queue" and "remove orphan task entries from
 * ExecContextVariableState when tasks deleted from DAG during reset/re-execution").
 *
 * <h2>Scenario</h2>
 * <ol>
 *   <li><b>Phase 1</b> — first run. {@code checkObjectives} (external) produces
 *       {@code hasObjectives=false}; {@code nopObjectivesWrapper} (internal,
 *       mh.nop) creates a {@code nopObjectives} child whose condition gates on
 *       hasObjectives — evaluates to false, so the child is SKIPPED. ExecContext
 *       reaches FINISHED.</li>
 *   <li><b>Phase 2</b> — reset everything back to NONE via the production
 *       {@code TaskResetService.resetTaskAndExecContext(...)} path. The
 *       invariant the recent fix establishes: any task that was in the DAG before
 *       reset but is NOT in the DAG after reset must have
 *       {@code execState=SKIPPED} in the DB (so the async internal-function
 *       cascade can't pick it up).</li>
 *   <li><b>Phase 3</b> — re-execute. With {@code hasObjectives=false} again, the
 *       resulting DAG must have the same number of tasks as after Phase 1. If
 *       the bug returns, the wrapper's old dynamically-created child remains in
 *       the DAG AND a new one is added — a duplicate branch.</li>
 * </ol>
 *
 * <h2>Why this test used to be flaky</h2>
 * The original setup drove execution through {@code TaskProviderTopLevelService.findTask}
 * on the test thread. That path publishes {@code InputVariablesInitedEvent} which
 * an {@code @Async @EventListener} consumes on a different thread, racing the
 * test's subsequent {@code storeExecResultWithTx} commit and surfacing as
 * {@code ObjectOptimisticLockingFailureException}. This rewrite uses
 * {@link MhInternalTaskPipelineRunner} — which transitions external tasks
 * directly to IN_PROGRESS and stores results on the test thread, bypassing the
 * async-event race entirely — so what's tested is the bug invariant, not the
 * test harness's tolerance of concurrent commits.
 *
 * @author Sergio Lissner
 * Date: 3/7/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TestDuplicateBranchAfterReset extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskRepositoryForTest taskRepositoryForTest;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private ExecContextGraphService execContextGraphService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private TaskResetService taskResetService;
    @Autowired private MhInternalTaskPipelineRunner pipelineRunner;

    @Override
    @SneakyThrows
        public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/source-code-for-duplicate-branch-test.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @AfterEach
    public void afterTest() {
        System.out.println("Finished TestDuplicateBranchAfterReset.afterTest()");
        ExecContextSyncService.getWithSyncNullable(getExecContextForTest().id,
            () -> txSupportForTestingService.deleteByExecContextId(getExecContextForTest().id));
    }

    /**
     * checkObjectives produces hasObjectives. We hand it "false" so the gated
     * nopObjectives subprocess is SKIPPED — that's the path the bug-repro requires.
     * function-02:1.1 (evaluateObjective) is only created when hasObjectives is true,
     * so it never runs in this scenario.
     */
    private static Map<String, String> syntheticData(String functionCode, Object tpy) {
        return switch (functionCode) {
            case "function-01:1.1" -> Map.of("hasObjectives", "false");
            default -> throw new IllegalStateException(
                    "Unexpected external function call for '" + functionCode +
                    "' — only function-01:1.1 (checkObjectives) is expected in this scenario");
        };
    }

    @SneakyThrows
    @Test
    public void test() {
        // ====== Setup: produce tasks and start ExecContext ======
        System.out.println("=== Setup: produce tasks ===");
        step_0_0_produce_tasks_and_start();

        // Record the root task id — TaskResetService needs it to find the descendants
        // subtree to reset.
        List<ExecContextData.TaskVertex> rootVertices = execContextGraphService.findAllRootVertices(
                getExecContextForTest().execContextGraphId);
        assertEquals(1, rootVertices.size(), "DAG should have exactly one root (checkObjectives)");
        Long resetTaskId = rootVertices.getFirst().taskId;

        // ====== Phase 1: first run ======
        System.out.println("=== Phase 1: first run (hasObjectives=false) ===");
        pipelineRunner.runPipelineToCompletion(getExecContextForTest().id,
                (fc, tpy) -> syntheticData(fc, tpy), 20);

        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));
        assertEquals(EnumsApi.ExecContextState.FINISHED.code, getExecContextForTest().getState(),
                "Phase 1 should reach FINISHED");

        List<ExecContextData.TaskVertex> phase1Vertices = execContextGraphService.findAll(
                getExecContextForTest().execContextGraphId);
        int taskCountAfterPhase1 = phase1Vertices.size();
        System.out.println("Task count after Phase 1: " + taskCountAfterPhase1);
        for (ExecContextData.TaskVertex v : phase1Vertices) {
            TaskImpl t = taskRepository.findByIdReadOnly(v.taskId);
            String state = t != null ? EnumsApi.TaskExecState.from(t.execState).toString() : "NOT_FOUND";
            System.out.println("  Task#" + v.taskId + " ctx=" + v.taskContextId + " state=" + state);
        }

        // ====== Phase 2: reset all tasks ======
        System.out.println("=== Phase 2: reset all tasks ===");
        taskResetService.resetTaskAndExecContext(getExecContextForTest().id, resetTaskId);

        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));
        List<ExecContextData.TaskVertex> phase2Vertices = execContextGraphService.findAll(
                getExecContextForTest().execContextGraphId);
        int taskCountAfterReset = phase2Vertices.size();
        System.out.println("Task count after reset: " + taskCountAfterReset);

        // Graph structure must be preserved across reset (only states change, not vertices).
        assertEquals(taskCountAfterPhase1, taskCountAfterReset,
                "Task count in DAG should remain the same after reset — only states change, " +
                        "not graph structure");

        // Invariant: tasks in DB but NOT in the DAG must have execState=SKIPPED.
        // This guards the "set Task.execState=SKIPPED when task deleted from DAG" fix —
        // without it, async internal-function processing could pick up a stale orphan task.
        Set<Long> graphTaskIdsAfterReset = phase2Vertices.stream()
                .map(v -> v.taskId).collect(Collectors.toSet());
        for (TaskImpl dbTask : taskRepositoryForTest.findByExecContextIdAsList(getExecContextForTest().id)) {
            if (!graphTaskIdsAfterReset.contains(dbTask.id)) {
                assertEquals(EnumsApi.TaskExecState.SKIPPED.value, dbTask.execState,
                        "Task#" + dbTask.id + " was removed from DAG during reset but execState is " +
                                EnumsApi.TaskExecState.from(dbTask.execState) +
                                " instead of SKIPPED — async processing could pick it up");
                System.out.println("  VERIFIED: orphan Task#" + dbTask.id + " has execState=SKIPPED");
            }
        }

        // ====== Phase 3: re-execute — verify no duplicate branch ======
        System.out.println("=== Phase 3: re-execute after reset ===");
        assertEquals(EnumsApi.ExecContextState.STARTED.code, getExecContextForTest().getState(),
                "ExecContext should be STARTED after reset");

        pipelineRunner.runPipelineToCompletion(getExecContextForTest().id,
                (fc, tpy) -> syntheticData(fc, tpy), 20);

        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));
        assertEquals(EnumsApi.ExecContextState.FINISHED.code, getExecContextForTest().getState(),
                "Phase 3 should reach FINISHED");

        List<ExecContextData.TaskVertex> phase3Vertices = execContextGraphService.findAll(
                getExecContextForTest().execContextGraphId);
        int taskCountAfterPhase3 = phase3Vertices.size();
        System.out.println("Task count after Phase 3: " + taskCountAfterPhase3);
        for (ExecContextData.TaskVertex v : phase3Vertices) {
            TaskImpl t = taskRepository.findByIdReadOnly(v.taskId);
            String state = t != null ? EnumsApi.TaskExecState.from(t.execState).toString() : "NOT_FOUND";
            System.out.println("  Task#" + v.taskId + " ctx=" + v.taskContextId + " state=" + state);
        }

        // Core invariant: re-execution with the same condition produces the same DAG shape.
        // If the bug returns, the old dynamically-created subprocess child remains in the
        // DAG AND a new one is added — taskCountAfterPhase3 > taskCountAfterPhase1.
        assertEquals(taskCountAfterPhase1, taskCountAfterPhase3,
                "DUPLICATE BRANCH REGRESSION: DAG has " + taskCountAfterPhase3 +
                        " tasks after Phase 3 but should have " + taskCountAfterPhase1 +
                        " (same as Phase 1). The old dynamically-created subprocess child was not " +
                        "removed before re-creating it.");
    }
}
