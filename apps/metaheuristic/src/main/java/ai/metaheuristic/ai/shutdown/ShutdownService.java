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
 * @author Sergio Lissner
 * Date: 4/24/2026
 * Time: 8:39 PM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ShutdownService {

    public final List<ShutdownInterface> shutdowns;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @PreDestroy
    public void preDestroy() {
        log.warn("start ShutdownService.preDestroy(), count: {}", shutdowns.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (ShutdownInterface shutdown : shutdowns) {
                log.warn("inform "+shutdown.getClass().getSimpleName()+" about shutdown");
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        shutdown.shutdown();
                    } catch (Throwable t) {
                        log.error("Error during shutdown of " + shutdown.getClass().getSimpleName(), t);
                    }
                }, executor));
            }
            // Block until EVERY ShutdownInterface.shutdown() has returned. The previous
            // code started the virtual threads fire-and-forget and returned immediately,
            // so @PreDestroy completed (and the Spring context "closed") while the drains
            // were still running. On Windows that left in-flight Lucene IndexWriters open
            // and blocked @TempDir cleanup. Joining here makes context-close wait for the
            // drains to finish before resources are considered released.
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }
}
