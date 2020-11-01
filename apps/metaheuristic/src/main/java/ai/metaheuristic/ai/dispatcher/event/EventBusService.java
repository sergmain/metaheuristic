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

import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.task.TaskCheckCachingTopLevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;

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

    @Async
    @EventListener
    public void registerTask(RegisterTaskForCheckCachingEvent event) {
        VariableData.DataStreamHolder holder = new VariableData.DataStreamHolder();
        try {
            taskCheckCachingService.checkCaching(event, holder);
        }
        finally {
                for (InputStream inputStream : holder.inputStreams) {
                    try {
                        inputStream.close();
                    }
                    catch(Throwable th)  {
                        log.warn("#447.040 Error while closing stream", th);
                    }
                }
            }
    }

    @Async
    @EventListener
    public void processInternalFunction(final TaskWithInternalContextEvent event) {
        taskWithInternalContextEventService.processInternalFunction(event);
    }

}
