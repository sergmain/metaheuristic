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

package ai.metaheuristic.commons.utils.threads;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.CustomInterruptedException;
import com.google.errorprone.annotations.MustBeClosed;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;
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

    /* For auto-numbering anonymous threads. */
    private static final AtomicInteger THREAD_INIT_NUMBER = new AtomicInteger(1);
    public static int nextThreadNum() {
        return THREAD_INIT_NUMBER.incrementAndGet();
    }

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
        private final StampedLock lock = new StampedLock();
        private final Supplier<T> supplier;

        public CommonThreadLocker(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Nullable
        private T holder = null;

        public void reset(Runnable run) {
            long stamp = lock.writeLock();
            try {
                run.run();
                this.holder = null;
            } finally {
                lock.unlockWrite(stamp);
            }
        }

        @SuppressWarnings("DataFlowIssue")
        public T get() {
            long stamp = lock.tryOptimisticRead();
            T tempHolder = this.holder;

            if (tempHolder != null && lock.validate(stamp)) {
                return tempHolder;
            } else {
                stamp = lock.readLock();
                try {
                    tempHolder = this.holder;
                    if (tempHolder != null) {
                        return tempHolder;
                    }
                } finally {
                    lock.unlock(stamp);
                }
            }
            stamp = lock.writeLock();
            try {
                if (holder == null) {
                    holder = supplier.get();
                    //log.info("Parameter field of class {} was parsed.", holder.getClass().getSimpleName());
                }
            } finally {
                lock.unlock(stamp);
            }
            return holder;
        }
    }

    public static class CommonThreadLockerReadWriteBased<T> {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

        private final Supplier<T> supplier;

        public CommonThreadLockerReadWriteBased(Supplier<T> supplier) {
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
                    //log.info("Parameter field of class {} was parsed.", holder.getClass().getSimpleName());
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
                final Runtime rt = Runtime.getRuntime();
                log.debug("Threads total: {}, completed: {}. RAM free: {}, max: {}, total: {}",
                    executor.getTaskCount(), executor.getCompletedTaskCount(),
                    rt.freeMemory(), rt.maxMemory(), rt.totalMemory());
                i = 0;
            }
        }
    }

    public static void waitTaskCompleted(List<Future<?>> f, int numberOfPeriods) throws InterruptedException {
        int i = 0;
        int completed;
        while ((f.size() - (completed = completed(f))) > 0) {
            Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            if (++i % numberOfPeriods == 0) {
                final Runtime rt = Runtime.getRuntime();
                log.debug("Threads total: {}, completed: {}. RAM free: {}, max: {}, total: {}",
                    f.size(), completed,
                    rt.freeMemory(), rt.maxMemory(), rt.totalMemory());
                i = 0;
            }
        }
    }

    public static int completed(List<Future<?>> f) {
        int r = 0;
        for (Future<?> future : f) {
            if (future.isDone()) {
                r++;
            }
        }
        return r;
    }

    public static long execStat(long mills, ThreadPoolExecutor executor) {
        return execStat(mills, executor.getTaskCount());
    }

    public static long execStat(long mills, long taskCount) {
        final long curr = System.currentTimeMillis();
        if (log.isInfoEnabled()) {
            final int sec = (int) ((curr - mills) / 1000);
            String s = S.f("\nprocessed %d tasks for %d seconds", taskCount, sec);
            if (sec!=0) {
                s += (", " + (((int) taskCount / sec)) + " tasks/sec");
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
        Thread t = Thread.ofVirtual().start(()-> {
            Matcher matcher = p.matcher(new InterruptableCharSequence(text));
            if (matcher.find()) {
                result[0] = matcher.group();
            }
            done.set(true);
        });
        t.join(timeout.toMillis());
        long endMills = System.currentTimeMillis();
        long time = endMills - mills;
        return done.get() ? new PatternMatcherResultWithTimeout(result[0], false) : new PatternMatcherResultWithTimeout(null, true);
    }


}
