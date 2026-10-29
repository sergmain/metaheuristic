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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.dispatcher.task.TaskQueueData;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueService;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The admin view of the in-memory task queue - what is queued, in which group, and whether a Processor can
 * be given it. Read-only: every endpoint returns a copy taken under the queue's read lock.
 *
 * <p>❗ Answers the question the database cannot: a Task can be NONE in MH_TASK, NONE in the queue and
 * still never reach a Processor, because the hand-out walks only locked groups. {@code summary} separates
 * {@code handOutVisible} (unassigned in locked groups) from {@code handOutInvisible} (unassigned in
 * unlocked groups); {@code groups} shows each group and its Tasks in hand-out order.
 *
 * <p>Admin-scoped like {@link ExecutionGateRestController}: the copies carry Function codes and Task ids
 * of every company's ExecContexts.
 *
 * @author Sergio Lissner
 * Date: 10/29/2026
 */
@RestController
@RequestMapping("/rest/v1/dispatcher/task-queue")
@Profile("dispatcher")
@CrossOrigin
@PreAuthorize("hasAnyRole('ADMIN')")
public class TaskQueueRestController {

    /**
     * Counts over the whole queue and per ExecContext.
     */
    @GetMapping("/summary")
    public TaskQueueData.QueueSummary summary() {
        return TaskQueueService.getSummaryWithReadSync();
    }

    /**
     * Every non-empty group with its Tasks, in hand-out order; {@code execContextId} narrows it to one ExecContext.
     */
    @GetMapping("/groups")
    public List<TaskQueueData.GroupState> groups(@RequestParam(required = false) @Nullable Long execContextId) {
        return TaskQueueService.getGroupStatesWithReadSync(execContextId);
    }

    /**
     * One Task as the queue holds it; 404 when the Task isn't queued.
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<TaskQueueData.TaskState> task(@PathVariable Long taskId) {
        return TaskQueueService.getTaskStateWithReadSync(taskId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
