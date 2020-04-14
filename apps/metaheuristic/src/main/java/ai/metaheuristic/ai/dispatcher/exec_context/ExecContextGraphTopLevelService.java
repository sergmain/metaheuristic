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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
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
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextGraphTopLevelService {

    private final @NonNull ExecContextGraphService execContextGraphService;
    private final @NonNull ExecContextSyncService execContextSyncService;
    private final @NonNull TaskPersistencer taskPersistencer;

    // section 'execContext graph methods'

    // read-only operations with graph
    public @NonNull List<ExecContextData.TaskVertex> findAll(@NonNull ExecContextImpl execContext) {
        List<ExecContextData.TaskVertex> vertexList = execContextSyncService.getWithSyncReadOnly(execContext, () -> execContextGraphService.findAll(execContext));
        return vertexList;
    }

    public List<ExecContextData.TaskVertex> findLeafs(ExecContextImpl execContext) {
        return execContextSyncService.getWithSyncReadOnly(execContext, () -> execContextGraphService.findLeafs(execContext));
    }

    public Set<ExecContextData.TaskVertex> findDescendants(ExecContextImpl execContext, Long taskId) {
        return execContextSyncService.getWithSyncReadOnly(execContext, () -> execContextGraphService.findDescendants(execContext, taskId));
    }

    public Set<ExecContextData.TaskVertex> findDirectDescendants(ExecContextImpl execContext, Long taskId) {
        return execContextSyncService.getWithSyncReadOnly(execContext, () -> execContextGraphService.findDirectDescendants(execContext, taskId));
    }

    public List<ExecContextData.TaskVertex> findAllForAssigning(ExecContextImpl execContext) {
        return execContextSyncService.getWithSyncReadOnly(execContext, () -> execContextGraphService.findAllForAssigning(execContext));
    }

    public List<ExecContextData.TaskVertex> findAllBroken(ExecContextImpl execContext) {
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
        if (execContext==null) {
            // this execContext was deleted
            return new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
        }
        taskPersistencer.changeTaskState(taskId, EnumsApi.TaskExecState.from(execState));
        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(execContext, taskId, execState);
        taskPersistencer.updateTasksStateInDb(status);
        return status;
    }

    public ExecContextOperationStatusWithTaskList updateGraphWithSettingAllChildrenTasksAsBroken(Long execContextId, Long taskId) {
        return execContextSyncService.getWithSync(execContextId, (execContext) -> execContextGraphService.updateGraphWithSettingAllChildrenTasksAsBroken(execContext, taskId));
    }

    public OperationStatusRest addNewTasksToGraph(Long execContextId, List<Long> parentTaskIds, List<Long> taskIds) {
        //noinspection UnnecessaryLocalVariable
        final OperationStatusRest withSync = execContextSyncService.getWithSync(execContextId,
                (execContext) -> execContextGraphService.addNewTasksToGraph(execContext, parentTaskIds, taskIds));
        return withSync;
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

    public void createEdges(Long execContextId, List<Long> lastIds, Set<ExecContextData.TaskVertex> descendants) {
        execContextSyncService.getWithSyncNullable(execContextId,
                (execContext) -> execContextGraphService.createEdges(execContext, lastIds, descendants));

    }
}
