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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.event.StartTaskProcessingEvent;
import ai.metaheuristic.ai.dispatcher.event.UnAssignTaskEvent;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.dispatcher.task.TaskQueueSyncStaticService.*;

/**
 * @author Serge
 * Date: 11/1/2021
 * Time: 8:00 PM
 */
public class TaskQueueService {

    private static final TaskQueue taskQueue = new TaskQueue();

    public static TaskQueue.GroupIterator getIterator() {
        checkWriteLockPresent();
        return taskQueue.getIterator();
    }

    public static void removeAll(List<TaskQueue.QueuedTask> forRemoving) {
        checkWriteLockPresent();
        taskQueue.removeAll(forRemoving);
    }

    public static void deRegisterTask(Long execContextId, Long taskId) {
        checkWriteLockPresent();
        taskQueue.deRegisterTask(execContextId, taskId);
    }

    public static void lock(Long execContextId) {
        checkWriteLockPresent();
        taskQueue.lock(execContextId);
    }

    public static boolean isQueueEmpty() {
        checkWriteLockPresent();
        return taskQueue.isQueueEmpty();
    }

    public static boolean isQueueEmptyWithSync() {
        checkWriteLockNotPresent();
        return getWithSync(taskQueue::isQueueEmpty);
    }

    public static void startTaskProcessing(StartTaskProcessingEvent event) {
        checkWriteLockPresent();
        taskQueue.startTaskProcessing(event.execContextId, event.taskId);
    }


    public static void deleteByExecContextId(Long execContextId) {
        checkWriteLockPresent();
        taskQueue.deleteByExecContextId(execContextId);
    }

    public static boolean setTaskExecState(Long execContextId, Long taskId, EnumsApi.TaskExecState state) {
        checkWriteLockPresent();
        return taskQueue.setTaskExecState(execContextId, taskId, state);
    }

    @Nullable
    public static TaskQueue.TaskGroup getFinishedTaskGroup(Long execContextId) {
        checkWriteLockPresent();
        return taskQueue.getFinishedTaskGroup(execContextId);
    }

    @Nullable
    public static TaskQueue.AllocatedTask getTaskExecState(Long execContextId, Long taskId) {
        checkWriteLockPresent();
        return taskQueue.getTaskExecState(execContextId, taskId);
    }

    public static Map<Long, TaskQueue.AllocatedTask> getTaskExecStates(Long execContextId) {
        checkWriteLockPresent();
        return taskQueue.getTaskExecStates(execContextId);
    }

    public static void unAssignTask(UnAssignTaskEvent event) {
        checkWriteLockPresent();
        taskQueue.deRegisterTask(event.execContextId, event.taskId);
    }

    public static boolean allTaskGroupFinished(Long execContextId) {
        checkWriteLockPresent();
        return taskQueue.allTaskGroupFinished(execContextId);
    }

    public static boolean alreadyRegistered(Long taskId) {
        checkWriteLockPresent();
        return taskQueue.alreadyRegistered(taskId);
    }

    public static void addNewTask(TaskQueue.QueuedTask queuedTask) {
        checkWriteLockPresent();
        taskQueue.addNewTask(queuedTask);
    }

    public static void addNewInternalTask(Long execContextId, Long taskId, TaskParamsYaml taskParamYaml) {
        checkWriteLockPresent();
        taskQueue.addNewInternalTask(execContextId, taskId, taskParamYaml);
    }

    public static void shrink() {
        checkWriteLockPresent();
        taskQueue.shrink();
    }

}
