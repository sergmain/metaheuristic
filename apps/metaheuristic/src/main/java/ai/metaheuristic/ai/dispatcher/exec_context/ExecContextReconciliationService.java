/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskStateTxService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 10/12/2020
 * Time: 1:38 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextReconciliationService {

    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final TaskStateTxService taskStateTxService;
    private final ExecContextTaskResettingService execContextTaskResettingService;

    @Transactional
    public void finishReconciliation(ExecContextData.ReconciliationStatus status) {
        ExecContextSyncService.checkWriteLockPresent(status.execContextId);

        if (!status.isNullState.get() && status.taskIsOkIds.isEmpty() && status.taskForResettingIds.isEmpty()) {
            return;
        }
        ExecContextImpl execContext = execContextCache.findById(status.execContextId);
        if (execContext==null) {
            return;
        }
        if (status.isNullState.get()) {
            log.info("#307.180 Found non-created task, graph consistency is failed");
            execContext.completedOn = System.currentTimeMillis();
            execContext.state = EnumsApi.ExecContextState.ERROR.code;
            return;
        }

        for (Long taskForResettingId : status.taskForResettingIds) {
            TaskSyncService.getWithSyncVoid(taskForResettingId, ()-> execContextTaskResettingService.resetTask(execContext, taskForResettingId));
        }
        for (Long taskIsOkId : status.taskIsOkIds) {
            TaskSyncService.getWithSyncVoid(taskIsOkId, ()-> {
                TaskImpl task = taskRepository.findById(taskIsOkId).orElse(null);
                if (task==null) {
                    log.error("#307.200 task is null");
                    return;
                }
                taskStateTxService.updateTaskExecStates(task, EnumsApi.TaskExecState.OK);
            });
        }
    }

}


