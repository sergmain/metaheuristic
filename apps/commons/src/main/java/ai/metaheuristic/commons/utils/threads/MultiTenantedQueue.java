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

import ai.metaheuristic.commons.S;
import lombok.SneakyThrows;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * @author Sergio Lissner
 * Date: 9/21/2023
 * Time: 2:11 PM
 */
public class MultiTenantedQueue<T, P extends EventWithId<T>> {

    public final int maxCapacity;
    public final long postProcessingDelay;
    public final boolean checkForDouble;
    public final String namePrefix;

    public final LinkedHashMap<T, QueueWithThread<P>> queue;

    private final ReentrantReadWriteLock queueLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock queueReadLock = queueLock.readLock();
    private final ReentrantReadWriteLock.WriteLock queueWriteLock = queueLock.writeLock();

    // if maxCapacity==-1 then max size is unbound
    public MultiTenantedQueue(int maxCapacity, Duration postProcessingDelay, boolean checkForDouble, @Nullable String namePrefix) {
        this.maxCapacity = maxCapacity;
        this.checkForDouble = checkForDouble;
        this.namePrefix = S.b(namePrefix) ? "virtual-tread-" : namePrefix;

        if (maxCapacity == -1) {
            queue = new LinkedHashMap<>() {
                protected boolean removeEldestEntry(Map.Entry<T, QueueWithThread<P>> entry) {
                    return entry.getValue().canBeRemoved();
                }
            };
        }
        else {
            queue = new LinkedHashMap<>(maxCapacity) {
                protected boolean removeEldestEntry(Map.Entry<T, QueueWithThread<P>> entry) {
                    return this.size() > maxCapacity && entry.getValue().canBeRemoved();
                }
            };
        }
        this.postProcessingDelay = postProcessingDelay.toMillis();
    }

    public void putToQueue(final P event, Consumer<P> eventConsumer) {
        putToQueueInternal(event);
        processPoolOfExecutors(event.getId(), eventConsumer);
    }

    public void clearQueue() {
        queueWriteLock.lock();
        try {
            for (Map.Entry<T, QueueWithThread<P>> entry : queue.entrySet()) {
                final QueueWithThread<P> twe = entry.getValue();
                if (twe.thread != null) {
                    try {
                        twe.thread.interrupt();
                    } catch (Throwable th) {
                        //
                    }
                }
                twe.clear();
            }
            queue.clear();
        } finally {
            queueWriteLock.unlock();
        }
    }

    public void processPoolOfExecutors(T id, Consumer<P> eventConsumer) {
        queueReadLock.lock();
        try {
            QueueWithThread<P> twe = queue.get(id);
            if (twe != null && twe.thread != null) {
                return;
            }
        } finally {
            queueReadLock.unlock();
        }

        queueWriteLock.lock();
        try {
            QueueWithThread<P> twe = queue.get(id);
            // there is nothing for processing or processing is active rn
            if (twe == null || twe.isEmpty() || twe.thread != null) {
                return;
            }

            twe.thread = Thread.ofVirtual().name(namePrefix + ThreadUtils.nextThreadNum()).start(() -> {
                try {
                    process(id, eventConsumer);
                } finally {
                    twe.thread = null;
                }
            });
        } finally {
            queueWriteLock.unlock();
        }
    }

    @SneakyThrows
    private void process(T id, Consumer<P> taskProcessor) {
        P e;
        while ((e = pullFromQueue(id)) != null) {
            taskProcessor.accept(e);
            if (postProcessingDelay>0) {
                Thread.sleep(postProcessingDelay);
            }
        }
    }

    public void putToQueueInternal(final P event) {
        QueueWithThread<P> q;
        queueWriteLock.lock();
        try {
            q = queue.computeIfAbsent(event.getId(), (o) -> new QueueWithThread<>(this.checkForDouble));
        } finally {
            queueWriteLock.unlock();
        }
        q.add(event);
    }

    @Nullable
    private P pullFromQueue(T id) {
        queueReadLock.lock();
        try {
            final QueueWithThread<P> twe = queue.get(id);
            if (twe == null) {
                return null;
            }
            return twe.pollFirst();
        } finally {
            queueReadLock.unlock();
        }
    }


}
