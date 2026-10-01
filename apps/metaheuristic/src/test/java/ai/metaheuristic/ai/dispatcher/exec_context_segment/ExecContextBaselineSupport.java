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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueEvent;
import ai.metaheuristic.ai.dispatcher.event.events.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCreatorTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskAssigningTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_variable_state.ExecContextVariableStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.internal_functions.TaskWithInternalContextEventService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.utils.threads.MultiTenantedQueue;
import ai.metaheuristic.commons.utils.threads.QueueWithThread;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives and observes an ExecContext for the Phase 1 baseline tests of 041-EXEC-CONTEXT-SEGMENTS-PLAN
 * (section 8.4), using only storage-independent observations: {@code MH_TASK} records (process code,
 * {@code taskContextId}, exec state), the ExecContext state, and the contents of the real internal-task queue.
 * Nothing here reads the whole-ExecContext graph, task-state or variable-state records, so the tests built on
 * it survive the switch to segments unchanged.
 *
 * <p><b>Hand-out order.</b> {@code MhInternalTaskPipelineRunner} executes every Task in state NONE it finds,
 * which is not the scheduler's ready set, so it cannot pin the order in which Tasks are handed out. This
 * driver instead runs the real allocator scan and reads what it queued:
 * <ol>
 *   <li>the internal-task queue is suspended for the whole run - BEFORE the caller's start action (creating the
 *       ExecContext, or a RUN_NOW graft, both of which kick the scheduler) - so nothing queued runs unobserved,
 *       including hand-outs from allocator scans fired by committed transactions;</li>
 *   <li>before each step, the ExecContext settles: no Task is left in INIT;</li>
 *   <li>the real allocator scan runs repeatedly, and the queued events of this ExecContext are read until the set
 *       is non-empty, no Task is in INIT, and the set stays unchanged for {@link #STABLE} - long enough for at
 *       least two scans, so a scan that ran before a finished Task's state reached the scheduler is superseded by
 *       one that ran after - that set is the step;</li>
 *   <li>the gate opens only long enough to start exactly those Tasks, then closes; the step ends when each
 *       of them has run - finished, or failed into ERROR_WITH_RECOVERY, which the dispatcher turns into ERROR
 *       only once a later scan finds nothing ready.</li>
 * </ol>
 * The run ends when the ExecContext reaches a finished state (FINISHED or ERROR).
 *
 * <p>⚠️ The queue is keyed by taskId, not by ExecContext id ({@link TaskWithInternalContextEvent#getId()}),
 * so {@code size(execContextId)} never sees anything; the events are found by scanning the queue's entries.
 *
 * <p>⚠️ No step waits for the task-state record to agree with {@code MH_TASK}: after a reset the two disagree by
 * construction ({@code TaskResetTxService} writes descendants PRE_INIT to {@code MH_TASK} and NONE to
 * {@code ExecContextTaskState}), so such a wait never ends - and it would read the record the baseline must not.
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class ExecContextBaselineSupport {

    /** How long the queued hand-out must stay unchanged; spans at least two allocator scans (the allocator queue waits 1 s after each). */
    private static final Duration STABLE = Duration.ofMillis(2500);

    /** Capture poll period; a scan is requested every second poll, i.e. no faster than the allocator queue processes them. */
    private static final Duration CAPTURE_POLL = Duration.ofMillis(600);

    public record TaskRow(Long id, String processCode, String ctx, EnumsApi.TaskExecState state) {
        public String key() {
            return processCode + "@" + ctx;
        }

        public boolean isFinished() {
            return state == EnumsApi.TaskExecState.OK || state == EnumsApi.TaskExecState.SKIPPED || state == EnumsApi.TaskExecState.ERROR;
        }
    }

    /** The ExecContext a step run drove, and the keys of the Tasks handed out at each step. */
    public record StepRun(Long execContextId, List<List<String>> steps) {
    }

    private final TaskRepository taskRepository;
    private final ExecContextCache execContextCache;
    private final ExecContextCreatorTopLevelService execContextCreatorTopLevelService;
    private final ExecContextTaskAssigningTopLevelService execContextTaskAssigningTopLevelService;
    private final TaskWithInternalContextEventService taskWithInternalContextEventService;
    private final ExecContextSchedulerService execContextSchedulerService;
    private final ExecContextVariableStateTopLevelService execContextVariableStateTopLevelService;

    /**
     * Creates, produces and starts an ExecContext of the given SourceCode, seeding the source-level input
     * {@code items} through the production entry point; a null {@code items} seeds nothing (the shape declares no input).
     */
    public ExecContextImpl createAndStart(PreparingData.PreparingSourceCodeData data, @Nullable String items) {
        final SourceCodeImpl sourceCode = Objects.requireNonNull(data.sourceCode, "the SourceCode of the shape must exist");
        final ExecContextApiData.UserExecContext context =
                new ExecContextApiData.UserExecContext(data.account.id, data.company.getUniqueId());
        final Map<String, ExecContextData.VariableValue> inputs =
                items == null ? Map.of() : Map.of("items", new ExecContextData.VariableValue(items));
        final ExecContextCreatorService.ExecContextCreationResult result = execContextCreatorTopLevelService.createExecContextAndStart(
                sourceCode.id, context, true, null,
                new ExecContextData.ExecContextCreationInfo("041 segment baseline"),
                inputs);
        assertTrue(result.getErrorMessagesAsList().isEmpty(),
                "creating the ExecContext must not error, errors: " + result.getErrorMessagesAsStr());
        assertNotNull(result.execContext, "the ExecContext must exist");
        final ExecContextImpl ec = execContextCache.findById(result.execContext.id, true);
        assertNotNull(ec, "the ExecContext must be readable after creation");
        assertEquals(EnumsApi.ExecContextState.STARTED.code, ec.state, "the ExecContext must be STARTED after creation");
        return ec;
    }

    /** Every Task of the ExecContext as (id, process code, ctx, exec state), from {@code MH_TASK}, sorted by key then id. */
    public List<TaskRow> rows(Long execContextId) {
        final List<TaskRow> rows = new ArrayList<>();
        for (TaskImpl t : taskRepository.findByExecContextIdReadOnly(execContextId)) {
            final TaskParamsYaml tpy = t.getTaskParamsYaml();
            rows.add(new TaskRow(t.id, tpy.task.processCode, tpy.task.taskContextId, EnumsApi.TaskExecState.from(t.execState)));
        }
        rows.sort(Comparator.comparing(TaskRow::key).thenComparing(TaskRow::id));
        return rows;
    }

    /** One line per Task: {@code processCode@ctx=STATE}, in {@link #rows} order. */
    public static String describeRows(List<TaskRow> rows) {
        return rows.stream().map(r -> r.key() + "=" + r.state()).collect(Collectors.joining("\n"));
    }

    /** One line per step: {@code <index>: key, key, ...}. */
    public static String describeSteps(List<List<String>> steps) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i).append(": ").append(String.join(", ", steps.get(i)));
        }
        return sb.toString();
    }

    public EnumsApi.ExecContextState state(Long execContextId) {
        final ExecContextImpl ec = execContextCache.findById(execContextId, true);
        assertNotNull(ec, "ExecContext #" + execContextId + " must exist");
        return EnumsApi.ExecContextState.fromCode(ec.state);
    }

    /**
     * Suspends the internal-task queue, runs {@code startUnderSuspendedQueue} (which returns the ExecContext it
     * started or reopened), then runs that ExecContext to a finished state one scheduler step at a time and returns,
     * per step, the keys of the Tasks the scheduler handed out, sorted.
     */
    public StepRun runStepByStep(Supplier<Long> startUnderSuspendedQueue, int maxSteps) {
        TxUtils.checkTxNotExists();
        final MultiTenantedQueue<Long, TaskWithInternalContextEvent> mtq = taskWithInternalContextEventService.TASK_WITH_INTERNAL_CTX_MTQ;
        final AtomicBoolean gateClosed = new AtomicBoolean(true);
        mtq.registerProcessSuspender(gateClosed::get);
        try {
            final Long execContextId = startUnderSuspendedQueue.get();
            final List<List<String>> steps = new ArrayList<>();
            for (int step = 0; step < maxSteps; step++) {
                settle(execContextId);
                final EnumsApi.ExecContextState state = state(execContextId);
                if (EnumsApi.ExecContextState.isFinishedState(state)) {
                    return new StepRun(execContextId, steps);
                }
                assertEquals(EnumsApi.ExecContextState.STARTED, state,
                        "step " + step + ": ExecContext #" + execContextId + " must still be STARTED, steps so far:\n" + describeSteps(steps));
                final List<Long> handedOut = captureHandOut(execContextId, step, steps);
                if (handedOut.isEmpty()) {
                    // the ExecContext finished while the step was being captured
                    continue;
                }
                steps.add(keysOf(execContextId, handedOut));
                drain(mtq, gateClosed, execContextId, handedOut, step);
            }
            fail("ExecContext #" + execContextId + " did not finish within " + maxSteps + " steps, steps so far:\n" + describeSteps(steps));
            return new StepRun(execContextId, steps);
        }
        finally {
            mtq.deRegisterProcessSuspender();
        }
    }

    /**
     * Every Task that ran - ended OK or ERROR - was handed out in exactly one step, and nothing else was. A hand-out
     * that escaped the suspended queue would break this, so a leak fails here instead of silently reordering the steps.
     */
    public void assertEveryRunTaskHandedOutOnce(Long execContextId, List<List<String>> steps, String label) {
        final List<String> handedOut = new ArrayList<>();
        steps.forEach(handedOut::addAll);
        final Set<String> distinct = new TreeSet<>(handedOut);
        assertEquals(distinct.size(), handedOut.size(), label + ": a Task was handed out in more than one step:\n" + describeSteps(steps));
        final Set<String> ran = rows(execContextId).stream()
                .filter(r -> r.state() == EnumsApi.TaskExecState.OK || r.state() == EnumsApi.TaskExecState.ERROR)
                .map(TaskRow::key)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(ran, distinct, label + ": the Tasks handed out must be exactly the Tasks that ended OK or ERROR");
    }

    /**
     * Waits until no Task of the ExecContext is left in INIT, driving the scheduler services that move INIT on.
     */
    public void settle(Long execContextId) {
        try {
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(200))
                    .until(() -> {
                        processScheduledTasks();
                        return taskRepository.findTaskWithInitState(execContextId).isEmpty();
                    });
        }
        catch (ConditionTimeoutException e) {
            fail("ExecContext #" + execContextId + " kept Tasks in INIT for 30s: " + taskRepository.findTaskWithInitState(execContextId));
        }
    }

    /**
     * Returns the task ids this step handed out, or an empty list when the ExecContext finished during the capture.
     */
    private List<Long> captureHandOut(Long execContextId, int step, List<List<String>> steps) {
        final AtomicReference<List<Long>> last = new AtomicReference<>(List.of());
        final AtomicInteger polls = new AtomicInteger();
        final AtomicBoolean finished = new AtomicBoolean(false);
        try {
            await().atMost(Duration.ofSeconds(30))
                    .pollDelay(Duration.ZERO)
                    .pollInterval(CAPTURE_POLL)
                    .during(STABLE)
                    .until(() -> {
                        if (finished.get()) {
                            return true;
                        }
                        processScheduledTasks();
                        if (EnumsApi.ExecContextState.isFinishedState(state(execContextId))) {
                            finished.set(true);
                            return true;
                        }
                        if (polls.getAndIncrement() % 2 == 0) {
                            execContextTaskAssigningTopLevelService.putToQueue(new FindUnassignedTasksAndRegisterInQueueEvent());
                        }
                        final List<Long> now = queuedTaskIds(execContextId);
                        final boolean stable = !now.isEmpty() && now.equals(last.get())
                                && taskRepository.findTaskWithInitState(execContextId).isEmpty();
                        last.set(now);
                        return stable;
                    });
        }
        catch (ConditionTimeoutException e) {
            fail("step " + step + ": the scheduler handed out no stable set of Tasks of ExecContext #" + execContextId
                    + ", last queued " + last.get() + ", steps so far:\n" + describeSteps(steps));
        }
        return finished.get() ? List.of() : last.get();
    }

    private void drain(MultiTenantedQueue<Long, TaskWithInternalContextEvent> mtq, AtomicBoolean gateClosed,
                       Long execContextId, List<Long> taskIds, int step) {
        gateClosed.set(false);
        try {
            for (Long taskId : taskIds) {
                mtq.processPoolOfExecutors(taskId);
            }
        }
        finally {
            gateClosed.set(true);
        }
        try {
            await().atMost(Duration.ofSeconds(60))
                    .pollInterval(Duration.ofMillis(200))
                    .until(() -> EnumsApi.ExecContextState.isFinishedState(state(execContextId)) || taskIds.stream().allMatch(this::hasRun));
        }
        catch (ConditionTimeoutException e) {
            fail("step " + step + ": the Tasks handed out did not run within 60s: " + taskIds);
        }
    }

    /** Finished, or failed and waiting for the dispatcher's recovery pass (ERROR_WITH_RECOVERY). */
    private boolean hasRun(Long taskId) {
        final TaskImpl t = taskRepository.findByIdReadOnly(taskId);
        return t != null && (t.execState == EnumsApi.TaskExecState.OK.value
                || t.execState == EnumsApi.TaskExecState.SKIPPED.value
                || t.execState == EnumsApi.TaskExecState.ERROR.value
                || t.execState == EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value);
    }

    /** The task ids of this ExecContext's events waiting in the suspended internal-task queue, sorted. */
    private List<Long> queuedTaskIds(Long execContextId) {
        final MultiTenantedQueue<Long, TaskWithInternalContextEvent> mtq = taskWithInternalContextEventService.TASK_WITH_INTERNAL_CTX_MTQ;
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                final List<Long> ids = new ArrayList<>();
                for (QueueWithThread<TaskWithInternalContextEvent> q : new ArrayList<>(mtq.queue.values())) {
                    for (int i = 0; i < q.size(); i++) {
                        final TaskWithInternalContextEvent event = q.get(i);
                        if (execContextId.equals(event.execContextId)) {
                            ids.add(event.taskId);
                        }
                    }
                }
                ids.sort(Comparator.naturalOrder());
                return ids;
            }
            catch (ConcurrentModificationException | IndexOutOfBoundsException e) {
                // the allocator is adding an event right now; read again
            }
        }
        fail("could not read the internal-task queue: it kept changing during 20 reads");
        return List.of();
    }

    private List<String> keysOf(Long execContextId, List<Long> taskIds) {
        final Map<Long, String> keyById = rows(execContextId).stream().collect(Collectors.toMap(TaskRow::id, TaskRow::key));
        return taskIds.stream()
                .map(id -> Objects.requireNonNull(keyById.get(id), "handed-out Task #" + id + " is not a Task of ExecContext #" + execContextId))
                .sorted()
                .toList();
    }

    private void processScheduledTasks() {
        execContextVariableStateTopLevelService.processFlushing();
        execContextSchedulerService.initTaskVariables();
        execContextSchedulerService.updateExecContextStatuses();
    }
}
