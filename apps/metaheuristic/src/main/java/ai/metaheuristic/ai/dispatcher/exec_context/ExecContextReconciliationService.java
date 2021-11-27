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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class ExecContextReconciliationService {

    private final ExecContextService execContextService;
    private final TaskRepository taskRepository;
    private final TaskStateService taskStateService;
    private final ExecContextTaskResettingService execContextTaskResettingService;

    @Transactional
    public Void finishReconciliation(ExecContextData.ReconciliationStatus status) {
        ExecContextSyncService.checkWriteLockPresent(status.execContextId);

        if (!status.isNullState.get() && status.taskIsOkIds.isEmpty() && status.taskForResettingIds.isEmpty()) {
            return null;
        }
        ExecContextImpl execContext = execContextService.findById(status.execContextId);
        if (execContext==null) {
            return null;
        }
        if (status.isNullState.get()) {
            log.info("#307.180 Found non-created task, graph consistency is failed");
            execContext.completedOn = System.currentTimeMillis();
            execContext.state = EnumsApi.ExecContextState.ERROR.code;
            return null;
        }

        for (Long taskForResettingId : status.taskForResettingIds) {
            TaskSyncService.getWithSyncVoid(taskForResettingId, ()-> execContextTaskResettingService.resetTask(execContext, taskForResettingId));
        }
        for (Long taskIsOkId : status.taskIsOkIds) {
            TaskSyncService.getWithSyncNullable(taskIsOkId, ()-> {
                TaskImpl task = taskRepository.findById(taskIsOkId).orElse(null);
                if (task==null) {
                    log.error("#307.200 task is null");
                    return null;
                }
                TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                taskStateService.updateTaskExecStates(task, EnumsApi.TaskExecState.OK);
                return null;
            });
        }
        return null;
    }

}


