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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextVariableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 7:22 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskTopLevelService {

    private final ExecContextSyncService execContextSyncService;
    private final TaskTransactionalService taskTransactionalService;
    private final ExecContextVariableService execContextVariableService;

    public Enums.UploadVariableStatus setVariableReceived(TaskImpl task, Long variableId) {
        return execContextSyncService.getWithSync(task.execContextId, () -> execContextVariableService.setVariableReceivedWithTx(task, variableId));
    }

    public void deleteOrphanTasks(List<Long> orphanExecContextIds) {
        for (Long execContextId : orphanExecContextIds) {
            execContextSyncService.getWithSyncNullable(execContextId, ()-> taskTransactionalService.deleteOrphanTasks(execContextId));
        }
    }
}
