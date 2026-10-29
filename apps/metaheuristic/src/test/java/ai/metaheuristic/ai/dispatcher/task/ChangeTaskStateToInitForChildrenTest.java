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
import ai.metaheuristic.ai.dispatcher.event.events.ChangeTaskStateToInitForChildrenTasksEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextReconciliationTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextSegmentReadService;
import ai.metaheuristic.ai.dispatcher.exec_context_segment.SegmentStates;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ChangeTaskStateToInitForChildrenTasksEvent} carries the id of a finished parent and moves its children to INIT. In
 * the normal flow it fires once, when that parent finishes, and every child is still PRE_INIT then. Sent later - the
 * reconciliation re-sends it for a PRE_INIT Task stuck under finished parents - it meets children that have moved on.
 * "ToInit" is the transition PRE_INIT -> INIT only: a child in any other state is left as it is.
 *
 * <p>The lifecycle, from the code: PRE_INIT (created, {@code TaskProducingService}) -> INIT (this event) -> CHECK_CACHE or
 * NONE ({@code TaskVariableInitTxService}, {@code init.nextState}) -> IN_PROGRESS -> OK / ERROR / ...
 *
 * @author Sergio Lissner
 * Date: 10/29/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ChangeTaskStateToInitForChildrenTest extends FeatureMethods {

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private ExecContextCache execContextCache;
    @Autowired private ExecContextSegmentReadService segmentReadService;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ExecContextReconciliationTopLevelService execContextReconciliationTopLevelService;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    /** A late event for a finished parent must not move its running child (IN_PROGRESS on a Processor core) to INIT. */
    @Test
    public void test_lateInitEventForParent_leavesARunningChildAlone() {
        final String code = SharedItEnv.uniqueCode("INIT_GATE");

        step_0_0_produce_tasks_and_start();
        final PreparingData.ProcessorIdAndCoreIds cores = preparingSourceCodeService.step_1_0_init_session_id(preparingCodeData.processor.getId());
        preparingSourceCodeService.step_1_1_register_function_statuses(cores, preparingSourceCodeData, preparingCodeData);
        final Long execContextId = getExecContextForTest().id;

        final Long childId = awaitExternalTaskQueuedAsNone(execContextId);
        final DispatcherCommParamsYaml.AssignedTask assigned = taskProviderTopLevelService.findTask(cores.coreId1, false);
        assertNotNull(assigned, code + ": Task #" + childId + " must be handed out");
        assertEquals(childId, assigned.taskId, code + ": Task #" + childId + " must be handed out");
        assertEquals(EnumsApi.TaskExecState.IN_PROGRESS.value, mhTaskState(childId), code + ": Task #" + childId + " must be IN_PROGRESS");

        final List<Long> parents = SegmentStates.Adjacency.of(segmentReadService.snapshot(execContextId).lines()).parentsOf(childId);
        assertFalse(parents.isEmpty(), code + ": Task #" + childId + " must have a direct parent");
        for (Long parentId : parents) {
            assertTrue(EnumsApi.TaskExecState.isFinishedState(mhTaskState(parentId)),
                    code + ": the direct parent #" + parentId + " of Task #" + childId + " must be finished");
        }

        // the event a late sender publishes for a finished direct parent
        final long sentAt = System.currentTimeMillis();
        eventPublisher.publishEvent(new ChangeTaskStateToInitForChildrenTasksEvent(parents.getFirst()));

        // ToInit is PRE_INIT -> INIT only: the running child keeps IN_PROGRESS (the handler acts within ~100 ms - observed
        // 2026-10-29 - so 3 s of an unchanged state is 30 times that)
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100))
                .until(() -> mhTaskState(childId) == EnumsApi.TaskExecState.IN_PROGRESS.value);
        System.out.println(code + " Task #" + childId + " after " + (System.currentTimeMillis() - sentAt) +
                " ms, state in MH_TASK: " + EnumsApi.TaskExecState.from(mhTaskState(childId)) + ", parents: " + parents);
        assertEquals(EnumsApi.TaskExecState.IN_PROGRESS.value, mhTaskState(childId), code + ": Task #" + childId + " left IN_PROGRESS");
    }

    /**
     * A Task PRE_INIT in all three places - MH_TASK, the graph, not queued - whose direct parents are all finished: its own
     * move to INIT, made when the last parent finished, didn't happen (here MH_TASK was set to PRE_INIT afterwards, as the
     * operator's SQL of 2026-10-28 22:02 did). The reconciliation aligns the graph and the queue to MH_TASK (01.307.090), and
     * from then on nothing moves the Task.
     */
    @Test
    public void test_reconciliation_preInitUnderFinishedParents_isAdvancedAndHandedOut() {
        final String code = SharedItEnv.uniqueCode("PRE_INIT_STUCK");

        step_0_0_produce_tasks_and_start();
        final PreparingData.ProcessorIdAndCoreIds cores = preparingSourceCodeService.step_1_0_init_session_id(preparingCodeData.processor.getId());
        preparingSourceCodeService.step_1_1_register_function_statuses(cores, preparingSourceCodeData, preparingCodeData);
        final Long execContextId = getExecContextForTest().id;

        final Long taskId = awaitExternalTaskQueuedAsNone(execContextId);
        final int updated = jdbcTemplate.update("update MH_TASK set EXEC_STATE = ?, VERSION = VERSION + 1, UPDATED_ON = ? where ID = ?",
                EnumsApi.TaskExecState.PRE_INIT.value, System.currentTimeMillis() - 60_000, taskId);
        assertEquals(1, updated, code + ": Task #" + taskId + " must be updated in MH_TASK");

        // two scheduler passes: the first aligns the graph and the queue to MH_TASK (01.307.090), the second meets the Task
        // PRE_INIT in all three places
        execContextReconciliationTopLevelService.reconcileStates(execContextId);
        execContextReconciliationTopLevelService.reconcileStates(execContextId);

        DispatcherCommParamsYaml.AssignedTask assigned = null;
        for (int i = 0; i < 6 && assigned == null; i++) {
            preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(Objects.requireNonNull(execContextCache.findById(execContextId, true)));
            assigned = taskProviderTopLevelService.findTask(cores.coreId1, false);
        }
        final TaskQueue.AllocatedTask entry = TaskProviderTopLevelService.getTaskExecStates(execContextId).get(taskId);
        System.out.println(code + " Task #" + taskId + " MH_TASK: " + EnumsApi.TaskExecState.from(mhTaskState(taskId)) +
                ", graph: " + graphState(execContextId, taskId) + ", queued: " + (entry != null) +
                ", handed out: " + (assigned == null ? null : assigned.taskId));

        // the PRE_INIT Task under finished direct parents is advanced - INIT, then CHECK_CACHE / NONE - and handed out
        assertNotNull(assigned, code + ": Task #" + taskId + " wasn't handed out, its state in MH_TASK: " + EnumsApi.TaskExecState.from(mhTaskState(taskId)));
        assertEquals(taskId, assigned.taskId, code + ": Task #" + taskId + " must be handed out");
        assertEquals(EnumsApi.TaskExecState.IN_PROGRESS.value, mhTaskState(taskId), code + ": Task #" + taskId + " must be IN_PROGRESS");
    }

    private EnumsApi.TaskExecState graphState(Long execContextId, Long taskId) {
        return segmentReadService.snapshot(execContextId).states().getOrDefault(taskId, EnumsApi.TaskExecState.NONE);
    }

    private int mhTaskState(Long taskId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject("select EXEC_STATE from MH_TASK where ID = ?", Integer.class, taskId));
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
}
