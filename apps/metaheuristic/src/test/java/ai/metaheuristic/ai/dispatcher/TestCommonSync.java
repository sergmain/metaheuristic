/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import ai.metaheuristic.commons.S;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 9/25/2020
 * Time: 4:43 PM
 */
public class TestCommonSync {

    public static class TestSync {

        private static final CommonSync<Long> commonSync = new CommonSync<>();

        public void checkWriteLockPresent(Long execContextId) {
            if (!getWriteLock(execContextId).isHeldByCurrentThread()) {
                throw new IllegalStateException("#977.020 Must be locked by WriteLock");
            }
        }

        public ReentrantReadWriteLock getLock(Long execContextId) {
            return commonSync.getLock(execContextId);
        }

        public ReentrantReadWriteLock.WriteLock getWriteLock(Long execContextId) {
            return commonSync.getWriteLock(execContextId);
        }

        private ReentrantReadWriteLock.ReadLock getReadLock(Long execContextId) {
            return commonSync.getReadLock(execContextId);
        }

        public <T> T getWithSync(Long execContextId, Supplier<T> supplier) {
            final ReentrantReadWriteLock.WriteLock lock = getWriteLock(execContextId);
            try {
                lock.lock();
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }

        @Nullable
        public <T> T getWithSyncNullable(Long execContextId, Supplier<T> supplier) {
            final ReentrantReadWriteLock.WriteLock lock = getWriteLock(execContextId);
            try {
                lock.lock();
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }

        public <T> T getWithSyncReadOnly(ExecContextImpl execContext, Supplier<T> supplier) {
            final ReentrantReadWriteLock.ReadLock lock = getReadLock(execContext.id);
            try {
                lock.lock();
                return supplier.get();
            } finally {
                lock.unlock();
            }
        }
    }

    TestSync testSync = new TestSync();

    @Test
    public void test() throws InterruptedException {

        final AtomicBoolean isRun = new AtomicBoolean(true);
        final AtomicBoolean isRun2 = new AtomicBoolean(true);
        final AtomicBoolean isRun3 = new AtomicBoolean(true);
        final AtomicBoolean isStarted = new AtomicBoolean(false);
        final AtomicBoolean isStarted2 = new AtomicBoolean(false);
        final AtomicBoolean isStarted3 = new AtomicBoolean(false);

        final AtomicLong started2 = new AtomicLong();
        final AtomicLong started3 = new AtomicLong();

        final AtomicLong finished1 = new AtomicLong();
        final AtomicLong finished2 = new AtomicLong();


        Thread t1 = new Thread(() -> {
            testSync.getWithSyncNullable(42L, ()->{
                isStarted.set(true);
                try {
                    while (isRun.get()) {
                        Thread.sleep(TimeUnit.MILLISECONDS.toMillis(500));
                    }
                } catch (InterruptedException e) {
                    ExceptionUtils.rethrow(e);
                }
                finished1.set(System.currentTimeMillis());
                return null;
            });
            isStarted.set(false);
        }, "My thread #1");
        t1.start();

        long mills = System.currentTimeMillis();
        try {
            while (!isStarted.get()) {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                if (System.currentTimeMillis() - mills > 5_000) {
                    throw new IllegalStateException("Thread t1 wasn't started in 5 seconds");
                }
            }
        } catch (InterruptedException e) {
            ExceptionUtils.rethrow(e);
        }

        Thread t2 = new Thread(() -> {
            isStarted2.set(true);
            testSync.getWithSyncNullable(42L, ()->{
                started2.set(System.currentTimeMillis());
                try {
                    while (isRun2.get()) {
                        Thread.sleep(TimeUnit.MILLISECONDS.toMillis(500));
                    }
                } catch (InterruptedException e) {
                    ExceptionUtils.rethrow(e);
                }
                finished2.set(System.currentTimeMillis());
                return null;
            });
            isStarted2.set(false);
        }, "My thread #2");
        t2.start();

        Thread t3 = new Thread(() -> {
            isStarted3.set(true);
            testSync.getWithSyncNullable(42L, ()->{
                started3.set(System.currentTimeMillis());
                try {
                    while (isRun3.get()) {
                        Thread.sleep(TimeUnit.MILLISECONDS.toMillis(500));
                    }
                } catch (InterruptedException e) {
                    ExceptionUtils.rethrow(e);
                }
                return null;
            });
            isStarted3.set(false);
        }, "My thread #3");
        t3.start();

        mills = System.currentTimeMillis();
        try {
            while (!isStarted2.get()) {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                if (System.currentTimeMillis() - mills > 5_000) {
                    throw new IllegalStateException("Threads t2 wasn't started in 5 seconds");
                }
            }
        } catch (InterruptedException e) {
            ExceptionUtils.rethrow(e);
        }

        boolean lockOk = false;
        if (!testSync.getWriteLock(42L).isHeldByCurrentThread()) {
            lockOk = true;
        }

        isRun.set(false);
        mills = System.currentTimeMillis();
        try {
            while (isStarted.get()) {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                if (System.currentTimeMillis() - mills > 5_000) {
                    throw new IllegalStateException("Threads t2 wasn't finished in 5 seconds");
                }
            }
        } catch (InterruptedException e) {
            ExceptionUtils.rethrow(e);
        }

        assertTrue(started2.get()>=finished1.get(), S.f("started2: %d, finished1: %d", started2.get(), finished1.get()));

        isRun.set(false);
        isRun2.set(false);

        isRun3.set(false);
        Thread.sleep(TimeUnit.MILLISECONDS.toMillis(1000));

        assertTrue(lockOk);
        assertFalse(isStarted.get());
        final ReentrantReadWriteLock.WriteLock writeLock = testSync.getWriteLock(42L);
        final boolean condition = writeLock.tryLock();
        assertTrue(condition);
        writeLock.unlock();

    }
}
