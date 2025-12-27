/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.commons.CommonSync;
import ai.metaheuristic.commons.S;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.lang.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Serge
 * Date: 9/25/2020
 * Time: 4:43 PM
 */
@Execution(CONCURRENT)
public class TestCommonSync {

    private static final int DURATION = 10;

    public static class TestSync {

        private static final CommonSync<Long> commonSync = new CommonSync<>();

        public static void checkWriteLockPresent(Long execContextId) {
            if (!getWriteLock(execContextId).isHeldByCurrentThread()) {
                throw new IllegalStateException("#977.020 Must be locked by WriteLock");
            }
        }

        public static ReentrantReadWriteLock getLock(Long execContextId) {
            return commonSync.getLock(execContextId);
        }

        public static ReentrantReadWriteLock.WriteLock getWriteLock(Long execContextId) {
            return commonSync.getWriteLock(execContextId);
        }

        private static ReentrantReadWriteLock.ReadLock getReadLock(Long execContextId) {
            return commonSync.getReadLock(execContextId);
        }

        public static <T> T getWithSync(Long execContextId, Supplier<T> supplier) {
            final ReentrantReadWriteLock.WriteLock lock = getWriteLock(execContextId);
            try {
                lock.lock();
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }

        @Nullable
        public static <T> T getWithSyncNullable(Long execContextId, Supplier<T> supplier) {
            final ReentrantReadWriteLock.WriteLock lock = getWriteLock(execContextId);
            try {
                lock.lock();
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }

        public static <T> T getWithSyncReadOnly(ExecContextImpl execContext, Supplier<T> supplier) {
            final ReentrantReadWriteLock.ReadLock lock = getReadLock(execContext.id);
            try {
                lock.lock();
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }
    }

    @RepeatedTest(200)
    public void test() throws InterruptedException {

        Thread t1 = null;
        Thread t2 = null;
        Thread t3 = null;

        try {
            TestSync testSync = new TestSync();

            final AtomicBoolean isRun = new AtomicBoolean(true);
            final AtomicBoolean isRun2 = new AtomicBoolean(true);
            final AtomicBoolean isRun3 = new AtomicBoolean(true);

            final AtomicBoolean isStarted = new AtomicBoolean(false);
            final AtomicBoolean isStarted2 = new AtomicBoolean(false);
            final AtomicBoolean isStarted3 = new AtomicBoolean(false);

            final AtomicLong started1 = new AtomicLong();
            final AtomicLong started2 = new AtomicLong();
            final AtomicLong started3 = new AtomicLong();

            final AtomicLong finished1 = new AtomicLong();
            final AtomicLong finished2 = new AtomicLong();
            final AtomicLong finished3 = new AtomicLong();


            t1 = new Thread(() -> {
                TestSync.getWithSyncNullable(42L, () -> {
                    isStarted.set(true);
                    try {
                        while (isRun.get()) {
                            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(DURATION));
                        }
                    } catch (InterruptedException e) {
//                        ExceptionUtils.rethrow(e);
                    }
                    finished1.set(System.currentTimeMillis());
                    return null;
                });
                isStarted.set(false);
            }, "My thread #1");
            t1.start();

            waitForStartingThread(isStarted, "t1");

            AtomicBoolean insideSync2 = new AtomicBoolean();
            t2 = new Thread(() -> {
                isStarted2.set(true);
                TestSync.getWithSyncNullable(42L, () -> {
                    started2.set(System.currentTimeMillis());
                    insideSync2.set(true);
                    try {
                        while (isRun2.get()) {
                            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(DURATION));
                        }
                    } catch (InterruptedException e) {
//                        ExceptionUtils.rethrow(e);
                    }
                    finished2.set(System.currentTimeMillis());
                    return null;
                });
                isStarted2.set(false);
            }, "My thread #2");
            t2.start();

            AtomicBoolean insideSync3 = new AtomicBoolean();
            t3 = new Thread(() -> {
                isStarted3.set(true);
                TestSync.getWithSyncNullable(42L, () -> {
                    started3.set(System.currentTimeMillis());
                    insideSync3.set(true);
                    try {
                        while (isRun3.get()) {
                            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(DURATION));
                        }
                    } catch (InterruptedException e) {
//                        ExceptionUtils.rethrow(e);
                    }
                    finished3.set(System.currentTimeMillis());
                    return null;
                });
                isStarted3.set(false);
            }, "My thread #3");
            t3.start();

            waitForStartingThreads("t2, t3", isStarted2, isStarted3);
            assertFalse(insideSync2.get());
            assertFalse(insideSync3.get());

            isRun.set(false);
            waitForStoppingThread(isStarted, "t1");

            waitForAnyInsideThreads("t2, t3", insideSync2, insideSync3);

            final boolean b = insideSync2.get();
            final boolean b1 = insideSync3.get();
            assertNotEquals(b, b1, S.f("insideSync2.get(): %s, insideSync3.get(): %s", b, b1));

            if (insideSync2.get()) {
                assertTrue(started2.get() >= finished1.get(), S.f("started2: %d, finished1: %d", started2.get(), finished1.get()));
            }
            else if (insideSync3.get()) {
                assertTrue(started3.get() >= finished1.get(), S.f("started3: %d, finished1: %d", started3.get(), finished1.get()));
            }
            else {
                throw new IllegalStateException("something wrong");
            }

            isRun2.set(false);
            isRun3.set(false);
            waitForStoppingThreads("t2, t3", isStarted2, isStarted3);

            boolean lockOk = false;
            if (!TestSync.getWriteLock(42L).isHeldByCurrentThread()) {
                lockOk = true;
            }

            assertTrue(lockOk);
            final ReentrantReadWriteLock.WriteLock writeLock = TestSync.getWriteLock(42L);
            final boolean condition = writeLock.tryLock();
            assertTrue(condition);
            writeLock.unlock();
        }
        finally {
            terminateThread(t1);
            terminateThread(t2);
            terminateThread(t3);
        }
    }

    private static void terminateThread(@Nullable Thread t) {
        if (t!=null) {
            t.interrupt();
        }
    }

    private static void waitForStoppingThread(AtomicBoolean isStarted, String threadName) throws InterruptedException {
        long mills;
        mills = System.currentTimeMillis();
        while (isStarted.get()) {
            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(DURATION));
            if (System.currentTimeMillis() - mills > 15_000) {
                throw new IllegalStateException("Thread "+threadName+" wasn't ended in 15 seconds");
            }
        }
    }

    private static void waitForStoppingThreads(String threadNames, AtomicBoolean... isStarteds) throws InterruptedException {
        long mills;
        mills = System.currentTimeMillis();
        while (isAny(isStarteds)) {
            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(DURATION));
            if (System.currentTimeMillis() - mills > 15_000) {
                throw new IllegalStateException("Threads "+threadNames+" weren't stopped in 15 seconds");
            }
        }
    }

    private static void waitForAnyInsideThreads(String threadNames, AtomicBoolean... isStarteds) throws InterruptedException {
        long mills;
        mills = System.currentTimeMillis();
        while (!isAny(isStarteds)) {
            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(DURATION));
            if (System.currentTimeMillis() - mills > 15_000) {
                throw new IllegalStateException("Any thread "+threadNames+" wasn't stepped inside in 15 seconds");
            }
        }
    }

    private static void waitForStartingThread(AtomicBoolean isStarted, String threadName) throws InterruptedException {
        long mills;
        mills = System.currentTimeMillis();
        while (!isStarted.get()) {
            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(DURATION));
            if (System.currentTimeMillis() - mills > 15_000) {
                throw new IllegalStateException("Thread "+threadName+" wasn't started in 15 seconds");
            }
        }
    }

    private static void waitForStartingThreads(String threadName, AtomicBoolean... isStarteds) throws InterruptedException {
        long mills;
        mills = System.currentTimeMillis();
        while (!isStarted(isStarteds)) {
            Thread.sleep(TimeUnit.MILLISECONDS.toMillis(DURATION));
            if (System.currentTimeMillis() - mills > 15_000) {
                throw new IllegalStateException("Threads "+threadName+" weren't started in 15 seconds");
            }
        }
    }

    private static boolean isStarted(AtomicBoolean... isStarteds) {
        for (AtomicBoolean isStarted : isStarteds) {
            if (!isStarted.get()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAny(AtomicBoolean... isStarteds) {
        for (AtomicBoolean isStarted : isStarteds) {
            if (isStarted.get()) {
                return true;
            }
        }
        return false;
    }
}
