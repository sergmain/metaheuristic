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

package ai.metaheuristic.ai.launchpad.exec_context;

import ai.metaheuristic.ai.launchpad.beans.ExecContextImpl;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
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
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class ExecContextGraphTopLevelService {

    private final ExecContextGraphService execContextGraphService;
    private final ExecContextSyncService execContextSyncService;
    private final TaskPersistencer taskPersistencer;

    // section 'execContext graph methods'

    // read-only operations with graph
    public List<WorkbookParamsYaml.TaskVertex> findAll(ExecContextImpl workbook) {
        return execContextSyncService.getWithSyncReadOnly(workbook, () -> execContextGraphService.findAll(workbook));
    }

    public List<WorkbookParamsYaml.TaskVertex> findLeafs(ExecContextImpl workbook) {
        return execContextSyncService.getWithSyncReadOnly(workbook, () -> execContextGraphService.findLeafs(workbook));
    }

    public Set<WorkbookParamsYaml.TaskVertex> findDescendants(ExecContextImpl workbook, Long taskId) {
        return execContextSyncService.getWithSyncReadOnly(workbook, () -> execContextGraphService.findDescendants(workbook, taskId));
    }

    public List<WorkbookParamsYaml.TaskVertex> findAllForAssigning(ExecContextImpl workbook) {
        return execContextSyncService.getWithSyncReadOnly(workbook, () -> execContextGraphService.findAllForAssigning(workbook));
    }

    public List<WorkbookParamsYaml.TaskVertex> findAllBroken(ExecContextImpl workbook) {
        return execContextSyncService.getWithSyncReadOnly(workbook, () -> execContextGraphService.findAllBroken(workbook));
    }

    // write operations with graph
    public OperationStatusRest updateTaskExecStateByWorkbookId(Long workbookId, Long taskId, int execState) {
        return execContextSyncService.getWithSync(workbookId, workbook -> {
            final ExecContextOperationStatusWithTaskList status = updateTaskExecStateWithoutSync(workbook, taskId, execState);
            return status.status;
        });
    }

    private ExecContextOperationStatusWithTaskList updateTaskExecStateWithoutSync(ExecContextImpl workbook, Long taskId, int execState) {
        taskPersistencer.changeTaskState(taskId, EnumsApi.TaskExecState.from(execState));
        final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecState(workbook, taskId, execState);
        taskPersistencer.updateTasksStateInDb(status);
        return status;
    }

    public ExecContextOperationStatusWithTaskList updateGraphWithSettingAllChildrenTasksAsBroken(Long workbookId, Long taskId) {
        return execContextSyncService.getWithSync(workbookId, (workbook) -> execContextGraphService.updateGraphWithSettingAllChildrenTasksAsBroken(workbook, taskId));
    }

    public OperationStatusRest addNewTasksToGraph(Long workbookId, List<Long> parentTaskIds, List<Long> taskIds) {
        final OperationStatusRest withSync = execContextSyncService.getWithSync(workbookId, (workbook) -> execContextGraphService.addNewTasksToGraph(workbook, parentTaskIds, taskIds));
        return withSync != null ? withSync : OperationStatusRest.OPERATION_STATUS_OK;
    }

    public ExecContextOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(Long workbookId, Long taskId) {
        return execContextSyncService.getWithSync(workbookId, (workbook) -> execContextGraphService.updateGraphWithResettingAllChildrenTasks(workbook, taskId));
    }

    public ExecContextOperationStatusWithTaskList updateTaskExecStates(Long workbookId, ConcurrentHashMap<Long, Integer> taskStates) {
        if (taskStates==null || taskStates.isEmpty()) {
            return new ExecContextOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
        }
        return execContextSyncService.getWithSync(workbookId, (workbook) -> {
            final ExecContextOperationStatusWithTaskList status = execContextGraphService.updateTaskExecStates(workbook, taskStates);
            taskPersistencer.updateTasksStateInDb(status);
            return status;
        });
    }

    // end of section 'execContext graph methods'



}
