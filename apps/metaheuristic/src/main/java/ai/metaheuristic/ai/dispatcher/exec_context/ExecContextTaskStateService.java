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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 10/3/2020
 * Time: 10:22 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextTaskStateService {

    private final ExecContextGraphService execContextGraphService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskExecStateService taskExecStateService;

    @Transactional
    public OperationStatusRest updateTaskExecStatesWithTx(@Nullable ExecContextImpl execContext, Long taskId, int execState, @Nullable String taskContextId) {
        return updateTaskExecStates(execContext, taskId, execState, taskContextId);
    }

    public OperationStatusRest updateTaskExecStates(@Nullable ExecContextImpl execContext, Long taskId, int execState, @Nullable String taskContextId) {
        return updateTaskExecStates(execContext, taskId, execState, taskContextId, false);
    }

    public OperationStatusRest updateTaskExecStates(@Nullable ExecContextImpl execContext, Long taskId, int execState, @Nullable String taskContextId, boolean markAsCompleted) {
        TxUtils.checkTxExists();
        if (execContext==null) {
            // this execContext was deleted
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        execContextSyncService.checkWriteLockPresent(execContext.id);
        TaskImpl t = taskExecStateService.changeTaskState(taskId, EnumsApi.TaskExecState.from(execState));
        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(execContext, taskId, execState, taskContextId);
        taskExecStateService.updateTasksStateInDb(status);
        if (markAsCompleted && t!=null && (!t.isCompleted || t.completedOn==null)) {
            taskExecStateService.markAsCompleted(taskId);
        }
        return status.status;
    }


}
