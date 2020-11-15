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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskFinishingTopLevelService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingTopLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 10/30/2020
 * Time: 7:14 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class EventBusService {

    public final TaskCheckCachingTopLevelService taskCheckCachingService;
    public final TaskWithInternalContextEventService taskWithInternalContextEventService;
    public final ExecContextTaskFinishingTopLevelService execContextTaskFinishingTopLevelService;
    public final ExecContextSyncService execContextSyncService;
    public final EventSenderService eventSenderService;
    public final DispatcherParamsService dispatcherParamsService;
    public final ExecContextTopLevelService execContextTopLevelService;

    @Async
    @EventListener
    public void registerVariableState(VariableUploadedEvent event) {
        execContextTopLevelService.registerVariableState(event);
    }

    @Async
    @EventListener
    public void registerCreatedTask(TaskCreatedEvent event) {
        execContextTopLevelService.registerCreatedTask(event);
    }

    @Async
    @EventListener
    public void checkTaskCanBeFinished(CheckTaskCanBeFinishedEvent event) {
        execContextTaskFinishingTopLevelService.checkTaskCanBeFinished(event.taskId, event.checkCaching);
    }

    @Async
    @EventListener
    public void registerTask(RegisterTaskForCheckCachingEvent event) {
        taskCheckCachingService.checkCaching(event);
    }

    @Async
    @EventListener
    public void processInternalFunction(final TaskWithInternalContextEvent event) {
        taskWithInternalContextEventService.processInternalFunction(event);
    }

    @SuppressWarnings("unused")
    @Async
    @EventListener
    public void checkAndCreateNewDispatcher(final DispatcherCacheCheckingEvent event) {
        dispatcherParamsService.checkAndCreateNewDispatcher();
    }


}
