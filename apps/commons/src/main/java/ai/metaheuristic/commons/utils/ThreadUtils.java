/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CustomInterruptedException;
import com.google.errorprone.annotations.MustBeClosed;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergio Lissner
 * Date: 11/3/2022
 * Time: 12:08 PM
 */
@Slf4j
public class ThreadUtils {

    public static final class ResourceLock extends ReentrantLock implements AutoCloseable {
        @MustBeClosed
        public ResourceLock obtain() {
            lock();
            return this;
        }

        @Override
        public void close() {
            this.unlock();
        }
    }
//    	try (ResourceLock ignored = lock.obtain()) {
//        // critical section
//    }

    public static class CommonThreadLocker<T> {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

        private final Supplier<T> supplier;

        public CommonThreadLocker(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Nullable
        private T holder = null;

        public void reset(Runnable run) {
            try {
                writeLock.lock();
                run.run();
                this.holder = null;
            } finally {
                writeLock.unlock();
            }
        }

        @SuppressWarnings("DataFlowIssue")
        public T get() {
            try {
                readLock.lock();
                if (holder != null) {
                    return holder;
                }
            } finally {
                readLock.unlock();
            }

            try {
                writeLock.lock();
                if (holder == null) {
                    holder = supplier.get();
                }
            } finally {
                writeLock.unlock();
            }
            return holder;
        }
    }

    public record PatternMatcherResultWithTimeout(@Nullable String s, boolean withTimeout) {}

    // https://stackoverflow.com/a/910798/2672202
    public static class InterruptableCharSequence implements CharSequence {
        CharSequence inner;
        public InterruptableCharSequence(CharSequence inner) {
            this.inner = inner;
        }

        public char charAt(int index) {
            if (Thread.interrupted()) {
                throw new RuntimeException(new InterruptedException());
            }
            return inner.charAt(index);
        }

        public int length() {
            return inner.length();
        }

        public CharSequence subSequence(int start, int end) {
            return new InterruptableCharSequence(inner.subSequence(start, end));
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }

    public static class ThreadedPool<T> {
        private final int maxThreadInPool;

        // 0 is for unbound queue
        private final int maxQueueSize;
        private final Consumer<T> process;

        private final ThreadPoolExecutor executor;
        private final LinkedList<T> queue = new LinkedList<>();

        private final ReentrantReadWriteLock queueReadWriteLock = new ReentrantReadWriteLock();
        private final ReentrantReadWriteLock.WriteLock queueWriteLock = queueReadWriteLock.writeLock();

        public ThreadedPool(int maxQueueSize, Consumer<T> process) {
            this.maxThreadInPool = 1;
            this.maxQueueSize = maxQueueSize;
            this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxThreadInPool);
            this.process = process;
        }

        public ThreadedPool(int maxThreadInPool, int maxQueueSize, Consumer<T> process) {
            this.maxThreadInPool = maxThreadInPool;
            this.maxQueueSize = maxQueueSize;
            this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxThreadInPool);
            this.process = process;
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

            queueWriteLock.lock();
            try {
                queue.add(event);
            }
            finally {
                queueWriteLock.unlock();
            }
            processEvent();
        }

        @Nullable
        private T pullFromQueue() {
            queueWriteLock.lock();
            try {
                return queue.pollFirst();
            }
            finally {
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
            if (executor.getActiveCount()>=maxThreadInPool) {
                return;
            }
            executor.submit(this::actualProcessing);
        }

        private void actualProcessing() {
            T event;
            while ((event = pullFromQueue())!=null) {
                try {
                    process.accept(event);
                }
                catch (Throwable th) {
                    log.error("207.040 Error while processing queue: "+ th.getMessage(), th);
                }
            }
        }
    }

    public static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CustomInterruptedException();
        }
    }

    public static void waitTaskCompleted(ThreadPoolExecutor executor) throws InterruptedException {
        waitTaskCompleted(executor, 20);
    }

    public static void waitTaskCompleted(ThreadPoolExecutor executor, int numberOfPeriods) throws InterruptedException {
        int i = 0;
        while ((executor.getTaskCount() - executor.getCompletedTaskCount()) > 0) {
            Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            if (++i % numberOfPeriods == 0) {
                System.out.print("total: " + executor.getTaskCount() + ", completed: " + executor.getCompletedTaskCount());
                final Runtime rt = Runtime.getRuntime();
                System.out.println(", free: " + rt.freeMemory() + ", max: " + rt.maxMemory() + ", total: " + rt.totalMemory());
                i = 0;
            }
        }
    }

    public static long execStat(long mills, ThreadPoolExecutor executor) {
        final long curr = System.currentTimeMillis();
        if (log.isInfoEnabled()) {
            final int sec = (int) ((curr - mills) / 1000);
            String s = S.f("\nprocessed %d tasks for %d seconds", executor.getTaskCount(), sec);
            if (sec!=0) {
                s += (", " + (((int) executor.getTaskCount() / sec)) + " tasks/sec");
            }
            log.info(s);
        }
        return curr;
    }

    @SneakyThrows
    public static PatternMatcherResultWithTimeout matchPattern(Pattern p, String text, Duration timeout) {
        final String[] result = new String[1];
        result[0] = null;
        final AtomicBoolean done = new AtomicBoolean(false);
        long mills = System.currentTimeMillis();
        Thread t = new Thread(()-> {
            Matcher matcher = p.matcher(new InterruptableCharSequence(text));
            if (matcher.find()) {
                result[0] = matcher.group();
            }
            done.set(true);
        });
        t.start();
        t.join(timeout.toMillis());
        long endMills = System.currentTimeMillis();
        long time = endMills - mills;
        return done.get() ? new PatternMatcherResultWithTimeout(result[0], false) : new PatternMatcherResultWithTimeout(null, true);
    }


}
