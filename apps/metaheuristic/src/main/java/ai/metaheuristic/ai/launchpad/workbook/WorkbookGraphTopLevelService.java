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

package ai.metaheuristic.ai.launchpad.workbook;

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
public class WorkbookGraphTopLevelService {

    private final WorkbookGraphService workbookGraphService;
    private final WorkbookSyncService workbookSyncService;
    private final TaskPersistencer taskPersistencer;

    // section 'execContext graph methods'

    // read-only operations with graph
    public List<WorkbookParamsYaml.TaskVertex> findAll(ExecContextImpl workbook) {
        return workbookSyncService.getWithSyncReadOnly(workbook, () -> workbookGraphService.findAll(workbook));
    }

    public List<WorkbookParamsYaml.TaskVertex> findLeafs(ExecContextImpl workbook) {
        return workbookSyncService.getWithSyncReadOnly(workbook, () -> workbookGraphService.findLeafs(workbook));
    }

    public Set<WorkbookParamsYaml.TaskVertex> findDescendants(ExecContextImpl workbook, Long taskId) {
        return workbookSyncService.getWithSyncReadOnly(workbook, () -> workbookGraphService.findDescendants(workbook, taskId));
    }

    public List<WorkbookParamsYaml.TaskVertex> findAllForAssigning(ExecContextImpl workbook) {
        return workbookSyncService.getWithSyncReadOnly(workbook, () -> workbookGraphService.findAllForAssigning(workbook));
    }

    public List<WorkbookParamsYaml.TaskVertex> findAllBroken(ExecContextImpl workbook) {
        return workbookSyncService.getWithSyncReadOnly(workbook, () -> workbookGraphService.findAllBroken(workbook));
    }

    // write operations with graph
    public OperationStatusRest updateTaskExecStateByWorkbookId(Long workbookId, Long taskId, int execState) {
        return workbookSyncService.getWithSync(workbookId, workbook -> {
            final WorkbookOperationStatusWithTaskList status = updateTaskExecStateWithoutSync(workbook, taskId, execState);
            return status.status;
        });
    }

    private WorkbookOperationStatusWithTaskList updateTaskExecStateWithoutSync(ExecContextImpl workbook, Long taskId, int execState) {
        taskPersistencer.changeTaskState(taskId, EnumsApi.TaskExecState.from(execState));
        final WorkbookOperationStatusWithTaskList status = workbookGraphService.updateTaskExecState(workbook, taskId, execState);
        taskPersistencer.updateTasksStateInDb(status);
        return status;
    }

    public WorkbookOperationStatusWithTaskList updateGraphWithSettingAllChildrenTasksAsBroken(Long workbookId, Long taskId) {
        return workbookSyncService.getWithSync(workbookId, (workbook) -> workbookGraphService.updateGraphWithSettingAllChildrenTasksAsBroken(workbook, taskId));
    }

    public OperationStatusRest addNewTasksToGraph(Long workbookId, List<Long> parentTaskIds, List<Long> taskIds) {
        final OperationStatusRest withSync = workbookSyncService.getWithSync(workbookId, (workbook) -> workbookGraphService.addNewTasksToGraph(workbook, parentTaskIds, taskIds));
        return withSync != null ? withSync : OperationStatusRest.OPERATION_STATUS_OK;
    }

    public WorkbookOperationStatusWithTaskList updateGraphWithResettingAllChildrenTasks(Long workbookId, Long taskId) {
        return workbookSyncService.getWithSync(workbookId, (workbook) -> workbookGraphService.updateGraphWithResettingAllChildrenTasks(workbook, taskId));
    }

    public WorkbookOperationStatusWithTaskList updateTaskExecStates(Long workbookId, ConcurrentHashMap<Long, Integer> taskStates) {
        if (taskStates==null || taskStates.isEmpty()) {
            return new WorkbookOperationStatusWithTaskList(OperationStatusRest.OPERATION_STATUS_OK);
        }
        return workbookSyncService.getWithSync(workbookId, (workbook) -> {
            final WorkbookOperationStatusWithTaskList status = workbookGraphService.updateTaskExecStates(workbook, taskStates);
            taskPersistencer.updateTasksStateInDb(status);
            return status;
        });
    }

    // end of section 'execContext graph methods'



}
