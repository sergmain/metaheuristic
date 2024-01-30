/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.DispatcherRequestorHolderService;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.processor.dispatcher_selection.ActiveDispatchers;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

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
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ProcessorEventBusService {

    private final DispatcherRequestorHolderService dispatcherRequestorHolderService;
    private final ProcessorEnvironment processorEnvironment;
    private ActiveDispatchers activeDispatchers;

    private final Map<String, ThreadPoolExecutor> executors = new HashMap<>();
    private final Map<String, ThreadPoolExecutor> functionExecutors = new HashMap<>();

    private boolean shutdown = false;

    @PostConstruct
    public void post() {
        this.activeDispatchers = new ActiveDispatchers(processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap, "RoundRobin for KeepAlive", Enums.DispatcherSelectionStrategy.alphabet);
        for (Map.Entry<ProcessorAndCoreData.DispatcherUrl, AtomicBoolean> entry : activeDispatchers.getActiveDispatchers().entrySet()) {
            // TODO p5 2023-10-30 do we need to switch to a virtual threads?
            // TODO p0 2023-11-16 yes, it needs to be switched to virtual threads
            executors.put(entry.getKey().url, (ThreadPoolExecutor) Executors.newFixedThreadPool(1));
            functionExecutors.put(entry.getKey().url, (ThreadPoolExecutor) Executors.newFixedThreadPool(1));
        }
    }

    @PreDestroy
    public void onExit() {
        shutdown = true;
        for (Map.Entry<ProcessorAndCoreData.DispatcherUrl, AtomicBoolean> entry : activeDispatchers.getActiveDispatchers().entrySet()) {
            ThreadPoolExecutor executor = executors.get(entry.getKey().url);
            executor.shutdownNow();
            entry.getValue().set(false);
        }
    }

    public void keepAlive(KeepAliveEvent event) {
        if (shutdown) {
            return;
        }
        try {
            Map<ProcessorAndCoreData.DispatcherUrl, AtomicBoolean> dispatchers = activeDispatchers.getActiveDispatchers();
            if (dispatchers.isEmpty()) {
                log.info("047.030 Can't find any enabled dispatcher");
                return;
            }
            // TODO 2020-11-22 do we need to convert Set to List and sort it?
            // TODO 2021-11-10 actually, activeDispatchers.getActiveDispatchers() returns a map, which is unmodifiable LinkedHashMap
            //  so we don't need to sort this map.
            for (ProcessorAndCoreData.DispatcherUrl dispatcher : dispatchers.keySet()) {
                ThreadPoolExecutor executor = executors.get(dispatcher.url);
                if (executor==null) {
                    log.error("047.060 ThreadPoolExecutor wasn't found, need to investigate");
                    continue;
                }
                int activeCount = executor.getActiveCount();
                if (activeCount >0) {
                    log.warn("047.090 executor has a not finished tasks, count: {}", activeCount);
                    continue;
                }

                Thread t = new Thread(() -> {
                    log.info("Call processorKeepAliveRequestor, url: {}", dispatcher.url);
                    try {
                        dispatcherRequestorHolderService.dispatcherRequestorMap.get(dispatcher).processorKeepAliveRequestor.proceedWithRequest();
                    } catch (Throwable th) {
                        log.error("047.120 ProcessorEventBusService.keepAlive()", th);
                    }
                }, "ProcessorEventBusService-" + ThreadUtils.nextThreadNum());
                executor.submit(t);
            }
        } catch (Throwable th) {
            log.error("047.150 Error, need to investigate ", th);
        }
    }

    public void interactWithFunctionRepository() {
        if (shutdown) {
            return;
        }
        try {
            Map<ProcessorAndCoreData.DispatcherUrl, AtomicBoolean> dispatchers = activeDispatchers.getActiveDispatchers();
            if (dispatchers.isEmpty()) {
                log.info("047.180 Can't find any enabled dispatcher");
                return;
            }
            for (ProcessorAndCoreData.DispatcherUrl dispatcher : dispatchers.keySet()) {
                ThreadPoolExecutor executor = functionExecutors.get(dispatcher.url);
                if (executor==null) {
                    log.error("047.210 interactWithFunctionRepository wasn't found, need to investigate");
                    continue;
                }
                int activeCount = executor.getActiveCount();
                if (activeCount>0) {
                    log.warn("047.240 executor has a not finished tasks, count: {}", activeCount);
                    continue;
                }

                Thread t = new Thread(() -> {
                    // log.info("Call interactWithFunctionRepository, url: {}", dispatcher.url);
                    try {
                        dispatcherRequestorHolderService.dispatcherRequestorMap.get(dispatcher).functionRepositoryRequestor.requestFunctionRepository();
                    } catch (Throwable th) {
                        log.error("047.270 ProcessorEventBusService.interactWithFunctionRepository()", th);
                    }
                }, "ProcessorEventBusService-" + ThreadUtils.nextThreadNum());
                executor.submit(t);
            }
        } catch (Throwable th) {
            log.error("047.300 Error, need to investigate ", th);
        }
    }
}
