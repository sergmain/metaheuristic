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

import ai.metaheuristic.api.EnumsApi;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only copies of the in-memory {@link TaskQueue}, for looking at what the queue holds right now.
 *
 * <p>Every value is copied out of the queue while its read lock is held ({@link TaskQueueService}), so a
 * record never aliases a live {@code TaskGroup} or {@code AllocatedTask} and cannot be used to change one.
 *
 * <p>The distinction these records exist to make visible: a queued Task is offered to a Processor only
 * when its group is {@code locked} and the Task is not {@code assigned} - the hand-out iterator
 * ({@code TaskQueue.GroupIterator}) skips every unlocked group. A Task in an unlocked group is in the queue
 * and invisible to every Processor at once.
 *
 * @author Sergio Lissner
 * Date: 10/29/2026
 */
public final class TaskQueueData {

    private TaskQueueData() {
    }

    /**
     * One queued Task.
     *
     * @param queueState    the queue's own state of the Task ({@code AllocatedTask.state})
     * @param snapshotState the exec state of the {@code TaskImpl} copy taken when the Task was queued
     *                      ({@code QueuedTask.task}); {@code null} for a Task queued without one (internal
     *                      Functions). The hand-out rejects a Task whose copy is not NONE.
     */
    public record TaskState(Long taskId, Long execContextId, EnumsApi.FunctionExecContext context,
                            EnumsApi.TaskExecState queueState, boolean assigned,
                            EnumsApi.@Nullable TaskExecState snapshotState,
                            @Nullable String functionCode, @Nullable String tag, int priority) {
    }

    /**
     * One non-empty group of the queue.
     *
     * @param index the group's position in the queue - the order the hand-out walks groups in
     */
    public record GroupState(int index, @Nullable Long execContextId, int priority, boolean locked,
                             int allocated, int groupSize, List<TaskState> tasks) {
    }

    /**
     * The queue as seen from one ExecContext.
     *
     * @param handOutVisible   Tasks not assigned in locked groups - the only ones a Processor can be given
     * @param handOutInvisible Tasks not assigned in unlocked groups - queued, never offered until locked
     */
    public record ExecContextQueueState(Long execContextId, int groups, int lockedGroups, int tasks,
                                        int assigned, int handOutVisible, int handOutInvisible,
                                        Map<EnumsApi.TaskExecState, Integer> byQueueState) {
    }

    /**
     * @param groupCount     every group the queue holds, empty ones included
     * @param nonEmptyGroups groups holding at least one Task
     */
    public record QueueSummary(int groupCount, int nonEmptyGroups, int lockedGroups, int unlockedNonEmptyGroups,
                               int tasks, int assigned, int handOutVisible, int handOutInvisible,
                               List<ExecContextQueueState> execContexts) {
    }

    /**
     * Pure: folds the non-empty groups into the summary, per ExecContext ordered by id.
     */
    public static QueueSummary summarize(int groupCount, List<GroupState> nonEmptyGroups) {
        int locked = 0, tasks = 0, assigned = 0, visible = 0, invisible = 0;
        final Map<Long, int[]> perEc = new TreeMap<>();
        final Map<Long, Map<EnumsApi.TaskExecState, Integer>> perEcStates = new TreeMap<>();
        for (GroupState g : nonEmptyGroups) {
            if (g.locked()) {
                locked++;
            }
            for (TaskState t : g.tasks()) {
                tasks++;
                // [groups, lockedGroups, tasks, assigned, visible, invisible]; groups are counted below
                final int[] c = perEc.computeIfAbsent(t.execContextId(), k -> new int[6]);
                c[2]++;
                if (t.assigned()) {
                    assigned++;
                    c[3]++;
                }
                else if (g.locked()) {
                    visible++;
                    c[4]++;
                }
                else {
                    invisible++;
                    c[5]++;
                }
                perEcStates.computeIfAbsent(t.execContextId(), k -> new TreeMap<>()).merge(t.queueState(), 1, Integer::sum);
            }
            if (g.execContextId() != null) {
                final int[] c = perEc.computeIfAbsent(g.execContextId(), k -> new int[6]);
                c[0]++;
                if (g.locked()) {
                    c[1]++;
                }
            }
        }
        final List<ExecContextQueueState> ecs = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : perEc.entrySet()) {
            final int[] c = e.getValue();
            ecs.add(new ExecContextQueueState(e.getKey(), c[0], c[1], c[2], c[3], c[4], c[5],
                    perEcStates.getOrDefault(e.getKey(), Map.of())));
        }
        return new QueueSummary(groupCount, nonEmptyGroups.size(), locked, nonEmptyGroups.size() - locked,
                tasks, assigned, visible, invisible, ecs);
    }
}
