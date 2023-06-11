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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.event.InitVariablesEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskFinishWithErrorEvent;
import ai.metaheuristic.ai.dispatcher.event.TransferStateFromTaskQueueToExecContextEvent;
import ai.metaheuristic.commons.utils.ThreadUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Sergio Lissner
 * Date: 6/10/2023
 * Time: 9:07 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskVariableInitService {

    private final TaskVariableInitTxService taskVariableInitTxService;

    private final ThreadUtils.ThreadedPool<InitVariablesEvent> threadedPool =
            new ThreadUtils.ThreadedPool<>(10, 0, this::intiVariables);

    @Async
    @EventListener
    public void handleEvent(InitVariablesEvent event) {
        threadedPool.putToQueue(event);
    }

    public void intiVariables(InitVariablesEvent event) {
        TaskSyncService.getWithSyncVoid(event.taskId,
                ()-> taskVariableInitTxService.intiVariables(event));
    }

}
