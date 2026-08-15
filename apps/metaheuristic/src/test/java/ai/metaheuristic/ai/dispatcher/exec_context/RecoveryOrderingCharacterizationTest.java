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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingTxService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueService;
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
 * Characterization of the "recovery runs only when nothing else is assignable" rule.
 *
 * <p>The rule is business behaviour, not an implementation detail: draining every other assignable
 * Task before reconsidering a failed one is what stops that Task being handed straight back to the
 * Processor that just failed it. In a multi-Processor deployment an immediate re-assignment most
 * often lands on the same problematic Processor, because it is the one that just freed a slot.
 * Nothing in this codebase provides processor-affinity avoidance, so this ordering is the whole of
 * the protection.
 *
 * <p>Two source lines carry it and, until this class existed, zero tests did:
 * <ul>
 * <li>{@link ExecContextGraphService#findAllForAssigning} returns only NONE / CHECK_CACHE vertices,
 *     so an ERROR_WITH_RECOVERY Task is structurally absent from the assignable set — not last in
 *     the queue, not in the queue at all. That half is pinned by
 *     {@code ai.metaheuristic.ai.exec_context_graph.ExecContextGraphServiceTest}.</li>
 * <li>{@code ExecContextTaskAssigningTopLevelService} fires {@code ResetTasksWithErrorEvent} only
 *     inside {@code if (vertices.isEmpty())}. That half is pinned HERE.</li>
 * </ul>
 *
 * <p>Every pre-existing caller in the test tree invokes {@code resetTasksWithErrorForRecovery(...)}
 * directly and so never exercises the guard, which means deleting the {@code if} condition left the
 * whole suite green. This test drives the real public entry point instead and inspects the recovery
 * queue, so deleting that condition turns the key assertion from 0 to 1.
 *
 * <p>Mechanics worth not re-deriving:
 * <ul>
 * <li>Schedulers are inert here — every scheduled method opens with a {@code globals.testing} guard
 *     — so the assigning pass runs only when this test asks for it, and no reconciliation pass can
 *     move a state the test set by hand.</li>
 * <li>The suspender is what makes a zero stable: with the recovery queue held, nothing can have
 *     drained between the await and the assertion.</li>
 * <li>The root vertex must be in a finished state. {@code findAllForAssigning} short-circuits on a
 *     NONE / CHECK_CACHE root and returns only that vertex, which would make the whole setup
 *     vacuous. Only one root vertex is permitted at all.</li>
 * </ul>
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
@Slf4j
public class RecoveryOrderingCharacterizationTest extends PreparingSourceCode {

    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private TxTestingService txTestingService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private ExecContextGraphService execContextGraphService;
    @Autowired private ExecContextTaskAssigningTopLevelService execContextTaskAssigningTopLevelService;
    @Autowired private ExecContextTaskResettingTopLevelService execContextTaskResettingTopLevelService;
    @Autowired private TaskFinishingTxService taskFinishingTxService;
    @Autowired private TaskRepository taskRepository;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    @Test
    public void test_recoveryIsQueuedOnlyWhenNothingElseIsAssignable() {
        // ---- an ExecContext holding three real Tasks -----------------------------------------
        //   root(OK) -> failing(ERROR_WITH_RECOVERY)
        //   root(OK) -> assignable(NONE)
        DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        setExecContextForTest(result.execContext);
        assertNotNull(getExecContextForTest());

        final Long execContextId = getExecContextForTest().id;

        final TaskImpl root = txTestingService.create(execContextId, taskParams(execContextId, "assembly-raw-file"));
        final TaskImpl failing = txTestingService.create(execContextId, taskParams(execContextId, "dataset-processing"));
        final TaskImpl assignable = txTestingService.create(execContextId, taskParams(execContextId, "assembly-raw-file"));

        ExecContextSyncService.getWithSyncVoid(execContextId, () ->
                ExecContextGraphSyncService.getWithSyncVoid(getExecContextForTest().execContextGraphId, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(getExecContextForTest().execContextTaskStateId,
                                () -> buildGraph(root, failing, assignable))));

        // ❗ The root is advanced in the GRAPH only, deliberately. Moving it to OK in the DB as well
        // fires TaskStateService.changeTaskStateToInitForChildren, which drags every child to INIT —
        // and the assigning pass diverts an INIT Task to InitVariablesEvent instead of registering it,
        // so the Task under test would never reach the task queue. Nothing reads the DB state of a
        // vertex the graph has already excluded from the assignable set, so leaving it alone costs
        // nothing here.

        // ERROR_WITH_RECOVERY cannot be written through TaskExecStateService — changeTaskState()
        // throws for it deliberately. finishWithErrorWithTx is the route the production code uses.
        TaskSyncService.getWithSyncVoid(failing.id,
                () -> taskFinishingTxService.finishWithErrorWithTx(failing.id, "characterization: simulated function failure"));

        final TaskImpl failingAfter = taskRepository.findByIdReadOnly(failing.id);
        assertNotNull(failingAfter);
        assertEquals(EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value, failingAfter.execState,
                "setup failed: the failing Task must be in ERROR_WITH_RECOVERY before the assigning pass runs");

        // ❗ The DB state and the GRAPH state are two separate stores and the assigning pass reads the
        // GRAPH. TaskExecStateService / finishWithErrorWithTx write only the DB, so the graph has to
        // be written too — otherwise the root stays NONE and findAllForAssigning short-circuits on it,
        // returning only the root and never reaching the branch under test.
        setGraphState(root.id, EnumsApi.TaskExecState.OK);
        setGraphState(failing.id, EnumsApi.TaskExecState.ERROR_WITH_RECOVERY);

        assertEquals(List.of(assignable.id), assignableTaskIds(),
                "setup failed: exactly the one NONE Task must be assignable before the assigning pass runs");

        ExecContextSyncService.getWithSyncVoid(execContextId, () -> txSupportForTestingService.toStarted(execContextId));

        // ---- scenario A: something else IS assignable -----------------------------------------
        execContextTaskResettingTopLevelService.getResetTasksWithErrorEventThreadedPool().registerProcessSuspender(() -> true);
        try {
            execContextTaskAssigningTopLevelService.putToQueue(new FindUnassignedTasksAndRegisterInQueueEvent());

            // the assignable Task reaching the task queue is proof the pass ran for this ExecContext
            // AND that it took the non-empty branch
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                    .until(() -> TaskQueueService.alreadyRegisteredWithSync(assignable.id));

            // THE KEY ASSERTION. Delete the `if (vertices.isEmpty())` guard and this becomes 1.
            assertEquals(0, execContextTaskResettingTopLevelService.getResetTasksWithErrorEventThreadedPool().size(execContextId),
                    "recovery must NOT be queued while another Task of the same ExecContext is still assignable");

            // ---- scenario B: nothing else is assignable ---------------------------------------
            // graph only, for the same reason as the root above
            setGraphState(assignable.id, EnumsApi.TaskExecState.OK);

            assertTrue(assignableTaskIds().isEmpty(),
                    "setup failed: the assignable set must be empty before the positive control runs");

            execContextTaskAssigningTopLevelService.putToQueue(new FindUnassignedTasksAndRegisterInQueueEvent());

            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                    .until(() -> execContextTaskResettingTopLevelService.getResetTasksWithErrorEventThreadedPool().isNotEmpty(execContextId));

            assertEquals(1, execContextTaskResettingTopLevelService.getResetTasksWithErrorEventThreadedPool().size(execContextId),
                    "with nothing else assignable the pass must queue exactly one recovery event");
        }
        finally {
            execContextTaskResettingTopLevelService.getResetTasksWithErrorEventThreadedPool().deRegisterProcessSuspender();
        }

        // ---- draining the held event advances the Task ----------------------------------------
        execContextTaskResettingTopLevelService.getResetTasksWithErrorEventThreadedPool().processPoolOfExecutors(execContextId);

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> execContextTaskResettingTopLevelService.getResetTasksWithErrorEventThreadedPool().size(execContextId) == 0);

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    TaskImpl t = taskRepository.findByIdReadOnly(failing.id);
                    return t != null && t.execState != EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value;
                });

        final TaskImpl recovered = taskRepository.findByIdReadOnly(failing.id);
        assertNotNull(recovered);
        // triesAfterError=1 and triesWasMade=0, so maxTries > triesWasMade and the target state is NONE
        assertEquals(EnumsApi.TaskExecState.NONE.value, recovered.execState,
                "draining the recovery event must reset the failed Task to NONE for its remaining try");
    }

    private void buildGraph(TaskImpl root, TaskImpl failing, TaskImpl assignable) {
        final TaskApiData.TaskWithContext tRoot = new TaskApiData.TaskWithContext(root.id, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext tFailing = new TaskApiData.TaskWithContext(failing.id, CommonConsts.TOP_LEVEL_CONTEXT_ID);
        final TaskApiData.TaskWithContext tAssignable = new TaskApiData.TaskWithContext(assignable.id, CommonConsts.TOP_LEVEL_CONTEXT_ID);

        OperationStatusRest osr;

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(), List.of(tRoot));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        refreshExecContext();

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(root.id), List.of(tFailing));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        refreshExecContext();

        osr = txSupportForTestingService.addTasksToGraphWithTx(getExecContextForTest().id, List.of(root.id), List.of(tAssignable));
        assertEquals(EnumsApi.OperationStatus.OK, osr.status);
        refreshExecContext();
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

    private List<Long> assignableTaskIds() {
        return execContextGraphService.findAllForAssigning(
                        getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId, true)
                .stream().map(v -> v.taskId).toList();
    }

    private void refreshExecContext() {
        setExecContextForTest(Objects.requireNonNull(execContextCache.findById(getExecContextForTest().id, true)));
    }

    private static String taskParams(Long execContextId, String processCode) {
        TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.execContextId = execContextId;
        tpy.task.taskContextId = CommonConsts.TOP_LEVEL_CONTEXT_ID;
        tpy.task.processCode = processCode;
        tpy.task.context = EnumsApi.FunctionExecContext.external;
        tpy.task.function = new TaskParamsYaml.FunctionConfig();
        tpy.task.function.code = "function-01:1.1";
        // one remaining try, so a drained recovery event resets to NONE rather than to ERROR
        tpy.task.triesAfterError = 1;
        return TaskParamsYamlUtils.UTILS.toString(tpy);
    }
}
