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
import ai.metaheuristic.ai.SharedItEnv;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueSyncStaticService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.preparing.PreparingData;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * An ExecContext that went STARTED -> STOPPED -> STARTED must be served exactly like one that was never stopped:
 * every Task ready at the start, and every Task that becomes ready afterwards, is handed out to a Processor core.
 *
 * <p>The stop and the start go through {@link ExecContextTopLevelService#changeExecContextState}, the method behind
 * the MCP tool {@code mh_exec_context_target_state} and the UI buttons - never through a write to
 * {@code mh_exec_context.state}. Every assertion is on the real static {@link TaskQueue} - all its entries for the
 * ExecContext, and the subset its hand-out iterator can see - and on what
 * {@link TaskProviderTopLevelService#findTask} returns.
 *
 * @author Sergio Lissner
 * Date: 10/29/2026
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextResumeHandOutTest extends FeatureMethods {

    @Autowired private ExecContextTopLevelService execContextTopLevelService;
    @Autowired private ExecContextStatusService execContextStatusService;
    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang("/source_code/yaml/default-source-code-for-testing.yaml", EnumsApi.SourceCodeLang.yaml, null);
    }

    /**
     * Shape 1: a Task is NONE and registered in the queue, nothing has taken it yet; the ExecContext is stopped and
     * started again; the allocator scan runs; a core polls.
     */
    @Test
    public void test_taskQueuedBeforeStop_isHandedOutAfterStart() {
        final String code = SharedItEnv.uniqueCode("RESUME_QUEUED");

        step_0_0_produce_tasks_and_start();
        final PreparingData.ProcessorIdAndCoreIds cores = preparingSourceCodeService.step_1_0_init_session_id(preparingCodeData.processor.getId());
        preparingSourceCodeService.step_1_1_register_function_statuses(cores, preparingSourceCodeData, preparingCodeData);
        final Long execContextId = getExecContextForTest().id;

        final Long taskId = awaitExternalTaskQueuedAsNone(execContextId);

        changeState(execContextId, EnumsApi.ExecContextState.STOPPED);
        changeState(execContextId, EnumsApi.ExecContextState.STARTED);

        // in production Schedulers refreshes the ExecContext status cache every 9 s and fires the allocator scan
        execContextStatusService.resetStatus();
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());

        final DispatcherCommParamsYaml.AssignedTask assigned = taskProviderTopLevelService.findTask(cores.coreId1, false);

        final TaskQueue.AllocatedTask entry = TaskProviderTopLevelService.getTaskExecStates(execContextId).get(taskId);
        System.out.println(code + " Task #" + taskId + " queued: " + (entry != null) +
                (entry == null ? "" : ", state in queue: " + entry.state + ", assigned: " + entry.assigned) +
                ", visible to hand-out: " + handOutVisibleTaskIds().contains(taskId) +
                ", handed out: " + (assigned == null ? null : assigned.taskId));

        // Green-1: current behaviour - the Task stays queued as NONE and is never handed out
        // observed 2026-10-29: shape 1 does not reproduce the incidents - after STOPPED -> STARTED alone the queued Task IS
        // handed out; pinned here as the current behaviour. The defect that holds Tasks is a queue entry whose Task left NONE in
        // MH_TASK - see ai.metaheuristic.ai.dispatcher.task.TaskHandOutStaleQueueEntryTest
        assertNotNull(assigned, code + ": Task #" + taskId + " wasn't handed out after STOPPED -> STARTED");
        assertEquals(taskId, assigned.taskId, code + ": Task #" + taskId + " must be handed out after STOPPED -> STARTED");
    }

    private Long awaitExternalTaskQueuedAsNone(Long execContextId) {
        final AtomicReference<Long> ref = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(500))
                .until(() -> {
                    preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest());
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

    private void changeState(Long execContextId, EnumsApi.ExecContextState state) {
        final OperationStatusRest status = execContextTopLevelService.changeExecContextState(
                state.name(), execContextId, new DispatcherContext(getAccount(), getCompany()));
        assertEquals(EnumsApi.OperationStatus.OK, status.status, "changeExecContextState(" + state + "): " + status.getErrorMessagesAsStr());
    }

    /** The Task ids the hand-out loop can reach - {@link TaskQueue.GroupIterator} over the real queue. */
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
