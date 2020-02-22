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

package ai.metaheuristic.ai.mh.dispatcher..exec_context;

import ai.metaheuristic.ai.mh.dispatcher..beans.ExecContextImpl;
import ai.metaheuristic.ai.mh.dispatcher..task.TaskPersistencer;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Serge
 * Date: 1/24/2020
 * Time: 1:02 AM
 */
@Service
@Profile("mh.dispatcher.")
@Slf4j
@RequiredArgsConstructor
public class ExecContextGraphTopLevelService {

    private final ExecContextGraphService execContextGraphService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskPersistencer taskPersistencer;

    // section 'execContext graph methods'

    // read-only operations with graph
    public List<ExecContextParamsYaml.TaskVertex> findAll(ExecContextImpl execContext) {
        return execContextSyncService.getWithSyncReadOnly(execContext, () -> execContextGraphService.findAll(execContext));
    }

    public List<ExecContextParamsYaml.TaskVertex> findLeafs(ExecContextImpl execContext) {
        return execContextSyncService.getWithSyncReadOnly(execContext, () -> execContextGraphService.findLeafs(execContext));
    }

    public Set<ExecContextParamsYaml.TaskVertex> findDescendants(ExecContextImpl execContext, Long taskId) {
        return execContextSyncService.getWithSyncReadOnly(execContext, () -> execContextGraphService.findDescendants(execContext, taskId));
    }

    public List<ExecContextParamsYaml.TaskVertex> findAllForAssigning(ExecContextImpl execContext) {
        return execContextSyncService.getWithSyncReadOnly(execContext, () -> execContextGraphService.findAllForAssigning(execContext));
    }

    public List<ExecContextParamsYaml.TaskVertex> findAllBroken(ExecContextImpl execContext) {
        return execContextSyncService.getWithSyncReadOnly(execContext, () -> execContextGraphService.findAllBroken(execContext));
    }

    // write operations with graph
    public OperationStatusRest updateTaskExecStateByExecContextId(Long execContextId, Long taskId, int execState) {
        return execContextSyncService.getWithSync(execContextId, execContext -> {
            final ExecContextOperationStatusWithTaskList status = updateTaskExecStateWithoutSync(execContext, taskId, execState);
            return status.status;
        });
    }

    private ExecContextOperationStatusWithTaskList updateTaskExecStateWithoutSync(ExecContextImpl execContext, Long taskId, int execState) {
        taskPersistencer.changeTaskState(taskId, EnumsApi.TaskExecState.from(execState));
        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(execContext, taskId, execState);
        taskPersistencer.updateTasksStateInDb(status);
        return status;
    }

    public ExecContextOperationStatusWithTaskList updateGraphWithSettingAllChildrenTasksAsBroken(Long execContextId, Long taskId) {
        return execContextSyncService.getWithSync(execContextId, (execContext) -> execContextGraphService.updateGraphWithSettingAllChildrenTasksAsBroken(execContext, taskId));
    }

    public OperationStatusRest addNewTasksToGraph(Long execContextId, List<Long> parentTaskIds, List<Long> taskIds) {
        final OperationStatusRest withSync = execContextSyncService.getWithSync(execContextId, (execContext) -> execContextGraphService.addNewTasksToGraph(execContext, parentTaskIds, taskIds));
        return withSync != null ? withSync : OperationStatusRest.OPERATION_STATUS_OK;
    }

    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(Long execContextId, Long taskId) {
        return execContextSyncService.getWithSync(execContextId, (execContext) -> execContextGraphService.updateGraphWithResettingAllChildrenTasks(execContext, taskId));
    }

    public ExecContextOperationStatusWithTaskList updateTaskExecStates(Long execContextId, ConcurrentHashMap<Long, Integer> taskStates) {
        if (taskStates==null || taskStates.isEmpty()) {
            return new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
        }
        return execContextSyncService.getWithSync(execContextId, (execContext) -> {
            final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecStates(execContext, taskStates);
            taskPersistencer.updateTasksStateInDb(status);
            return status;
        });
    }

    // end of section 'execContext graph methods'



}
