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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextReconciliationTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextStatusService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextSegmentReadService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A Task's state in MH_TASK is the source of truth; the static {@link TaskQueue} and the segments ("graph") are
 * reconciled over it, never the reverse. An abandoned queue entry - queued as NONE and unassigned while its Task left NONE
 * in MH_TASK through a write the queue did not follow (the operator's SQL of 2026-10-28 22:02 here) - is invisible to
 * the in-memory hand-out by design: the hand-out only asks the queue's groups. Reconciling it is the job of
 * {@link ExecContextReconciliationTopLevelService}.
 *
 * <p>Two ExecContexts from one SourceCode; the first external Task of each is queued as NONE, the first ExecContext's
 * ahead of the second's (asserted as a precondition on the real hand-out iterator). Every assertion is on the real
 * static {@link TaskQueue}, on the segment state and on what {@link TaskProviderTopLevelService#findTask} returns.
 *
 * @author Sergio Lissner
 * Date: 10/29/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class TaskHandOutStaleQueueEntryTest extends FeatureMethods {

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private ExecContextReconciliationTopLevelService execContextReconciliationTopLevelService;
    @Autowired private ExecContextSegmentReadService segmentReadService;
    @Autowired private ExecContextTopLevelService execContextTopLevelService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private record Abandoned(PreparingData.ProcessorIdAndCoreIds cores, Long execContextId1, Long abandonedTaskId,
                             Long execContextId2, Long nextTaskId) {}

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    /**
     * The hand-out alone does not reconcile: every poll stops at the abandoned entry, which stays queued, and the next
     * Task is never handed out. Pinned as the hand-out's behaviour - the reconciliation is the other test.
     */
    @Test
    public void test_handOutAlone_keepsAbandonedEntry_andHoldsUpTheNextTask() {
        final String code = SharedItEnv.uniqueCode("STALE_ENTRY");
        final Abandoned a = prepareAbandonedEntryAheadOfNextTask(code);

        final DispatcherCommParamsYaml.AssignedTask assigned = poll(a, 3);

        final TaskQueue.AllocatedTask abandonedEntry = TaskProviderTopLevelService.getTaskExecStates(a.execContextId1).get(a.abandonedTaskId);
        printState(code, a, abandonedEntry, assigned);

        // the hand-out alone - every poll stops at the abandoned entry, which stays queued, and the next Task is never handed out
        assertNull(assigned, code + ": a Task was handed out past the abandoned entry of Task #" + a.abandonedTaskId);
        assertNotNull(abandonedEntry, code + ": the abandoned entry of Task #" + a.abandonedTaskId + " left the queue");
    }

    /**
     * {@code db: PRE_INIT, graph: NONE, assigned: false, state in queue: NONE} - the state 307.100 logged for 299 Tasks on
     * 2026-10-28 22:14 and left as is ("required steps are unknown").
     */
    @Test
    public void test_reconciliation_dbPreInitGraphNoneQueuedNone_releasesTheNextTask() {
        final String code = SharedItEnv.uniqueCode("RECONCILE_PRE_INIT");
        final Abandoned a = prepareAbandonedEntryAheadOfNextTask(code);

        execContextReconciliationTopLevelService.reconcileStates(a.execContextId1);

        final Set<Long> handedOut = handOutToBothCores(a, 6);

        final TaskQueue.AllocatedTask abandonedEntry = TaskProviderTopLevelService.getTaskExecStates(a.execContextId1).get(a.abandonedTaskId);
        printState(code, a, abandonedEntry, null);
        System.out.println(code + " Task #" + a.abandonedTaskId + " MH_TASK: " + EnumsApi.TaskExecState.from(mhTaskState(a.abandonedTaskId)) +
                ", graph: " + graphState(a.execContextId1, a.abandonedTaskId) + "; handed out: " + handedOut);

        // MH_TASK is the source of truth: the reconciliation aligns the queue and the graph to it (01.307.090) and advances the
        // PRE_INIT Task under its finished direct parent (01.307.410) - nothing is held up any more, both Tasks are handed out
        assertEquals(Set.of(a.abandonedTaskId, a.nextTaskId), handedOut, code + ": both Tasks must be handed out");
    }

    /**
     * STOPPED -> STARTED is how an operator resumes an ExecContext, and a resumed ExecContext must be served like one that
     * was never stopped: its abandoned entries are reconciled at the start, not up to 630 s later by the scheduler.
     */
    @Test
    public void test_stoppedThenStarted_reconcilesTheAbandonedEntry() {
        final String code = SharedItEnv.uniqueCode("RESUME_RECONCILE");
        final Abandoned a = prepareAbandonedEntryAheadOfNextTask(code);

        // no poll while STOPPED - a poll seeing the ExecContext STOPPED (317.036) would drop the entry by itself
        changeState(code, a.execContextId1, EnumsApi.ExecContextState.STOPPED);
        changeState(code, a.execContextId1, EnumsApi.ExecContextState.STARTED);
        // in production Schedulers refreshes the ExecContext status cache every 9 s
        execContextStatusService.resetStatus();

        final Set<Long> handedOut = handOutToBothCores(a, 6);

        final TaskQueue.AllocatedTask abandonedEntry = TaskProviderTopLevelService.getTaskExecStates(a.execContextId1).get(a.abandonedTaskId);
        printState(code, a, abandonedEntry, null);
        System.out.println(code + " Task #" + a.abandonedTaskId + " MH_TASK: " + EnumsApi.TaskExecState.from(mhTaskState(a.abandonedTaskId)) +
                ", graph: " + graphState(a.execContextId1, a.abandonedTaskId) + "; handed out: " + handedOut);

        // STOPPED -> STARTED reconciles the ExecContext: the queue and the graph follow MH_TASK (01.307.090), the PRE_INIT Task
        // under its finished direct parent is advanced (01.307.410) - nothing is held up any more, both Tasks are handed out
        assertEquals(Set.of(a.abandonedTaskId, a.nextTaskId), handedOut, code + ": both Tasks must be handed out after STOPPED -> STARTED");
    }

    private void changeState(String code, Long execContextId, EnumsApi.ExecContextState state) {
        final OperationStatusRest status = execContextTopLevelService.changeExecContextState(
                state.name(), execContextId, new DispatcherContext(getAccount(), getCompany()));
        assertEquals(EnumsApi.OperationStatus.OK, status.status,
                code + ": changeExecContextState(" + state + ") of ExecContext #" + execContextId + ": " + status.getErrorMessagesAsStr());
    }

    private Abandoned prepareAbandonedEntryAheadOfNextTask(String code) {
        step_0_0_produce_tasks_and_start();
        final PreparingData.ProcessorIdAndCoreIds cores = preparingSourceCodeService.step_1_0_init_session_id(preparingCodeData.processor.getId());
        preparingSourceCodeService.step_1_1_register_function_statuses(cores, preparingSourceCodeData, preparingCodeData);
        final Long execContextId1 = getExecContextForTest().id;

        final Long abandonedTaskId = awaitExternalTaskQueuedAsNone(execContextId1);

        final Long execContextId2 = produceAndStartSecondExecContext(code);
        final Long nextTaskId = awaitExternalTaskQueuedAsNone(execContextId2);

        // precondition: the first ExecContext's entry is ahead of the second's in the hand-out order
        final List<Long> order = handOutVisibleTaskIds();
        assertTrue(order.contains(abandonedTaskId) && order.contains(nextTaskId),
                code + ": both Tasks must be visible to the hand-out, order: " + order);
        assertTrue(order.indexOf(abandonedTaskId) < order.indexOf(nextTaskId),
                code + ": Task #" + abandonedTaskId + " must be ahead of Task #" + nextTaskId + ", order: " + order);

        // MH_TASK moves on without the queue: the Task leaves NONE, its queue entry is not touched. UPDATED_ON a minute
        // back - the Task has been in that state for a while, as the 300 Tasks of 2026-10-28 had
        final int updated = jdbcTemplate.update("update MH_TASK set EXEC_STATE = ?, VERSION = VERSION + 1, UPDATED_ON = ? where ID = ?",
                EnumsApi.TaskExecState.PRE_INIT.value, System.currentTimeMillis() - 60_000, abandonedTaskId);
        assertEquals(1, updated, code + ": Task #" + abandonedTaskId + " must be updated in MH_TASK");

        return new Abandoned(cores, execContextId1, abandonedTaskId, execContextId2, nextTaskId);
    }

    private DispatcherCommParamsYaml.@Nullable AssignedTask poll(Abandoned a, int times) {
        DispatcherCommParamsYaml.AssignedTask assigned = null;
        for (int i = 0; i < times && assigned == null; i++) {
            preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(Objects.requireNonNull(execContextCache.findById(a.execContextId2, true)));
            assigned = taskProviderTopLevelService.findTask(a.cores.coreId1, false);
        }
        return assigned;
    }

    /** Hands out to both cores of the Processor until each has a Task - a core that has one gets the same Task again. */
    private Set<Long> handOutToBothCores(Abandoned a, int times) {
        DispatcherCommParamsYaml.AssignedTask onCore1 = null;
        DispatcherCommParamsYaml.AssignedTask onCore2 = null;
        for (int i = 0; i < times && (onCore1 == null || onCore2 == null); i++) {
            preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(Objects.requireNonNull(execContextCache.findById(a.execContextId2, true)));
            if (onCore1 == null) {
                onCore1 = taskProviderTopLevelService.findTask(a.cores.coreId1, false);
            }
            if (onCore2 == null) {
                onCore2 = taskProviderTopLevelService.findTask(a.cores.coreId2, false);
            }
        }
        final Set<Long> ids = new LinkedHashSet<>();
        if (onCore1 != null) {
            ids.add(onCore1.taskId);
        }
        if (onCore2 != null) {
            ids.add(onCore2.taskId);
        }
        return ids;
    }

    private int mhTaskState(Long taskId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject("select EXEC_STATE from MH_TASK where ID = ?", Integer.class, taskId));
    }

    private EnumsApi.TaskExecState graphState(Long execContextId, Long taskId) {
        return segmentReadService.snapshot(execContextId).states().getOrDefault(taskId, EnumsApi.TaskExecState.NONE);
    }

    private static void printState(String code, Abandoned a, TaskQueue.@Nullable AllocatedTask abandonedEntry,
                                   DispatcherCommParamsYaml.@Nullable AssignedTask assigned) {
        final TaskQueue.AllocatedTask nextEntry = TaskProviderTopLevelService.getTaskExecStates(a.execContextId2).get(a.nextTaskId);
        System.out.println(code + " abandoned Task #" + a.abandonedTaskId + " queued: " + (abandonedEntry != null) +
                (abandonedEntry == null ? "" : ", state in queue: " + abandonedEntry.state + ", assigned: " + abandonedEntry.assigned) +
                "; next Task #" + a.nextTaskId + " queued: " + (nextEntry != null) +
                (nextEntry == null ? "" : ", state in queue: " + nextEntry.state + ", assigned: " + nextEntry.assigned) +
                "; handed out: " + (assigned == null ? null : assigned.taskId) + "; hand-out order: " + handOutVisibleTaskIds());
    }

    private Long produceAndStartSecondExecContext(String code) {
        final DispatcherContext context = new DispatcherContext(getAccount(), getCompany());
        final ExecContextCreatorService.ExecContextCreationResult result =
                txSupportForTestingService.createExecContext(getSourceCode(), context.asUserExecContext());
        assertFalse(result.isErrorMessages(), code + ": the second ExecContext wasn't created");
        final ExecContextImpl execContext = Objects.requireNonNull(result.execContext);
        final Long id = execContext.id;
        ExecContextSyncService.getWithSyncVoid(id, () ->
                ExecContextGraphSyncService.getWithSyncVoid(id, () ->
                        ExecContextTaskStateSyncService.getWithSyncVoid(id, () ->
                                txSupportForTestingService.produceAndStartAllTasks(getSourceCode(), id))));
        execContextStatusService.resetStatus();
        assertEquals(EnumsApi.ExecContextState.STARTED, execContextStatusService.getExecContextState(id),
                code + ": the second ExecContext #" + id + " must be STARTED");
        return id;
    }

    private Long awaitExternalTaskQueuedAsNone(Long execContextId) {
        final AtomicReference<Long> ref = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(Objects.requireNonNull(execContextCache.findById(execContextId, true)));
                    final Long id = TaskProviderTopLevelService.getTaskExecStates(execContextId).values().stream()
                            .filter(t -> t.queuedTask.execContext == EnumsApi.FunctionExecContext.external)
                            .filter(t -> t.state == EnumsApi.TaskExecState.NONE && !t.assigned)
                            .map(t -> t.queuedTask.taskId)
                            .findFirst().orElse(null);
                    ref.set(id);
                    return id != null;
                });
        return ref.get();
    }

    /** The Task ids the hand-out loop can reach, in its order - {@link TaskQueue.GroupIterator} over the real queue. */
    private static List<Long> handOutVisibleTaskIds() {
        return TaskQueueSyncStaticService.getWithReadSync(() -> {
            final List<Long> ids = new ArrayList<>();
            final TaskQueue.GroupIterator iter = TaskQueueService.getIterator();
            while (iter.hasNext()) {
                ids.add(iter.next().queuedTask.taskId);
            }
            return ids;
        });
    }
}
