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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.event.*;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

    private final TaskService taskService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Async
    @EventListener
    public void handleTaskCommunicationEvent(TaskCommunicationEvent event) {
        taskService.updateAccessByProcessorOn(event.taskId);
    }

    public void processResendTaskOutputResourceResult(@Nullable String processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
        switch (status) {
            case SEND_SCHEDULED:
                log.info("#303.380 Processor #{} scheduled for sending an output variable #{}, task #{}. This is normal operation of Processor", processorId, variableId, taskId);
                break;
            case TASK_NOT_FOUND:
            case VARIABLE_NOT_FOUND:
            case TASK_IS_BROKEN:
            case TASK_PARAM_FILE_NOT_FOUND:
                applicationEventPublisher.publishEvent(new TaskFinishWithErrorEvent(taskId,
                        S.f("#303.390 Task #%s was finished while resending variable #%s with status %s", taskId, variableId, status)));

                break;
            case OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE:
                applicationEventPublisher.publishEvent(new SetVariableReceivedTxEvent(taskId, variableId, false).to());
                break;
        }
    }


}
