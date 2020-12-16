/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.ai.dispatcher.event.ProcessDeletedExecContextEvent;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Serge
 * Date: 12/16/2020
 * Time: 3:09 AM
 */
public class TaskQueue {

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode(of = {"taskId"})
    public static class QueuedTask {
        public Long execContextId;
        public Long taskId;
        public TaskImpl task;
        public TaskParamsYaml taskParamYaml;
        public String tags;
        public int priority;
    }

    public static class AllocatedTask {
        public final QueuedTask queuedTask;
        public boolean assigned;

        public AllocatedTask(QueuedTask queuedTask) {
            this.queuedTask = queuedTask;
        }
    }

    private static final int GROUP_SIZE = 5;
    private static final int MIN_QUEUE_SIZE = 25;

    public static class TaskGroup {
        @Nullable
        public Long execContextId;

        public final AllocatedTask[] tasks = new AllocatedTask[GROUP_SIZE];
        public int allocated = 0;

        public TaskGroup(Long execContextId) {
            this.execContextId = execContextId;
        }

        public boolean alreadyRegistered(Long taskId) {
            if (execContextId==null) {
                return false;
            }
            for (AllocatedTask task : tasks) {
                if (task!=null && task.queuedTask.taskId.equals(taskId)) {
                    return true;
                }
            }
            return false;
        }

        public boolean deRegisterTask(Long taskId) {
            for (int i = 0; i < tasks.length; i++) {
                if (tasks[i]!=null && tasks[i].queuedTask.taskId.equals(taskId)) {
                    tasks[i] = null;
                    --allocated;
                    if (Arrays.stream(tasks).noneMatch(Objects::nonNull)) {
                        execContextId = null;
                    }
                    return true;
                }
            }
            return false;
        }

        public boolean isNewTask() {
            if (execContextId==null) {
                return false;
            }
            for (AllocatedTask task : tasks) {
                if (task != null && !task.assigned) {
                    return true;
                }
            }
            return false;
        }

        public void addTask(QueuedTask task) {
            if (allocated==GROUP_SIZE) {
                throw new IllegalStateException("already allocated");
            }
            ++allocated;
            for (int i = 0; i < tasks.length; i++) {
                if (tasks[i]==null) {
                    tasks[i] = new AllocatedTask(task);
                }
            }
        }

        public void reset() {
            allocated = 0;
            execContextId = null;
            Arrays.fill(tasks, null);
        }
    }

    private final AtomicInteger queuePtr = new AtomicInteger();
    private final CopyOnWriteArrayList<TaskGroup> taskGroups = new CopyOnWriteArrayList<>();


    public void removeAll(List<QueuedTask> forRemoving) {

    }

    public void addNewTask(QueuedTask task) {
        List<TaskGroup> temp = new ArrayList<>(taskGroups);
        temp.sort(Comparator.comparingInt(o -> o.allocated));
        TaskGroup taskGroup = null;
        for (TaskGroup group : temp) {
            if (task.execContextId.equals(group.execContextId)) {
                if (group.allocated<GROUP_SIZE) {
                    taskGroup = group;
                }
            }
        }
        if (taskGroup==null) {
            taskGroup = new TaskGroup(task.execContextId);
            taskGroups.add(taskGroup);
        }
        taskGroup.addTask(task);
    }

    public static class GroupIterator implements Iterator<AllocatedTask> {

        private int step = 0;
        private int level = 0;
        private final CopyOnWriteArrayList<TaskGroup> taskGroups;
        private final AtomicInteger queuePtr;

        public GroupIterator(CopyOnWriteArrayList<TaskGroup> taskGroups, AtomicInteger queuePtr) {
            this.taskGroups = taskGroups;
            this.queuePtr = queuePtr;
        }

        @Override
        public boolean hasNext() {
            return true;
//            return taskGroups.get((step+queuePtr.get())%taskGroups.size()).allocated==;
        }

        @Override
        public AllocatedTask next() {
            if (step>=taskGroups.size()) {
                if (level<GROUP_SIZE) {
                    step = 0;
                }
                throw new NoSuchElementException();
            }
            ++step;
            throw new NoSuchElementException();
        }

    }

    public GroupIterator iterator() {
        return new GroupIterator(taskGroups, queuePtr);
    }

    public void deleteByExecContextId(Long execContextId) {
        List<TaskGroup> forRemoving = new ArrayList<>();
        for (TaskGroup taskGroup : taskGroups) {
            if (execContextId.equals(taskGroup.execContextId)) {
                if (taskGroups.size()-forRemoving.size()>MIN_QUEUE_SIZE) {
                    forRemoving.add(taskGroup);
                }
                else {
                    taskGroup.reset();
                }
            }
        }
        if (!forRemoving.isEmpty()) {
            taskGroups.removeAll(forRemoving);
        }
    }

    public boolean alreadyRegistered(Long taskId) {
        return taskGroups.stream().anyMatch(o->o.alreadyRegistered(taskId));
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

    public boolean isQueueEmpty() {
        return taskGroups.stream().noneMatch(TaskGroup::isNewTask);
    }

}
