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
import ai.metaheuristic.commons.utils.threads.ThreadedPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
@Profile("dispatcher")
public class KbInitializingService {

    public final KbService kbService;

    private final ThreadedPool<InitKbEvent> initKbEventThreadedPool =
            new ThreadedPool<>(1, 0, true, false, kbService::processInitKbEvent);

    @Async
    @EventListener
    public void handleEvaluateProviderEvent(InitKbEvent event) {
        initKbEventThreadedPool.putToQueue(event);
    }

    public void processEvent() {
        initKbEventThreadedPool.processEvent();
    }

/*
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    private final LinkedList<InitKbEvent> queue = new LinkedList<>();

    @Async
    @EventListener
    public void handleInitKbEvent(InitKbEvent event) {
        putToQueue(event);
    }

    public void putToQueue(final InitKbEvent event) {
        synchronized (queue) {
            if (queue.contains(event)) {
                return;
            }
            queue.add(event);
        }
    }

    @Nullable
    private InitKbEvent pullFromQueue() {
        synchronized (queue) {
            return queue.pollFirst();
        }
    }

    public void processEvent() {
        if (executor.getActiveCount()>0) {
            return;
        }
        executor.submit(() -> {
            InitKbEvent event;
            while ((event = pullFromQueue())!=null) {
                try {
                    kbService.processInitKbEvent(event);
                }
                catch (Throwable th) {
                    log.error("Error while initialization of KB #"+event.kbId());
                }
            }
        });
    }
*/

}
