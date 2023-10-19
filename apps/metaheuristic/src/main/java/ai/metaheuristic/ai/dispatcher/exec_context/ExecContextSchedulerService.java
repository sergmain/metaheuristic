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

import ai.metaheuristic.ai.dispatcher.event.InitVariablesEvent;
import ai.metaheuristic.ai.dispatcher.event.InitVariablesTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueSyncStaticService;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Serge
 * Date: 7/16/2019
 * Time: 12:23 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExecContextSchedulerService {

    private final TaskRepository taskRepository;
    private final ExecContextRepository execContextRepository;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ApplicationEventPublisher eventPublisher;

    public void updateExecContextStatuses() {
        List<Long> execContextIds = execContextRepository.findIdsByExecState(EnumsApi.ExecContextState.STARTED.code);
        for (Long execContextId : execContextIds) {
            execContextTopLevelService.updateExecContextStatus(execContextId);
        }
        TaskProviderTopLevelService.shrink();
    }

    public void initTaskVariables() {
        List<Long> execContextIds = execContextRepository.findIdsByExecState(EnumsApi.ExecContextState.STARTED.code);
        for (Long execContextId : execContextIds) {
            List<Long> l = taskRepository.findTaskForInitState(execContextId);
            l.forEach(taskId->eventPublisher.publishEvent(new InitVariablesEvent(taskId)));

        }
        TaskProviderTopLevelService.shrink();
    }

}
