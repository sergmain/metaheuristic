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
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.StampedLock;
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

//    private final ThreadPoolExecutor executor;
    private final LinkedList<T> queue = new LinkedList<>();
    private final Semaphore semaphore;

    private boolean shutdown = false;
    private final StampedLock lock = new StampedLock();

//    private final ReentrantReadWriteLock queueReadWriteLock = new ReentrantReadWriteLock();
//    private final ReentrantReadWriteLock.ReadLock queueReadLock = queueReadWriteLock.readLock();
//    private final ReentrantReadWriteLock.WriteLock queueWriteLock = queueReadWriteLock.writeLock();

    public ThreadedPool(int maxThreadInPool, int maxQueueSize, boolean immediateProcessing, boolean checkForDouble, Consumer<T> process) {
        this.maxThreadInPool = maxThreadInPool;
        this.immediateProcessing = immediateProcessing;
        this.checkForDouble = checkForDouble;
        this.maxQueueSize = maxQueueSize;
        this.semaphore = new Semaphore(maxThreadInPool);
//        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxThreadInPool);
        this.process = process;
    }

    public boolean isShutdown() {
//        queueReadLock.lock();
//        try {
//            return shutdown;
//        }
//        finally {
//            queueReadLock.unlock();
//        }
        long stamp = lock.tryOptimisticRead();
        var tempHolder = this.shutdown;

        if (lock.validate(stamp)) {
            return tempHolder;
        }
        else {
            stamp = lock.readLock();
            try {
                return this.shutdown;
            } finally {
                lock.unlock(stamp);
            }
        }

    }

    public void shutdown() {
/*
        queueWriteLock.lock();
        try {
            shutdown = true;
            queue.clear();
        } finally {
            queueWriteLock.unlock();
        }
        semaphore.release();
*/
        //executor.shutdownNow();

        long stamp = lock.writeLock();
        try {
            if (shutdown){
                return;
            }
            shutdown = true;
            queue.clear();
        } finally {
            lock.unlock(stamp);
        }

    }

    public void putToQueue(final T event) {
//        final int activeCount = executor.getActiveCount();
        final int activeCount = getActiveCount();
        final boolean queuedThreads = semaphore.hasQueuedThreads();
        if (log.isDebugEnabled()) {
//            final long completedTaskCount = executor.getCompletedTaskCount();
//            final long taskCount = executor.getTaskCount();
//            log.debug("putToQueue({}), active task in executor: {}, awaiting tasks: {}", event.getClass().getSimpleName(), activeCount, taskCount - completedTaskCount);
            log.debug("putToQueue({}), active task in executor: {}", event.getClass().getSimpleName(), activeCount);
        }

        if (maxQueueSize!=0 && (activeCount>0 || queue.size()>maxQueueSize)) {
            return;
        }

        long stamp;
        if (checkForDouble) {
            stamp = lock.readLock();
            try {
                if (shutdown) {
                    return;
                }
                if (queue.contains(event)) {
                    return;
                }
            } finally {
                lock.unlock(stamp);
            }
        }

        stamp = lock.writeLock();
        try {
            if (checkForDouble && queue.contains(event)) {
                return;
            }
            queue.add(event);
        } finally {
            lock.unlock(stamp);
        }

        if (immediateProcessing) {
            processEvent();
        }
    }

    @Nullable
    private T pullFromQueue() {
        long stamp = lock.writeLock();
        try {
            if (shutdown) {
                return null;
            }
            return queue.pollFirst();
        } finally {
            lock.unlock(stamp);
        }

/*
        queueWriteLock.lock();
        try {
            if (shutdown) {
                return null;
            }
            return queue.pollFirst();
        } finally {
            queueWriteLock.unlock();
        }
*/
    }

    public int getActiveCount() {
//        return executor.getActiveCount();
        final int availablePermits = semaphore.availablePermits();
        final int activeCount = maxThreadInPool - availablePermits;
        return activeCount;
    }

    public int getQueueSize() {
        return queue.size();
    }

    @SneakyThrows
    public void processEvent() {
        if (isShutdown()) {
            return;
        }
//        if (getActiveCount()>=maxThreadInPool) {
//            return;
//        }
        boolean acquired = semaphore.tryAcquire();
        if (!acquired) {
            return;
        }
        try {
            actualProcessing();
            //
        } finally {
            semaphore.release();
        }
//        semaphore.submit(this::actualProcessing);
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
