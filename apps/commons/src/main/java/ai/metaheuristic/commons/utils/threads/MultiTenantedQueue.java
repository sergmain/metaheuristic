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

    public final int initialCapacity;
    public final long postProcessingDelay;

    public final LinkedHashMap<T, QueueWithThread<P>> queue;

    private final ReentrantReadWriteLock queueLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock queueReadLock = queueLock.readLock();
    private final ReentrantReadWriteLock.WriteLock queueWriteLock = queueLock.writeLock();

    public MultiTenantedQueue(int initialCapacity, Duration postProcessingDelay) {
        this.initialCapacity = initialCapacity;

        queue = new LinkedHashMap<>(initialCapacity) {
            protected boolean removeEldestEntry(Map.Entry<T, QueueWithThread<P>> entry) {
                return this.size() > initialCapacity && entry.getValue().canBeRemoved();
            }
        };
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
            if (twe == null || twe.isEmpty() || twe.thread != null) {
                return;
            }
            twe.thread = new Thread(() -> {
                try {
                    process(id, eventConsumer);
                } finally {
                    twe.thread = null;
                }
            }, "TaskWithInternalContextEventService-" + ThreadUtils.nextThreadNum());
            twe.thread.start();
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
            q = queue.computeIfAbsent(event.getId(), (o) -> new QueueWithThread<>());
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
