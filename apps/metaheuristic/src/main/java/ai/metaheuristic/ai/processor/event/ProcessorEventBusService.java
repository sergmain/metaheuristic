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

package ai.metaheuristic.ai.processor.event;

import ai.metaheuristic.ai.dispatcher.commons.RoundRobinForDispatcher;
import ai.metaheuristic.ai.processor.DispatcherLookupExtendedService;
import ai.metaheuristic.ai.processor.DispatcherRequestorHolderService;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Serge
 * Date: 11/22/2020
 * Time: 10:26 PM
 */
@SuppressWarnings("unused")
@Service
@EnableScheduling
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
//@DependsOn({"DispatcherLookupExtendedService"})
public class ProcessorEventBusService {

    private final DispatcherRequestorHolderService dispatcherRequestorHolderService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private RoundRobinForDispatcher roundRobin;

    private ThreadPoolExecutor executor;

    @PostConstruct
    public void post() {
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Math.min(4, dispatcherLookupExtendedService.lookupExtendedMap.size()));
        this.roundRobin = new RoundRobinForDispatcher(dispatcherLookupExtendedService.lookupExtendedMap);
    }

    @Async
    @EventListener
    public void keepAlive(KeepAliveEvent event) {

        // TODO 2020-11-22 do we need to convert Set to List and sort it?
        Set<ProcessorAndCoreData.DispatcherUrl> dispatchers = roundRobin.getActiveDispatchers();
        if (dispatchers.isEmpty()) {
            log.info("Can't find any enabled dispatcher");
            return;
        }
        int activeCount = executor.getActiveCount();
        if (activeCount >0) {
            log.error("#047.020 executor has not finished tasks, count: {}", activeCount);
        }
        for (ProcessorAndCoreData.DispatcherUrl dispatcher : dispatchers) {
            executor.submit(() -> {
                log.info("processorKeepAliveRequestor.proceedWithRequest(), url: {}", dispatcher);
                try {
                    dispatcherRequestorHolderService.dispatcherRequestorMap.get(dispatcher).processorKeepAliveRequestor.proceedWithRequest();
                } catch (Throwable th) {
                    log.error("ProcessorSchedulers.dispatcherRequester()", th);
                }
            });

        }
    }

}
