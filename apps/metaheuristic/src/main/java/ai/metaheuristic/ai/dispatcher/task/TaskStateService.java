/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 12/18/2020
 * Time: 4:18 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TaskStateService {

    private final TaskExecStateService taskExecStateService;

    public void updateTaskExecStates(TaskImpl task, EnumsApi.TaskExecState execState) {
        updateTaskExecStates(task, execState, false);
    }

    public void updateTaskExecStates(TaskImpl task, EnumsApi.TaskExecState execState, boolean markAsCompleted) {
        TxUtils.checkTxExists();
        TaskSyncService.checkWriteLockPresent(task.id);
        TaskImpl t = taskExecStateService.changeTaskState(task, execState);
        if (markAsCompleted) {
            t.setCompleted(true);
            t.setCompletedOn(System.currentTimeMillis());
        }
    }

}
