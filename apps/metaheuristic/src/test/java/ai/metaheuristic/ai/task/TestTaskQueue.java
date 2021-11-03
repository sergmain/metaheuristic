/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 12/16/2020
 * Time: 3:09 PM
 */
public class TestTaskQueue {

    private static TaskQueue.QueuedTask createTask(Long execContextId, Long taskId, int priority) {

        TaskImpl task = new TaskImpl();
        task.execContextId = execContextId;
        task.id = taskId;
        TaskParamsYaml taskParamYaml = new TaskParamsYaml();
        taskParamYaml.task.taskContextId = Consts.TOP_LEVEL_CONTEXT_ID;

        final TaskQueue.QueuedTask queuedTask = new TaskQueue.QueuedTask(
                EnumsApi.FunctionExecContext.external, execContextId, taskId, task, taskParamYaml, null, priority);

        return queuedTask;
    }

    @Test
    public void test_1() {

        final TaskQueue.TaskGroup taskGroup = new TaskQueue.TaskGroup(1L, 0, 5);

        assertFalse(taskGroup.alreadyRegistered(15L));
        assertFalse(taskGroup.deRegisterTask(15L));
        assertFalse(taskGroup.isNewTask());
        assertTrue(taskGroup.noneTasks());
        assertTrue(taskGroup.isEmpty());

        taskGroup.reset();

        assertFalse(taskGroup.alreadyRegistered(15L));
        assertFalse(taskGroup.deRegisterTask(15L));
        assertFalse(taskGroup.isNewTask());
        assertTrue(taskGroup.noneTasks());
        assertTrue(taskGroup.isEmpty());

        TaskQueue.QueuedTask task_1_1 = createTask(1L, 15L, 0);
        taskGroup.addTask(task_1_1);

        assertEquals(1L, taskGroup.execContextId);
        assertTrue(taskGroup.alreadyRegistered(15L));
        assertFalse(taskGroup.isEmpty());
        assertFalse(taskGroup.noneTasks());

        assertFalse(taskGroup.isNewTask());
        taskGroup.lock();
        assertTrue(taskGroup.isNewTask());

        taskGroup.reset();

        assertFalse(taskGroup.alreadyRegistered(15L));
        assertFalse(taskGroup.deRegisterTask(15L));
        assertFalse(taskGroup.isNewTask());
        assertTrue(taskGroup.noneTasks());
        assertTrue(taskGroup.isEmpty());

        taskGroup.addTask(task_1_1);

        assertEquals(1L, taskGroup.execContextId);
        assertTrue(taskGroup.alreadyRegistered(15L));
        assertFalse(taskGroup.noneTasks());
        assertFalse(taskGroup.isEmpty());

        assertFalse(taskGroup.isNewTask());
        taskGroup.lock();
        assertTrue(taskGroup.isNewTask());

        taskGroup.deRegisterTask(15L);

        assertFalse(taskGroup.alreadyRegistered(15L));
        assertFalse(taskGroup.deRegisterTask(15L));
        assertFalse(taskGroup.isNewTask());
        assertTrue(taskGroup.noneTasks());
        assertTrue(taskGroup.isEmpty());

        taskGroup.addTask(task_1_1);

        TaskQueue.QueuedTask task_2_1 = createTask(2L, 16L, 0);

        assertThrows(IllegalStateException.class, () -> taskGroup.addTask(task_2_1));

        TaskQueue.QueuedTask task_1_2 = createTask(1L, 22L, 0);
        TaskQueue.QueuedTask task_1_3 = createTask(1L, 23L, 0);
        TaskQueue.QueuedTask task_1_4 = createTask(1L, 24L, 0);
        TaskQueue.QueuedTask task_1_5 = createTask(1L, 25L, 0);
        TaskQueue.QueuedTask task_1_6 = createTask(1L, 26L, 0);

        taskGroup.addTask(task_1_2);
        taskGroup.addTask(task_1_3);
        taskGroup.addTask(task_1_4);
        taskGroup.addTask(task_1_5);

        assertThrows(IllegalStateException.class, () -> taskGroup.addTask(task_1_6));

        taskGroup.reset();

        assertFalse(taskGroup.isNewTask());
        assertTrue(taskGroup.noneTasks());
        assertTrue(taskGroup.isEmpty());

        taskGroup.addTask(task_1_1);
        taskGroup.addTask(task_1_2);
        taskGroup.addTask(task_1_3);
        taskGroup.addTask(task_1_4);
        taskGroup.addTask(task_1_5);

        assertFalse(taskGroup.isNewTask());
        taskGroup.lock();
        assertTrue(taskGroup.isNewTask());

        taskGroup.assignTask(task_1_1.taskId);
        taskGroup.assignTask(task_1_2.taskId);
        taskGroup.assignTask(task_1_3.taskId);
        taskGroup.assignTask(task_1_4.taskId);
        taskGroup.assignTask(task_1_5.taskId);

        assertFalse(taskGroup.isNewTask());

    }

    @Test
    public void test_2() {
        final TaskQueue taskQueue = new TaskQueue(1, 5);

        assertTrue(taskQueue.isQueueEmpty());
        taskQueue.shrink();
        assertTrue(taskQueue.isQueueEmpty());
        taskQueue.removeAll(List.of());
        assertTrue(taskQueue.isQueueEmpty());


        TaskQueue.QueuedTask task_1_1 = createTask(1L, 21L, 0);
        TaskQueue.QueuedTask task_1_2 = createTask(1L, 22L, 0);
        TaskQueue.QueuedTask task_1_3 = createTask(1L, 23L, 0);
        TaskQueue.QueuedTask task_1_4 = createTask(1L, 24L, 0);
        TaskQueue.QueuedTask task_1_5 = createTask(1L, 25L, 0);
        TaskQueue.QueuedTask task_1_6 = createTask(1L, 26L, 0);

        taskQueue.addNewTask(task_1_1);
        taskQueue.addNewTask(task_1_2);
        taskQueue.addNewTask(task_1_3);
        taskQueue.addNewTask(task_1_4);
        taskQueue.addNewTask(task_1_5);

        assertEquals(1, taskQueue.groupCount());

        taskQueue.addNewTask(task_1_6);

        assertEquals(2, taskQueue.groupCount());

        taskQueue.removeAll(List.of(task_1_6));

        // it's 1 because taskQueue was created with minQueueSize==1
        assertEquals(1, taskQueue.groupCount());

        taskQueue.addNewTask(task_1_6);

        taskQueue.deRegisterTask(task_1_6.execContextId, task_1_6.taskId);

        assertEquals(2, taskQueue.groupCount());

        taskQueue.addNewTask(task_1_6);
        // 3 because all task groups wwere locked already
        assertEquals(3, taskQueue.groupCount());

        taskQueue.deleteByExecContextId(1L);
        assertEquals(1, taskQueue.groupCount());
        assertTrue(taskQueue.isQueueEmpty());

        taskQueue.addNewTask(task_1_1);
        taskQueue.addNewTask(task_1_2);
        taskQueue.addNewTask(task_1_3);
        taskQueue.addNewTask(task_1_4);
        taskQueue.addNewTask(task_1_5);
        taskQueue.addNewTask(task_1_6);

        assertTrue(taskQueue.alreadyRegistered(task_1_1.taskId));
        assertTrue(taskQueue.alreadyRegistered(task_1_6.taskId));

        taskQueue.deRegisterTask(-1L, -1L);
        taskQueue.shrink();

        assertEquals(2, taskQueue.groupCount());

        TaskQueue.AllocatedTask allocatedTask;
        TaskQueue.GroupIterator iter;

        iter = taskQueue.getIterator();
        assertFalse(iter.hasNext());

        taskQueue.lock(1L);

        assertTrue(iter.hasNext());
        allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_1.taskId, allocatedTask.queuedTask.taskId);

        assertTrue(iter.hasNext());
        allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_2.taskId, allocatedTask.queuedTask.taskId);

        assertTrue(iter.hasNext());
        allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_3.taskId, allocatedTask.queuedTask.taskId);

        assertTrue(iter.hasNext());
        allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_4.taskId, allocatedTask.queuedTask.taskId);

        assertTrue(iter.hasNext());
        allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_5.taskId, allocatedTask.queuedTask.taskId);

        assertTrue(iter.hasNext());
        allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_6.taskId, allocatedTask.queuedTask.taskId);

        assertFalse(iter.hasNext());

        taskQueue.deRegisterTask(task_1_2.execContextId, task_1_2.taskId);
        taskQueue.deRegisterTask(task_1_4.execContextId, task_1_4.taskId);
        taskQueue.deRegisterTask(task_1_5.execContextId, task_1_5.taskId);

        iter = taskQueue.getIterator();
        assertTrue(iter.hasNext());
        allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_1.taskId, allocatedTask.queuedTask.taskId);

        assertTrue(iter.hasNext());
        allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_3.taskId, allocatedTask.queuedTask.taskId);

        assertTrue(iter.hasNext());
        allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_6.taskId, allocatedTask.queuedTask.taskId);

        final TaskQueue.GroupIterator iterTemp = iter;
        assertFalse(iter.hasNext());
        assertThrows(NoSuchElementException.class, iterTemp::next);
    }

    @Test
    public void test_2_1() {
        final TaskQueue taskQueue = new TaskQueue(1, 5);

        assertTrue(taskQueue.isQueueEmpty());
        taskQueue.shrink();
        assertTrue(taskQueue.isQueueEmpty());
        taskQueue.removeAll(List.of());
        assertTrue(taskQueue.isQueueEmpty());
        assertTrue(taskQueue.isQueueEmpty());


        TaskQueue.QueuedTask task_1_1 = createTask(1L, 21L, 0);
        TaskQueue.QueuedTask task_1_2 = createTask(1L, 22L, 0);
        TaskQueue.QueuedTask task_1_3 = createTask(1L, 23L, 0);
        TaskQueue.QueuedTask task_1_4 = createTask(1L, 24L, 0);
        TaskQueue.QueuedTask task_1_5 = createTask(1L, 25L, 0);
        TaskQueue.QueuedTask task_1_6 = createTask(1L, 26L, 0);

        taskQueue.addNewTask(task_1_1);
        taskQueue.addNewTask(task_1_2);
        taskQueue.addNewTask(task_1_3);
        taskQueue.addNewTask(task_1_4);
        taskQueue.addNewTask(task_1_5);
        taskQueue.addNewTask(task_1_6);

        assertEquals(2, taskQueue.groupCount());

        TaskQueue.QueuedTask task_1_7_1 = createTask(1L, 27L, 1);

        taskQueue.addNewTask(task_1_7_1);

        assertEquals(3, taskQueue.groupCount());

        TaskQueue.GroupIterator iter;

        iter = taskQueue.getIterator();
        assertFalse(iter.hasNext());

        taskQueue.lock(1L);

        assertTrue(iter.hasNext());
        TaskQueue.AllocatedTask allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_7_1.taskId, allocatedTask.queuedTask.taskId);

        assertTrue(iter.hasNext());
        allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_1.taskId, allocatedTask.queuedTask.taskId);

        assertTrue(iter.hasNext());
        allocatedTask = iter.next();
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_2.taskId, allocatedTask.queuedTask.taskId);

        do {
            allocatedTask = iter.next();
        } while (iter.hasNext());
        assertFalse(allocatedTask.assigned);
        assertEquals(task_1_6.taskId, allocatedTask.queuedTask.taskId);

        taskQueue.deRegisterTask(task_1_7_1.execContextId, task_1_7_1.taskId);
        taskQueue.shrink();

        assertEquals(2, taskQueue.groupCount());

    }

//    @Test
//    public void test_3_1() {
//
//    }
}