/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.shutdown;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Processor-side counterpart of {@link ShutdownService}: on context close, informs every
 * {@link ProcessorShutdownInterface} bean about the shutdown and waits until each one has returned.
 *
 * @author Sergio Lissner
 * Date: 10/7/2026
 */
@Service
@Profile("processor")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ProcessorShutdownService {

    public final List<ProcessorShutdownInterface> shutdowns;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @PreDestroy
    public void preDestroy() {
        try {
            preDestroyInternal();
        } finally {
            try {
                shutdowns.clear();
            } catch (Throwable e) {
                //
            }
        }
    }

    public void preDestroyInternal() {
        log.warn("start ProcessorShutdownService.preDestroy(), count: {}", shutdowns.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (ProcessorShutdownInterface shutdown : shutdowns) {
                log.warn("inform {} about shutdown", shutdown.getClass().getSimpleName());
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        shutdown.shutdown();
                    } catch (Throwable t) {
                        log.error("Error during shutdown of " + shutdown.getClass().getSimpleName(), t);
                    }
                }, executor));
            }
            // Block until EVERY ProcessorShutdownInterface.shutdown() has returned, so that
            // @PreDestroy (and therefore context close) completes only after all of them are done.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }
}
