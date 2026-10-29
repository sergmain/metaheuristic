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

package ai.metaheuristic.ai.task;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueData;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The read-only copies of {@link TaskQueue}: every assertion is on what the real queue reports after the real
 * queue operations ({@code addNewTask}, {@code lock}, {@code startTaskProcessing}, {@code deRegisterTask}).
 * Each test owns its own {@code TaskQueue} instance - the static queue of {@code TaskQueueService} is not used.
 *
 * @author Sergio Lissner
 * Date: 10/29/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class TaskQueueStateTest {

    private static TaskQueue.QueuedTask queued(Long execContextId, Long taskId, EnumsApi.TaskExecState snapshotState, String functionCode) {
        TaskImpl task = new TaskImpl();
        task.execContextId = execContextId;
        task.id = taskId;
        task.execState = snapshotState.value;
        TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.taskContextId = CommonConsts.TOP_LEVEL_CONTEXT_ID;
        tpy.task.function = new TaskParamsYaml.FunctionConfig();
        tpy.task.function.code = functionCode;
        return new TaskQueue.QueuedTask(EnumsApi.FunctionExecContext.external, execContextId, taskId, task, tpy, null, 0);
    }

    private static TaskQueue.QueuedTask queued(Long execContextId, Long taskId) {
        return queued(execContextId, taskId, EnumsApi.TaskExecState.NONE, "f:1.0");
    }

    @Test
    public void test_emptyQueue_noGroups_zeroSummary() {
        TaskQueue q = new TaskQueue(1, 5);
        assertEquals(List.of(), q.getGroupStates(null));
        TaskQueueData.QueueSummary s = TaskQueueData.summarize(q.groupCount(), q.getGroupStates(null));
        assertEquals(0, s.groupCount());
        assertEquals(0, s.nonEmptyGroups());
        assertEquals(0, s.tasks());
        assertEquals(List.of(), s.execContexts());
    }

    @Test
    public void test_queuedNotLocked_isInvisibleToHandOut() {
        TaskQueue q = new TaskQueue(1, 5);
        q.addNewTask(queued(1L, 11L));
        q.addNewTask(queued(1L, 12L));

        List<TaskQueueData.GroupState> groups = q.getGroupStates(null);
        assertEquals(1, groups.size());
        TaskQueueData.GroupState g = groups.getFirst();
        assertEquals(1L, g.execContextId());
        assertFalse(g.locked(), "a group is not locked until lock(execContextId)");
        assertEquals(2, g.allocated());
        assertEquals(List.of(11L, 12L), g.tasks().stream().map(TaskQueueData.TaskState::taskId).toList());
        assertFalse(q.getIterator().hasNext(), "the hand-out iterator must see nothing in an unlocked group");

        TaskQueueData.QueueSummary s = TaskQueueData.summarize(q.groupCount(), groups);
        assertEquals(1, s.unlockedNonEmptyGroups());
        assertEquals(0, s.handOutVisible());
        assertEquals(2, s.handOutInvisible());
        assertEquals(2, s.execContexts().getFirst().handOutInvisible());
    }

    @Test
    public void test_locked_becomesVisible_andAgreesWithIterator() {
        TaskQueue q = new TaskQueue(1, 5);
        q.addNewTask(queued(1L, 11L));
        q.addNewTask(queued(1L, 12L));
        q.lock(1L);

        List<TaskQueueData.GroupState> groups = q.getGroupStates(null);
        assertTrue(groups.getFirst().locked());
        TaskQueueData.QueueSummary s = TaskQueueData.summarize(q.groupCount(), groups);
        assertEquals(1, s.lockedGroups());
        assertEquals(2, s.handOutVisible());
        assertEquals(0, s.handOutInvisible());

        int seen = 0;
        TaskQueue.GroupIterator it = q.getIterator();
        while (it.hasNext()) {
            it.next();
            seen++;
        }
        assertEquals(s.handOutVisible(), seen, "handOutVisible must be exactly what the hand-out iterator walks");
    }

    @Test
    public void test_startTaskProcessing_assignedAndInProgress() {
        TaskQueue q = new TaskQueue(1, 5);
        q.addNewTask(queued(1L, 11L));
        q.addNewTask(queued(1L, 12L));
        q.lock(1L);
        q.startTaskProcessing(1L, 11L);

        TaskQueueData.TaskState t11 = q.getTaskState(11L);
        assertNotNull(t11);
        assertTrue(t11.assigned());
        assertEquals(EnumsApi.TaskExecState.IN_PROGRESS, t11.queueState());

        TaskQueueData.QueueSummary s = TaskQueueData.summarize(q.groupCount(), q.getGroupStates(null));
        assertEquals(1, s.assigned());
        assertEquals(1, s.handOutVisible());
        assertEquals(Map.of(EnumsApi.TaskExecState.IN_PROGRESS, 1, EnumsApi.TaskExecState.NONE, 1),
                s.execContexts().getFirst().byQueueState());
    }

    @Test
    public void test_taskState_carriesSnapshotStateAndFunctionCode() {
        TaskQueue q = new TaskQueue(1, 5);
        q.addNewTask(queued(1L, 11L, EnumsApi.TaskExecState.PRE_INIT, "mh.asset.call-cc_1.2"));

        TaskQueueData.TaskState t = q.getTaskState(11L);
        assertNotNull(t);
        assertEquals(EnumsApi.TaskExecState.NONE, t.queueState(), "the queue's own state starts as NONE");
        assertEquals(EnumsApi.TaskExecState.PRE_INIT, t.snapshotState(), "the TaskImpl copy taken at queueing time");
        assertEquals("mh.asset.call-cc_1.2", t.functionCode());
        assertEquals(EnumsApi.FunctionExecContext.external, t.context());
        assertNull(q.getTaskState(99L), "a Task not in the queue has no state");
    }

    @Test
    public void test_internalTask_hasNoSnapshot_andNoFunction() {
        TaskQueue q = new TaskQueue(1, 5);
        TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.context = EnumsApi.FunctionExecContext.internal;
        q.addNewInternalTask(1L, 21L, tpy);

        TaskQueueData.TaskState t = q.getTaskState(21L);
        assertNotNull(t);
        assertNull(t.snapshotState());
        assertNull(t.functionCode());
        assertTrue(t.assigned(), "an internal Task is assigned at queueing time");
    }

    @Test
    public void test_filterByExecContext_andPerExecContextSummary() {
        TaskQueue q = new TaskQueue(1, 5);
        q.addNewTask(queued(2L, 21L));
        q.addNewTask(queued(1L, 11L));
        q.lock(1L);

        assertEquals(List.of(1L), q.getGroupStates(1L).stream().map(TaskQueueData.GroupState::execContextId).toList());
        assertEquals(List.of(21L), q.getGroupStates(2L).getFirst().tasks().stream().map(TaskQueueData.TaskState::taskId).toList());
        assertEquals(List.of(), q.getGroupStates(3L));

        TaskQueueData.QueueSummary s = TaskQueueData.summarize(q.groupCount(), q.getGroupStates(null));
        assertEquals(List.of(1L, 2L), s.execContexts().stream().map(TaskQueueData.ExecContextQueueState::execContextId).toList());
        assertEquals(1, s.execContexts().get(0).handOutVisible());
        assertEquals(1, s.execContexts().get(0).lockedGroups());
        assertEquals(1, s.execContexts().get(1).handOutInvisible());
        assertEquals(0, s.execContexts().get(1).lockedGroups());
    }

    @Test
    public void test_copyIsDetached_fromLaterQueueChanges() {
        TaskQueue q = new TaskQueue(1, 5);
        q.addNewTask(queued(1L, 11L));
        List<TaskQueueData.GroupState> before = q.getGroupStates(null);

        q.lock(1L);
        q.deRegisterTask(1L, 11L);

        assertFalse(before.getFirst().locked(), "the copy keeps the state it was taken in");
        assertEquals(1, before.getFirst().tasks().size());
        assertEquals(List.of(), q.getGroupStates(null), "the queue itself is empty now");
        assertThrows(UnsupportedOperationException.class, () -> before.getFirst().tasks().clear());
    }

    @Test
    public void test_groupIndex_isQueuePosition() {
        TaskQueue q = new TaskQueue(1, 2);
        q.addNewTask(queued(1L, 11L));
        q.addNewTask(queued(1L, 12L));
        q.addNewTask(queued(1L, 13L));   // group size 2 - the third goes into a second group

        List<TaskQueueData.GroupState> groups = q.getGroupStates(null);
        assertEquals(List.of(0, 1), groups.stream().map(TaskQueueData.GroupState::index).toList());
        assertEquals(3, TaskQueueData.summarize(q.groupCount(), groups).tasks());
    }
}
