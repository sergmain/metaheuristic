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

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.LinkedList;
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
public class ThreadedPool<T> {
    private final int maxThreadInPool;

    // 0 is for unbound queue
    private final int maxQueueSize;
    private final boolean immediateProcessing;
    private final boolean checkForDouble;
    private final Consumer<T> process;
    private final String namePrefix;

    private final ThreadPoolExecutor executor;
    private final LinkedList<T> queue = new LinkedList<>();

    private boolean shutdown = false;

    private final ReentrantReadWriteLock queueReadWriteLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock queueReadLock = queueReadWriteLock.readLock();
    private final ReentrantReadWriteLock.WriteLock queueWriteLock = queueReadWriteLock.writeLock();

    public ThreadedPool(String namePrefix, int maxQueueSize, Consumer<T> process) {
        this(namePrefix, 1, maxQueueSize, true, false, process);
    }

    public ThreadedPool(String namePrefix, int maxThreadInPool, int maxQueueSize, Consumer<T> process) {
        this(namePrefix, maxThreadInPool, maxQueueSize, true, false, process);
    }

    public ThreadedPool(String namePrefix, int maxThreadInPool, int maxQueueSize, boolean immediateProcessing, boolean checkForDouble, Consumer<T> process) {
        this.maxThreadInPool = maxThreadInPool;
        this.maxQueueSize = maxQueueSize;
        this.immediateProcessing = immediateProcessing;
        this.checkForDouble = checkForDouble;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxThreadInPool);
        this.process = process;
        this.namePrefix = namePrefix;
    }

    public boolean isShutdown() {
        queueReadLock.lock();
        try {
            return shutdown;
        }
        finally {
            queueReadLock.unlock();
        }
    }

    public void shutdown() {
        queueWriteLock.lock();
        try {
            shutdown = true;
            queue.clear();
        } finally {
            queueWriteLock.unlock();
        }
        executor.shutdownNow();
    }

    public void putToQueue(final T event) {
        final int activeCount = executor.getActiveCount();
        if (log.isDebugEnabled()) {
            final long completedTaskCount = executor.getCompletedTaskCount();
            final long taskCount = executor.getTaskCount();
            log.debug("putToQueue({}), active task in executor: {}, awaiting tasks: {}", event.getClass().getSimpleName(), activeCount, taskCount - completedTaskCount);
        }

        if (maxQueueSize!=0 && (activeCount>0 || queue.size()>maxQueueSize)) {
            return;
        }

        if (checkForDouble) {
            queueReadLock.lock();
            try {
                if (shutdown) {
                    return;
                }
                if (queue.contains(event)) {
                    return;
                }
            }
            finally {
                queueReadLock.unlock();
            }
        }

        queueWriteLock.lock();
        try {
            if (checkForDouble && queue.contains(event)) {
                return;
            }
            queue.add(event);
        } finally {
            queueWriteLock.unlock();
        }
        if (immediateProcessing) {
            processEvent();
        }
    }

    @Nullable
    private T pullFromQueue() {
        queueWriteLock.lock();
        try {
            if (shutdown) {
                return null;
            }
            return queue.pollFirst();
        } finally {
            queueWriteLock.unlock();
        }
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    public int getQueueSize() {
        return queue.size();
    }

    public void processEvent() {
        if (isShutdown()) {
            return;
        }
        if (executor.getActiveCount()>=maxThreadInPool) {
            return;
        }
        Thread t = new Thread(this::actualProcessing, namePrefix+ + ThreadUtils.nextThreadNum());
        executor.submit(t);
    }

    private void actualProcessing() {
        T event;
        while (!shutdown && (event = pullFromQueue()) != null) {
            try {
                process.accept(event);
            }
            catch (Throwable th) {
                log.error("207.040 Error while processing queue: "+ th.getMessage(), th);
            }
        }
    }
}
