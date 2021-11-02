/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.commons.CommonSync;
import org.springframework.lang.Nullable;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 11/1/2021
 * Time: 8:01 PM
 */
public class TaskQueueSyncStaticService {

    private static final CommonSync<Integer> commonSync = new CommonSync<>();
    private static final Integer ID = 1;

    public static void checkWriteLockPresent() {
        if (!getWriteLock().isHeldByCurrentThread()) {
            throw new IllegalStateException("#975.020 Must be locked by WriteLock");
        }
    }

    public static void checkWriteLockNotPresent() {
        if (getWriteLock().isHeldByCurrentThread()) {
            throw new IllegalStateException("#975.025 The thread was already locked by WriteLock");
        }
    }

    private static ReentrantReadWriteLock.WriteLock getWriteLock() {
        return commonSync.getWriteLock(ID);
    }

    private static ReentrantReadWriteLock.ReadLock getReadLock() {
        return commonSync.getReadLock(ID);
    }

    public static void getWithSyncVoid(Runnable runnable) {
        checkWriteLockNotPresent();

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock();
        try {
            lock.lock();
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    public static <T> T getWithSync(Supplier<T> supplier) {
        checkWriteLockNotPresent();

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock();
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public static <T> T getWithSyncNullable(Supplier<T> supplier) {
        checkWriteLockNotPresent();

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock();
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }
}
