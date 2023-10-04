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

package ai.metaheuristic.commons.utils.threads;

import ai.metaheuristic.api.ConstsApi;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * @author Sergio Lissner
 * Date: 6/13/2023
 * Time: 6:07 PM
 */
@Slf4j
public class ThreadedPool<I, T extends EventWithId<I>> {

    private final Consumer<T> process;

    private boolean shutdown = false;

    private final MultiTenantedQueue<I, T> queue;

    public ThreadedPool(String namePrefix, int maxThreadInPool, boolean immediateProcessing, boolean checkForDouble, Consumer<T> process, Duration postProcessingDelay) {
        this.queue = new MultiTenantedQueue<>(maxThreadInPool, postProcessingDelay, checkForDouble, namePrefix);
        this.process = process;
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public void shutdown() {
        shutdown = true;
        queue.clearQueue();
    }

    public void putToQueue(final T event) {
        if (shutdown) {
            return;
        }
        queue.putToQueue(event, process);
    }
}
