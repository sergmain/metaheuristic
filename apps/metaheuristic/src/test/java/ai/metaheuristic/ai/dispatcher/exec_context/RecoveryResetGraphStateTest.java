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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.events.ResetTasksWithErrorEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextTaskStateRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingTxService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxTestingService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What a recovery reset leaves behind in the TWO stores a Task's state lives in.
 *
 * <p>A Task's state is written in two places that are updated independently: {@code MH_TASK.EXEC_STATE}
 * in the DB, and the {@code states} map inside {@code ExecContextTaskState} — the "graph" state.
 * Assignment reads the GRAPH: {@link ExecContextGraphService#findAllForAssigning} returns only vertices
 * whose graph state is NONE or CHECK_CACHE. So a Task whose DB state says NONE but whose graph state
 * still says ERROR_WITH_RECOVERY is unassignable forever — the DB says "ready to run", the graph says
 * "not a candidate", and nothing reconciles the pair while the Task sits outside the task queue.
 *
 * <p>Observed in production on ExecContext #293 / Task #175450: DB {@code execState=0 (NONE)} while
 * {@code ExecContextTaskState.params} held {@code 175450: ERROR_WITH_RECOVERY} alongside
 * {@code triesWasMade: {175450: 0}}. The {@code triesWasMade} entry is the proof of which code ran:
 * {@code ExecContextTaskResettingService.resetTasksWithErrorForRecovery} is its only writer, and that
 * is the same method that moves the Task to NONE.
 *
 * <p>The two branches of that method are asymmetric, which is why only one of them was broken: the
 * ERROR branch goes through {@code TaskFinishingTxService.finishTaskAsError}, which publishes
 * {@code UpdateTaskExecStatesInExecContextTxEvent} and so carries its state into the graph. The NONE
 * branch calls {@code resetTask}, which publishes only {@code SetTaskExecStateInQueueTxEvent} — the
 * queue, not the graph. {@link #test_exhaustedTriesLeaveBothStoresOnError()} checks that claim about
 * the ERROR branch rather than taking it on trust.
 *
 * <p>Mechanics worth not re-deriving, from {@link RecoveryOrderingCharacterizationTest}:
 * <ul>
 * <li>Schedulers are inert in tests, so no reconciliation pass can move a state this test set by hand.
 *     The recovery entry point is therefore driven directly rather than awaited.</li>
 * <li>The root vertex must be in a finished state in the GRAPH. {@code findAllForAssigning}
 *     short-circuits on a NONE / CHECK_CACHE root and returns only that vertex, which would make the
 *     assignability assertions vacuous.</li>
 * <li>ERROR_WITH_RECOVERY cannot be written through {@code TaskExecStateService} — it throws for that
 *     state deliberately. {@code finishWithErrorWithTx} is the route production uses.</li>
 * </ul>
 *
 * @author Sergio Lissner
 * Date: 9/20/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
@Slf4j
public class RecoveryResetGraphStateTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TxTestingService txTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private ExecContextGraphService execContextGraphService;
    @Autowired private ExecContextTaskResettingTopLevelService execContextTaskResettingTopLevelService;
    @Autowired private ExecContextTaskStateRepository execContextTaskStateRepository;
    @Autowired private TaskFinishingTxService taskFinishingTxService;
    @Autowired private TaskRepository taskRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    /**
     * Drives the real recovery entry point over a Task that has one try left, then reads BOTH stores.
     */
    @Test
    public void test_recoveryResetMovesTheGraphStateTogetherWithTheDbState() {
        //   root(OK in graph) -> failing(ERROR_WITH_RECOVERY in both stores)
        final TaskImpl root = newTask("assembly-raw-file", 1);
        final TaskImpl failing = newTask("dataset-processing", 1);
        buildTwoVertexGraph(root, failing);

        failInDbAndGraph(failing);
        setGraphState(root.id, EnumsApi.TaskExecState.OK);

        assertEquals(EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value, dbStateOf(failing.id),
                "setup failed: the failing Task must be ERROR_WITH_RECOVERY in the DB before recovery runs");
        assertEquals(EnumsApi.TaskExecState.ERROR_WITH_RECOVERY, graphStateOf(failing.id),
                "setup failed: the failing Task must be ERROR_WITH_RECOVERY in the graph before recovery runs");
        assertFalse(assignableTaskIds().contains(failing.id),
                "setup failed: an ERROR_WITH_RECOVERY vertex must not be assignable");

        toStarted();

        // ---- the act under characterization ----------------------------------------------------
        // triesAfterError=1 and triesWasMade=0, so maxTries > triesWasMade and the target state is NONE
        runRecovery();

        // the DB half has always worked
        assertEquals(EnumsApi.TaskExecState.NONE.value, dbStateOf(failing.id),
                "recovery must reset the failed Task to NONE in the DB for its remaining try");

        // ---- the two assertions the bug lives in ------------------------------------------------
        assertEquals(EnumsApi.TaskExecState.NONE, graphStateOf(failing.id),
                "recovery must carry the new state into the graph, not only into the DB");

        assertTrue(assignableTaskIds().contains(failing.id),
                "a Task reset to NONE for a remaining try must be assignable again");
    }

    /**
     * The production loop, which is what made the stall permanent rather than transient.
     *
     * <p>A Function-level analyzer with {@code incrementTries=false} gives a FREE retry, so the same
     * Task can go through recovery many times over — nine times in one minute on ExecContext #293.
     * Every pass must leave the two stores agreeing; one pass that does not is enough to strand the
     * Task, because from then on it is never a candidate for assignment and so can never fail again
     * to trigger another pass.
     */
    @Test
    public void test_repeatedRecoveryKeepsBothStoresInStepEveryTime() {
        final TaskImpl root = newTask("assembly-raw-file", 3);
        final TaskImpl failing = newTask("dataset-processing", 3);
        buildTwoVertexGraph(root, failing);
        setGraphState(root.id, EnumsApi.TaskExecState.OK);
        toStarted();

        for (int cycle = 1; cycle <= 3; cycle++) {
            failInDbAndGraph(failing);

            assertFalse(assignableTaskIds().contains(failing.id),
                    "cycle #" + cycle + ": a freshly failed Task must not be assignable");

            runRecovery();

            assertEquals(EnumsApi.TaskExecState.NONE.value, dbStateOf(failing.id),
                    "cycle #" + cycle + ": the DB state must come back to NONE");
            assertEquals(EnumsApi.TaskExecState.NONE, graphStateOf(failing.id),
                    "cycle #" + cycle + ": the graph state must come back to NONE alongside the DB state");
            assertTrue(assignableTaskIds().contains(failing.id),
                    "cycle #" + cycle + ": the Task must be a candidate for assignment again");
            assertEquals(cycle, triesWasMadeOf(failing.id),
                    "cycle #" + cycle + ": each ordinary recovery charges exactly one try");
        }
    }

    /**
     * The other branch of the same method: no tries left, so recovery finishes the Task as ERROR.
     *
     * <p>This pins the asymmetry that explains why only the NONE branch was broken — and it is checked
     * rather than asserted in a comment. {@code finishWithError} publishes
     * {@code UpdateTaskExecStatesInExecContextTxEvent} after the transaction commits, and that listener
     * is {@code @Async}, hence the wait.
     */
    @Test
    public void test_exhaustedTriesLeaveBothStoresOnError() {
        // triesAfterError=0, so maxTries(0) > triesWasMade(0) is false and the target state is ERROR
        final TaskImpl root = newTask("assembly-raw-file", 0);
        final TaskImpl failing = newTask("dataset-processing", 0);
        buildTwoVertexGraph(root, failing);

        failInDbAndGraph(failing);
        setGraphState(root.id, EnumsApi.TaskExecState.OK);
        toStarted();

        runRecovery();

        assertEquals(EnumsApi.TaskExecState.ERROR.value, dbStateOf(failing.id),
                "with no tries left the Task must be finished as ERROR in the DB");

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> graphStateOf(failing.id) == EnumsApi.TaskExecState.ERROR);

        assertFalse(assignableTaskIds().contains(failing.id),
                "a Task finished as ERROR must not be a candidate for assignment");
    }

    // ---- helpers ------------------------------------------------------------------------------

    private boolean ecCreated = false;

    private TaskImpl newTask(String processCode, int triesAfterError) {
        if (!ecCreated) {
            ecCreated = true;
            DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
            ExecContextCreatorService.ExecContextCreationResult result =
                    txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
            setExecContextForTest(result.execContext);
            assertNotNull(getExecContextForTest());
        }
        final Long execContextId = getExecContextForTest().id;
        return txTestingService.create(execContextId, taskParams(execContextId, processCode, triesAfterError));
    }

    private void buildTwoVertexGraph(TaskImpl root, TaskImpl failing) {
        final Long execContextId = getExecContextForTest().id;
        ExecContextSyncService.getWithSyncVoid(execContextId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId,
                                () -> addVertices(root, failing))));
    }

    private void addVertices(TaskImpl root, TaskImpl failing) {
        final TaskApiData.TaskWithContext tRoot = new TaskApiData.TaskWithContext(root.id, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext tFailing = new TaskApiData.TaskWithContext(failing.id, CommonConsts.TOP_LEVEL_CONTEXT_ID);

        OperationStatusRest osr;

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(), List.of(tRoot));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        refreshExecContext();

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(root.id), List.of(tFailing));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        refreshExecContext();
    }

    /** Puts a Task into ERROR_WITH_RECOVERY in BOTH stores, which is the state recovery acts on. */
    private void failInDbAndGraph(TaskImpl failing) {
        TaskSyncService.getWithSyncVoid(failing.id,
                () -> taskFinishingTxService.finishWithErrorWithTx(failing.id, "characterization: simulated function failure"));
        setGraphState(failing.id, EnumsApi.TaskExecState.ERROR_WITH_RECOVERY);
    }

    private void runRecovery() {
        execContextTaskResettingTopLevelService.resetTasksWithErrorForRecovery(
                new ResetTasksWithErrorEvent(getExecContextForTest().id));
    }

    private void toStarted() {
        final Long execContextId = getExecContextForTest().id;
        ExecContextSyncService.getWithSyncVoid(execContextId, () -> txSupportForTestingService.toStarted(execContextId));
    }

    /**
     * Writes the state a Task carries INSIDE the ExecContext graph. Distinct from
     * {@code TaskExecStateService}, which writes {@code TaskImpl.execState} in the DB and nothing else.
     */
    private void setGraphState(Long taskId, EnumsApi.TaskExecState state) {
        final Long execContextId = getExecContextForTest().id;
        ExecContextSyncService.getWithSyncVoid(execContextId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId,
                                () -> txSupportForTestingService.updateTaskExecState(
                                        execContextGraphService.getExecContextDAC(execContextId, getExecContextForTest().execContextGraphId),
                                        getExecContextForTest().execContextTaskStateId, taskId,
                                        state, CommonConsts.TOP_LEVEL_CONTEXT_ID))));
        refreshExecContext();
    }

    private EnumsApi.TaskExecState graphStateOf(Long taskId) {
        refreshExecContext();
        return execContextGraphService.getAllTasksTopologically(
                        getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId)
                .stream()
                .filter(t -> taskId.equals(t.taskId))
                .map(t -> t.state)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("task #" + taskId + " is not a vertex of the graph"));
    }

    private int triesWasMadeOf(Long taskId) {
        final ExecContextTaskState ects =
                execContextTaskStateRepository.findById(getExecContextForTest().execContextTaskStateId).orElse(null);
        assertNotNull(ects, "ExecContextTaskState wasn't found");
        final Integer tries = ects.getExecContextTaskStateParamsYaml().triesWasMade.get(taskId);
        assertNotNull(tries, "no triesWasMade entry for task #" + taskId);
        return tries;
    }

    private int dbStateOf(Long taskId) {
        final TaskImpl task = taskRepository.findByIdReadOnly(taskId);
        assertNotNull(task, "task #" + taskId + " wasn't found in the DB");
        return task.execState;
    }

    private List<Long> assignableTaskIds() {
        refreshExecContext();
        return execContextGraphService.findAllForAssigning(
                        getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, true)
                .stream().map(v -> v.taskId).toList();
    }

    private void refreshExecContext() {
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));
    }

    private static String taskParams(Long execContextId, String processCode, int triesAfterError) {
        TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.execContextId = execContextId;
        tpy.task.taskContextId = CommonConsts.TOP_LEVEL_CONTEXT_ID;
        tpy.task.processCode = processCode;
        tpy.task.context = EnumsApi.FunctionExecContext.external;
        tpy.task.function = new TaskParamsYaml.FunctionConfig();
        tpy.task.function.code = "function-01:1.1";
        tpy.task.triesAfterError = triesAfterError;
        return TaskParamsYamlUtils.UTILS.toString(tpy);
    }
}
