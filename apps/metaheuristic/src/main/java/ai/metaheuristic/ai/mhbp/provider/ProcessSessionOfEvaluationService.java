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

package ai.metaheuristic.ai.mhbp.provider;

import ai.metaheuristic.ai.mhbp.events.EvaluateProviderEvent;
import ai.metaheuristic.commons.utils.threads.ThreadedPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author Sergio Lissner
 * Date: 3/19/2023
 * Time: 10:46 PM
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Profile("dispatcher")
public class ProcessSessionOfEvaluationService {

    public final ProviderQueryService providerQueryService;

    private final ThreadedPool<EvaluateProviderEvent> evaluateProviderEventThreadedPool =
            new ThreadedPool<>(1, 0, false, true, providerQueryService::evaluateProvider);

    @Async
    @EventListener
    public void handleEvaluateProviderEvent(EvaluateProviderEvent event) {
        evaluateProviderEventThreadedPool.putToQueue(event);
    }

    public void processSessionEvent() {
        evaluateProviderEventThreadedPool.processEvent();
    }
/*
//    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
//    private final LinkedList<EvaluateProviderEvent> queue = new LinkedList<>();

    @Async
    @EventListener
    public void handleEvaluateProviderEvent(EvaluateProviderEvent event) {
        evaluateProviderEventThreadedPool.putToQueue(event);
        putToQueue(event);
    }

    public void putToQueue(final EvaluateProviderEvent event) {
        synchronized (queue) {
            if (queue.contains(event)) {
                return;
            }
            queue.add(event);
        }
    }

    @Nullable
    private EvaluateProviderEvent pullFromQueue() {
        synchronized (queue) {
            return queue.pollFirst();
        }
    }

    public void processSessionEvent() {
        if (executor.getActiveCount()>0) {
            return;
        }
        executor.submit(() -> {
            EvaluateProviderEvent event;
            while ((event = pullFromQueue())!=null) {
                providerQueryService.evaluateProvider(event);
            }
        });
    }
*/

}
