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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Serge
 * Date: 12/16/2020
 * Time: 3:09 AM
 */
@Slf4j
public class TaskQueue {

    private static final int MAX_PRIORITY = 2_000_000;

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode(of = {"taskId"})
    public static class QueuedTask {
        public final EnumsApi.FunctionExecContext execContext;
        public final Long execContextId;
        public final Long taskId;
        @Nullable
        public final TaskImpl task;
        public final TaskParamsYaml taskParamYaml;
        @Nullable
        public final String tag;
        public int priority;
    }

    public static class AllocatedTask {
        public final QueuedTask queuedTask;
        public EnumsApi.TaskExecState state = EnumsApi.TaskExecState.NONE;
        public boolean assigned;

        public AllocatedTask(QueuedTask queuedTask) {
            this.queuedTask = queuedTask;
        }
    }

    private static final int GROUP_SIZE_DEFAULT = 10;
    private static final int MIN_QUEUE_SIZE_DEFAULT = 5000;

    @Slf4j
    public static class TaskGroup {

        public final int groupSize;
        @Nullable
        public Long execContextId;

        public final AllocatedTask[] tasks;
        public int allocated = 0;
        public int priority;
        public boolean locked;

        public TaskGroup(Long execContextId, int priority, int groupSize) {
            this.execContextId = execContextId;
            this.priority = priority;
            this.groupSize = groupSize;
            this.tasks = new AllocatedTask[groupSize];
        }

        // we dont need execContextId because taskIds are unique across entire database
        public boolean alreadyRegistered(Long taskId) {
            return alreadyRegisteredAsTask(taskId)!=null;
        }

        @Nullable
        // we dont need execContextId because taskIds are unique across entire database
        public AllocatedTask alreadyRegisteredAsTask(Long taskId) {
            if (execContextId==null) {
                return null;
            }
            for (AllocatedTask task : tasks) {
                if (task!=null && task.queuedTask.taskId.equals(taskId)) {
                    return task;
                }
            }
            return null;
        }

        public boolean deRegisterTask(Long taskId) {
            for (int i = 0; i < tasks.length; i++) {
                if (tasks[i]!=null && tasks[i].queuedTask.taskId.equals(taskId)) {
                    tasks[i] = null;
                    --allocated;

                    boolean noneMatch = true;
                    for (AllocatedTask task : tasks) {
                        if (task != null) {
                            noneMatch = false;
                            break;
                        }
                    }
                    if (noneMatch) {
                        if (allocated!=0) {
                            throw new IllegalStateException("(allocated!=0)");
                        }
                        execContextId = null;
                        priority = 0;
                        locked = false;
                    }
                    else if (allocated==0) {
                        throw new IllegalStateException("(allocated==0)");
                    }
                    return true;
                }
            }
            return false;
        }

        public boolean isNewTask() {
            if (execContextId == null) {
                return false;
            }
            if (!locked) {
                return false;
            }
            for (AllocatedTask task : tasks) {
                if (task != null && !task.assigned) {
                    return true;
                }
            }
            return false;
        }

        public int newTasks() {
            if (execContextId == null) {
                return 0;
            }
            if (!locked) {
                return 0;
            }
            int count = 0;
            for (AllocatedTask task : tasks) {
                if (task != null && !task.assigned) {
                    count++;
                }
            }
            return count;
        }

        public void addTask(QueuedTask task) {
            if (noneTasks()) {
                priority = task.priority;
            }
            if (priority!= task.priority) {
                throw new IllegalStateException(
                        S.f("#029.040 Different priority, group priority: %d, task priority %d",
                                priority, task.priority));
            }
            if (execContextId!=null && !execContextId.equals(task.execContextId)) {
                throw new IllegalStateException("#029.080 wrong execContextId");
            }
            if (allocated==groupSize) {
                throw new IllegalStateException("#029.120 already allocated");
            }
            if (execContextId==null) {
                execContextId = task.execContextId;
            }
            ++allocated;
            for (int i = 0; i < tasks.length; i++) {
                if (tasks[i]==null) {
                    tasks[i] = new AllocatedTask(task);
                    break;
                }
            }
        }

        @Nullable
        public AllocatedTask assignTask(Long taskId) {
            for (AllocatedTask task : tasks) {
                if (task != null && task.queuedTask.taskId.equals(taskId)) {
                    task.assigned = true;
                    return task;
                }
            }
            return null;
        }

        public void reset() {
            allocated = 0;
            execContextId = null;
            Arrays.fill(tasks, null);
            locked = false;
        }

        public boolean isEmpty() {
            boolean noneTasks = noneTasks();
            if (!noneTasks && execContextId==null) {
                log.error("#029.160 There is a task but execContextId is null. Shouldn't happened.");
            }
            return execContextId==null || noneTasks;
        }

        public boolean noneTasks() {
            for (AllocatedTask task : tasks) {
                if (task != null) {
                    return false;
                }
            }
            return true;
        }

        public void lock() {
            locked = true;
        }
    }

    public static class GroupIterator implements Iterator<AllocatedTask> {

        public final int groupSize;
        private int groupPtr = 0;
        private int taskPtr = 0;
        private final List<TaskGroup> taskGroups;

        public GroupIterator(List<TaskGroup> taskGroups, int groupSize) {
            this.taskGroups = taskGroups;
            this.groupSize = groupSize;
        }

        @Override
        public boolean hasNext() {
            int idx = taskPtr;
            for (int i = groupPtr; i < taskGroups.size(); i++) {
                TaskGroup taskGroup = taskGroups.get(i);
                if (!taskGroup.locked) {
                    continue;
                }
                for (int j = idx; j < groupSize; j++) {
                    AllocatedTask task = taskGroup.tasks[j];
                    if (task!=null && !task.assigned) {
                        return true;
                    }
                }
                idx = 0;
            }
            return false;
        }

        @Override
        public AllocatedTask next() {
            for (; groupPtr < taskGroups.size(); groupPtr++) {
                TaskGroup taskGroup = taskGroups.get(groupPtr);
                if (!taskGroup.locked) {
                    continue;
                }
                for (; taskPtr < groupSize; taskPtr++) {
                    AllocatedTask task = taskGroup.tasks[taskPtr];
                    if (task!=null && !task.assigned) {
                        ++taskPtr;
                        if (taskPtr==groupSize) {
                            taskPtr = 0;
                            ++groupPtr;
                        }
                        return task;
                    }
                }
                taskPtr = 0;
            }
            throw new NoSuchElementException();
        }

    }

    public GroupIterator getIterator() {
        return new GroupIterator(taskGroups, groupSize);
    }

    private final int minQueueSize;
    private final int groupSize;
    private final List<TaskGroup> taskGroups = new ArrayList<>();
    private final AtomicInteger taskForExecution = new AtomicInteger();

    public TaskQueue() {
        this(MIN_QUEUE_SIZE_DEFAULT, GROUP_SIZE_DEFAULT);
    }

    public TaskQueue(int minQueueSize, int groupSize) {
        this.minQueueSize = minQueueSize;
        this.groupSize = groupSize;
    }

    @Nullable
    public TaskGroup getFinishedTaskGroup(Long execContextId) {
        for (TaskGroup taskGroup : taskGroups) {
            if (execContextId.equals(taskGroup.execContextId) && groupFinished(taskGroup)) {
                return taskGroup;
            }
        }
        return null;
    }

    @Nullable
    public TaskGroup getTaskGroupForTransfering(Long execContextId) {
        for (TaskGroup taskGroup : taskGroups) {
            if (execContextId.equals(taskGroup.execContextId) && groupReadyForTransfering(taskGroup)) {
                return taskGroup;
            }
        }
        return null;
    }

    public boolean allTaskGroupFinished(Long execContextId) {
        for (TaskGroup o : taskGroups) {
            if (execContextId.equals(o.execContextId)) {
                if (!groupFinished(o)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Map<Long, AllocatedTask> getTaskExecStates(Long execContextId) {
        Map<Long, AllocatedTask> map = new HashMap<>();
        for (TaskGroup taskGroup : taskGroups) {
            if (!execContextId.equals(taskGroup.execContextId)) {
                continue;
            }
            for (AllocatedTask task : taskGroup.tasks) {
                if (task==null) {
                    continue;
                }
                map.put(task.queuedTask.taskId, task);
            }
        }
        return map;
    }

    @Nullable
    public AllocatedTask getTaskExecState(Long execContextId, Long taskId) {
        for (TaskGroup taskGroup : taskGroups) {
            if (!execContextId.equals(taskGroup.execContextId)) {
                continue;
            }
            for (AllocatedTask task : taskGroup.tasks) {
                if (task==null) {
                    continue;
                }
                if (task.queuedTask.taskId.equals(taskId)) {
                    return task;
                }
            }
        }
        return null;
    }

    /**
     *
     * @param execContextId
     * @param taskId
     * @param state
     *
     * @return true is all tasks in group were finished
     */
    public boolean setTaskExecState(Long execContextId, Long taskId, final EnumsApi.TaskExecState state) {
        if (state== EnumsApi.TaskExecState.IN_PROGRESS || state== EnumsApi.TaskExecState.OK) {
            log.debug("#029.200 set task #{} as {}, execContextId: #{}", taskId, state, execContextId);
        }
        boolean ok = false;
        for (TaskGroup taskGroup : taskGroups) {
            if (!execContextId.equals(taskGroup.execContextId)) {
                continue;
            }
            if (taskGroup.allocated==0) {
                continue;
            }
            for (AllocatedTask task : taskGroup.tasks) {
                if (task==null) {
                    continue;
                }
                if (!task.queuedTask.taskId.equals(taskId)) {
                    continue;
                }
                if (!task.assigned && (state == EnumsApi.TaskExecState.OK || state == EnumsApi.TaskExecState.ERROR || state == EnumsApi.TaskExecState.ERROR_WITH_RECOVERY )) {
                    log.warn("#029.240 start processing of task #{} because the task wasn't assigned.", task.queuedTask.taskId);
                    // if this task was already processed but wasn't assigned, then set it as IN_PROGRESS and then finish it with specified state
                    startTaskProcessing(task.queuedTask.execContextId, task.queuedTask.taskId);
                }
                // state from CHECK_CACHE to NONE is being changing without assigning
                else if (!task.assigned && state!=EnumsApi.TaskExecState.NONE) {
                    log.warn("#029.260 State of task #{} {} can't be changed to {} because the task wasn't assigned.",
                            task.queuedTask.task==null ? null : "<null>", task.queuedTask.taskId, state);
                    try {
                        throw new RuntimeException("This isn't actual an error, only for stacktrace:");
                    }
                    catch (RuntimeException e) {
                        log.warn("#029.280 Stacktrace", e);
                    }
                    continue;
                }
                task.state = state;
                // task was reset or checked with a cache and the cache was missed
                if (task.state== EnumsApi.TaskExecState.NONE) {
                    task.assigned = false;
                }
                ok = true;
                break;
            }
            log.debug("#029.300 task #{}, state {}, execContextId: #{}, changed: {}", taskId, state, execContextId, ok);
            if (ok) {
                return groupFinished(taskGroup);
            }
        }
        log.debug("#029.320 task #{}, state {}, execContextId: #{}, not changed", taskId, state, execContextId);
        return false;
    }

    private static boolean groupFinished(TaskGroup taskGroup) {
        for (AllocatedTask task : taskGroup.tasks) {
            if (task==null) {
                continue;
            }
            if (!EnumsApi.TaskExecState.isFinishedState(task.state.value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean groupReadyForTransfering(TaskGroup taskGroup) {
        for (AllocatedTask task : taskGroup.tasks) {
            if (task==null) {
                continue;
            }
            if (!EnumsApi.TaskExecState.isFinishedStateIncludingRecovery(task.state.value)) {
                return false;
            }
        }
        return true;
    }

    public void lock(Long execContextId) {
        for (TaskGroup tg : taskGroups) {
            if (tg.locked) {
                continue;
            }
            if (execContextId.equals(tg.execContextId)) {
                if (tg.allocated>0) {
                    tg.lock();
                }
            }
        }
        int i=0;
    }

    public void removeAll(List<QueuedTask> forRemoving) {
        if (forRemoving.isEmpty()) {
            return;
        }
        Set<Long> execContextIds = new HashSet<>();
        for (QueuedTask o : forRemoving) {
            execContextIds.add(o.execContextId);
        }
        for (TaskGroup taskGroup : taskGroups) {
            if (execContextIds.contains(taskGroup.execContextId)) {
                for (QueuedTask queuedTask : forRemoving) {
                    if (queuedTask.execContextId.equals(taskGroup.execContextId)) {
                        taskGroup.deRegisterTask(queuedTask.taskId);
                    }
                }
            }
        }
    }

    public void addNewTask(QueuedTask task) {
        addNewTask(task, true);
    }

    private TaskGroup addNewTask(QueuedTask task, boolean fixPriority) {
        if (fixPriority && task.priority>MAX_PRIORITY) {
            task.priority = MAX_PRIORITY;
        }
        TaskGroup taskGroup = null;
        // find an allocated task group with a free slot
        for (TaskGroup group : taskGroups) {
            if (group.locked) {
                continue;
            }
            if (group.execContextId == null || group.priority != task.priority || group.allocated == groupSize || !group.execContextId.equals(task.execContextId)) {
                continue;
            }
            taskGroup = group;
            break;
        }

        // there wasn't an initialized task group with a free slot but there is a task group with the same priority.
        // so a new task task will be added right after the last task group with the same priority
        if (taskGroup==null) {
            for (int i = taskGroups.size(); i-->0; ) {
                TaskGroup group = taskGroups.get(i);
                if (group.locked) {
                    continue;
                }
                if (group.priority==task.priority) {
                    taskGroup = new TaskGroup(task.execContextId, task.priority, groupSize);
                    if (i+1==taskGroups.size()) {
                        taskGroups.add(taskGroup);
                    }
                    else {
                        taskGroups.add(i+1, taskGroup);
                    }
                    break;
                }
            }
        }

        // there isn't any task groups with the same priority as a priority of task
        if (taskGroup==null) {
            for (int i = 0; i<taskGroups.size(); i++) {
                TaskGroup group = taskGroups.get(i);
                if (group.locked) {
                    continue;
                }
                if (group.priority < task.priority) {
                    taskGroup = new TaskGroup(task.execContextId, task.priority, groupSize);
                    taskGroups.add(i, taskGroup);
                    break;
                }
            }
        }

        if (taskGroup==null) {
            taskGroup = new TaskGroup(task.execContextId, task.priority, groupSize);
            taskGroups.add(taskGroup);
        }
        taskGroup.addTask(task);
        return taskGroup;
    }

    public void addNewInternalTask(Long execContextId, Long taskId, TaskParamsYaml taskParamYaml) {
        if (taskParamYaml.task.context!= EnumsApi.FunctionExecContext.internal) {
            throw new IllegalStateException("#029.360 (taskParamYaml.task.context!= EnumsApi.FunctionExecContext.internal)");
        }
        QueuedTask task = new QueuedTask(EnumsApi.FunctionExecContext.internal, execContextId, taskId, null, taskParamYaml, null, TaskQueue.MAX_PRIORITY + 1);
        TaskGroup taskGroup = addNewTask(task, false);
        if (taskGroup.assignTask(taskId)==null) {
            throw new IllegalStateException("#029.400 (taskGroup.assignTask(taskId)==null)");
        }
    }

    public void startTaskProcessing(Long execContextId, Long taskId) {
        for (TaskGroup taskGroup : taskGroups) {
            if (execContextId.equals(taskGroup.execContextId)) {
                AllocatedTask allocatedTask = taskGroup.assignTask(taskId);
                if (allocatedTask!=null) {
                    allocatedTask.state = EnumsApi.TaskExecState.IN_PROGRESS;
                    return;
                }
            }
        }
    }

    public void shrink() {
        if (taskGroups.size()>minQueueSize) {
            int size = taskGroups.size();
            for (int i = 0; i < size; i++) {
                if (size==minQueueSize) {
                    break;
                }
                if (taskGroups.get(i).noneTasks()) {
                    taskGroups.remove(i);
                    --size;
                    --i;
                }
            }
        }
    }

    public void deleteByExecContextId(Long execContextId) {
        for (TaskGroup taskGroup : taskGroups) {
            if (execContextId.equals(taskGroup.execContextId)) {
                taskGroup.reset();
            }
        }
    }

    public boolean alreadyRegistered(Long taskId) {
        return alreadyRegisteredAsTask(taskId)!=null;
    }

    @Nullable
    public AllocatedTask alreadyRegisteredAsTask(Long taskId) {
        for (TaskGroup o : taskGroups) {
            final AllocatedTask allocatedTask = o.alreadyRegisteredAsTask(taskId);
            if (allocatedTask !=null) {
                return allocatedTask;
            }
        }
        return null;
    }

    public void deRegisterTask(Long execContextId, Long taskId) {
        for (TaskGroup taskGroup : taskGroups) {
            if (execContextId.equals(taskGroup.execContextId)) {
                if (taskGroup.deRegisterTask(taskId)) {
                    return;
                }
            }
        }
    }

    public boolean allocatedTaskMoreThan(int requiredNumberOfTasks) {
        int count = 0;
        for (TaskGroup taskGroup : taskGroups) {
            count += taskGroup.newTasks();
            if (count>requiredNumberOfTasks) {
                return true;
            }
        }
        return false;
    }

    public boolean isQueueEmpty() {
        for (TaskGroup taskGroup : taskGroups) {
            if (taskGroup.isNewTask()) {
                return false;
            }
        }
        return true;
    }

    public int groupCount() {
        return taskGroups.size();
    }
}
