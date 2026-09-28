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

package ai.metaheuristic.ai.dispatcher.exec_context_task_state;

import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.events.FindUnassignedTasksAndRegisterInQueueTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextOperationStatusWithTaskList;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.commons.exceptions.CommonRollbackException;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Serge
 * Date: 10/3/2020
 * Time: 10:22 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextTaskStateTxService {

    private final TaskExecStateService taskExecStateService;
    private final EventPublisherService eventPublisherService;
    private final ai.metaheuristic.ai.dispatcher.exec_context_segment.ExecContextSegmentStateTxService segmentStateTxService;

    public record TransferStateResult(TaskQueue.TaskGroups taskGroups, Set<TaskData.TaskWithState> skippedTasks) {}

    // 041 Phase 9: both entry points write task states into segments and join records
    // (ExecContextSegmentStateTxService), no longer into the whole-ExecContext task-state record; the whole graph
    // (ExecContextDAC) is no longer needed - the SKIPPED closure walks the lines.
    @Transactional(rollbackFor = CommonRollbackException.class)
    public ExecContextOperationStatusWithTaskList updateTaskExecStatesInGraph(Long execContextId, List<TaskData.TaskWithStateAndTaskContextId> taskWithStates) {
        // 041 Phase 21: the task-state lock is keyed by the ExecContext id
        ExecContextTaskStateSyncService.checkWriteLockPresent(execContextId);

        final ExecContextOperationStatusWithTaskList status = segmentStateTxService.updateTaskExecStates(execContextId, taskWithStates);

        // the to-be-SKIPPED task-row writes are persisted by the orchestrator (per-task lock wrapping the per-task Tx), not here
        eventPublisherService.handleFindUnassignedTasksAndRegisterInQueueEvent(new FindUnassignedTasksAndRegisterInQueueTxEvent());

        return status;
    }

    @Transactional(rollbackFor = CommonRollbackException.class)
    public TransferStateResult transferStateFromTaskQueueToExecContext(Long execContextId) {
        ExecContextTaskStateSyncService.checkWriteLockPresent(execContextId);

        TaskQueue.TaskGroups taskGroups = TaskProviderTopLevelService.getTaskGroupForTransferring(execContextId);
        if (taskGroups.groups.isEmpty()) {
            throw new CommonRollbackException();
        }
        List<TaskData.TaskWithStateAndTaskContextId> taskWithStates = new ArrayList<>(TaskQueue.GROUP_SIZE_DEFAULT * 10);
        for (TaskQueue.TaskGroup group : taskGroups.groups) {
            for (TaskQueue.AllocatedTask task : group.tasks) {
                if (task==null) {
                    continue;
                }
                if (task.queuedTask.execContext != EnumsApi.FunctionExecContext.internal) {
                    if (task.queuedTask.task == null) {
                        throw new IllegalStateException("(task.queuedTask.task==null), need to investigate");
                    }
                    if (!task.queuedTask.execContextId.equals(task.queuedTask.task.execContextId)) {
                        throw new IllegalStateException("(!task.queuedTask.execContextId.equals(task.queuedTask.task.execContextId))");
                    }
                }
                if (task.queuedTask.taskParamYaml==null) {
                    throw new IllegalStateException("(task.queuedTask.taskParamYaml==null)");
                }
                String taskContextId = task.queuedTask.taskParamYaml.task.taskContextId;
                taskWithStates.add(new TaskData.TaskWithStateAndTaskContextId(task.queuedTask.taskId, task.state, taskContextId));
            }
        }
        final ExecContextOperationStatusWithTaskList status = segmentStateTxService.updateTaskExecStates(execContextId, taskWithStates);

        // the to-be-SKIPPED task-row writes are persisted by the orchestrator (per-task lock wrapping the per-task Tx), not here
        return new TransferStateResult(taskGroups, status.childrenTasks);
    }

    // 041 Phase 21: deleteOrphanTaskStates is gone with the whole-ExecContext task-state record

}
