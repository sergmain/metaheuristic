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
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.task.TaskPersistencer;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final ExecContextGraphService execContextGraphService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskPersistencer taskPersistencer;

    // section 'execContext graph methods'

    // read-only operations with graph
    public List<ExecContextData.TaskVertex> findAll(ExecContextImpl execContext) {
        List<ExecContextData.TaskVertex> vertexList = execContextSyncService.getWithSyncReadOnly(execContext.id, () -> execContextGraphService.findAll(execContext));
        return vertexList;
    }

    public List<ExecContextData.TaskVertex> findLeafs(ExecContextImpl execContext) {
        return execContextSyncService.getWithSyncReadOnly(execContext.id, () -> execContextGraphService.findLeafs(execContext));
    }

    public Set<ExecContextData.TaskVertex> findDescendants(ExecContextImpl execContext, Long taskId) {
        return execContextSyncService.getWithSyncReadOnly(execContext.id, () -> execContextGraphService.findDescendants(execContext, taskId));
    }

    public Set<ExecContextData.TaskVertex> findDirectDescendants(ExecContextImpl execContext, Long taskId) {
        return execContextSyncService.getWithSyncReadOnly(execContext.id, () -> execContextGraphService.findDirectDescendants(execContext, taskId));
    }

    public Set<ExecContextData.TaskVertex> findDirectAncestors(ExecContextImpl execContext, ExecContextData.TaskVertex vertex) {
        return execContextSyncService.getWithSyncReadOnly(execContext.id, () -> execContextGraphService.findDirectAncestors(execContext, vertex));
    }

    public List<ExecContextData.TaskVertex> findAllForAssigning(ExecContextImpl execContext) {
        return execContextSyncService.getWithSyncReadOnly(execContext.id, () -> execContextGraphService.findAllForAssigning(execContext));
    }

    public List<ExecContextData.TaskVertex> findAllBroken(ExecContextImpl execContext) {
        return execContextSyncService.getWithSyncReadOnly(execContext.id, () -> execContextGraphService.findAllBroken(execContext));
    }

    public Long getCountUnfinishedTasks(ExecContextImpl execContext) {
        return execContextSyncService.getWithSyncReadOnly(execContext.id, () -> execContextGraphService.getCountUnfinishedTasks(execContext));
    }

    public List<ExecContextData.TaskVertex> getUnfinishedTaskVertices(ExecContextImpl execContext) {
        return execContextSyncService.getWithSyncReadOnly(execContext.id, () -> execContextGraphService.getUnfinishedTaskVertices(execContext));
    }

    // write operations with graph

    public ExecContextOperationStatusWithTaskList updateTaskExecStateByExecContextId(Long execContextId, Long taskId, int execState, @Nullable String taskContextId) {
        return execContextSyncService.getWithSync(execContextId, execContext -> {
            final ExecContextOperationStatusWithTaskList status = updateTaskExecStateWithoutSync(execContext, taskId, execState, taskContextId);
            return status;
        });
    }

    public ExecContextOperationStatusWithTaskList updateGraphWithSettingAllChildrenTasksAsError(Long execContextId, Long taskId) {
        return execContextSyncService.getWithSync(execContextId, (execContext) ->
                execContextGraphService.updateGraphWithSettingAllChildrenTasksAsError(execContext, taskId));
    }

    public ExecContextOperationStatusWithTaskList updateGraphWithSettingAllChildrenTasksAsSkipped(Long execContextId, String taskContextId, Long taskId) {
        return execContextSyncService.getWithSync(execContextId, (execContext) ->
                execContextGraphService.updateGraphWithSettingAllChildrenTasksAsSkipped(execContext, taskContextId, taskId));
    }

    public OperationStatusRest addNewTasksToGraph(Long execContextId, List<Long> parentTaskIds, List<TaskApiData.TaskWithContext> taskIds) {
        final OperationStatusRest withSync = execContextSyncService.getWithSync(execContextId, (execContext) ->
                execContextGraphService.addNewTasksToGraph(execContext, parentTaskIds, taskIds));
        return withSync;
    }

    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(Long execContextId, Long taskId) {
        return execContextSyncService.getWithSync(execContextId, (execContext) ->
                execContextGraphService.updateGraphWithResettingAllChildrenTasks(execContext, taskId));
    }

    public ExecContextOperationStatusWithTaskList updateTaskExecStates(Long execContextId, Map<Long, TaskData.TaskState> taskStates) {
        if (taskStates.isEmpty()) {
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

    // this method is here because we need to call taskPersistencer
    private ExecContextOperationStatusWithTaskList updateTaskExecStateWithoutSync(@Nullable ExecContextImpl execContext, Long taskId, int execState, @Nullable String taskContextId) {
        if (execContext==null) {
            // this execContext was deleted
            return new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
        }
        taskPersistencer.changeTaskState(taskId, EnumsApi.TaskExecState.from(execState));
        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(execContext, taskId, execState, taskContextId);
        taskPersistencer.updateTasksStateInDb(status);
        return status;
    }

}
