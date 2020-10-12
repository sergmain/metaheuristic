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
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
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
    private final ExecContextService execContextService;
    private final TaskExecStateService taskExecStateService;
    private final TaskRepository taskRepository;

    @Transactional
    public OperationStatusRest updateTaskExecStatesWithTx(Long execContextId, Long taskId, EnumsApi.TaskExecState execState, @Nullable String taskContextId) {

        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            final String es = "#309.020 execContext #" + execContextId+ " wasn't found";
            log.warn(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            final String es = "#309.040 task #" + taskId+ " wasn't found";
            log.warn(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        return updateTaskExecStates(execContext, task, execState, taskContextId);
    }

    public OperationStatusRest updateTaskExecStates(@Nullable ExecContextImpl execContext, TaskImpl task, EnumsApi.TaskExecState execState, @Nullable String taskContextId) {
        return updateTaskExecStates(execContext, task, execState, taskContextId, false);
    }

    public OperationStatusRest updateTaskExecStates(@Nullable ExecContextImpl execContext, TaskImpl task, EnumsApi.TaskExecState execState, @Nullable String taskContextId, boolean markAsCompleted) {
        TxUtils.checkTxExists();
        if (execContext==null) {
            // this execContext was deleted
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        execContextSyncService.checkWriteLockPresent(execContext.id);
        TaskImpl t = taskExecStateService.changeTaskState(task, execState);
        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(execContext, t.id, execState, taskContextId);
        taskExecStateService.updateTasksStateInDb(status);
        if (markAsCompleted) {
//        if (markAsCompleted && (!t.isCompleted || t.completedOn==null)) {
            t.setCompleted(true);
            t.setCompletedOn(System.currentTimeMillis());
//            taskExecStateService.markAsCompleted(t);
        }
        return status.status;
    }


}
