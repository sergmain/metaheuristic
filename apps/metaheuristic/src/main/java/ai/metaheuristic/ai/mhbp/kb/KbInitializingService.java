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

package ai.metaheuristic.ai.mhbp.kb;

import ai.metaheuristic.ai.mhbp.events.InitKbEvent;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.commons.utils.threads.ThreadedPool;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author Sergio Lissner
 * Date: 4/23/2023
 * Time: 11:57 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
public class KbInitializingService {

    public final KbService kbService;

    private final ThreadedPool<Long, InitKbEvent> initKbEventThreadedPool;

    public KbInitializingService(@Autowired KbService kbService) {
        this.kbService = kbService;
        this.initKbEventThreadedPool = new ThreadedPool<>("KbInitializingService-", 50, true, false, kbService::processInitKbEvent, ConstsApi.DURATION_NONE );
    }

    @PreDestroy
    public void onExit() {
        initKbEventThreadedPool.shutdown();
    }

    @Async
    @EventListener
    public void handleEvaluateProviderEvent(InitKbEvent event) {
        initKbEventThreadedPool.putToQueue(event);
    }

}
