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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.dispatcher.commons.CommonSync;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * The lock of a Task at the Processor. The key is the absolute path to the Task's task.yaml, see {@link #key(Path)},
 * so one Task has one lock whichever core and Dispatcher it belongs to, and Tasks don't wait for each other.
 * A Task is changed and written to its task.yaml under its write lock; a state of a Task made of several fields is read
 * under its read lock. A nested lock of the same Task by the same thread isn't allowed - see {@link #checkWriteLockNotPresent}.
 * Reference: ai.metaheuristic.ai.dispatcher.task.TaskSyncService.
 *
 * <p>Error code prefix: {@code 01.718.} (unique to this class).
 */
@Slf4j
public class ProcessorTaskSyncService {

    private static final CommonSync<String> commonSync = new CommonSync<>();

    /** @return the key of the lock of a Task - the absolute, normalized path to its task.yaml */
    public static String key(Path taskYaml) {
        return taskYaml.toAbsolutePath().normalize().toString();
    }

    public static void checkWriteLockPresent(String key) {
        if (!getWriteLock(key).isHeldByCurrentThread()) {
            throw new IllegalStateException("01.718.020 Must be locked by WriteLock, key: " + key);
        }
    }

    public static void checkWriteLockNotPresent(String key) {
        if (getWriteLock(key).isHeldByCurrentThread()) {
            throw new IllegalStateException("01.718.040 The thread was already locked by WriteLock, key: " + key);
        }
    }

    public static ReentrantReadWriteLock.WriteLock getWriteLock(String key) {
        return commonSync.getWriteLock(key);
    }

    public static ReentrantReadWriteLock.ReadLock getReadLock(String key) {
        return commonSync.getReadLock(key);
    }

    /** @return the write lock of a Task for locking it - after checking that the current thread doesn't hold it already */
    public static ReentrantReadWriteLock.WriteLock getWriteLockNotHeld(String key) {
        checkWriteLockNotPresent(key);
        return getWriteLock(key);
    }

    public static <T> T getWithSync(String key, Supplier<T> supplier) {
        final ReentrantReadWriteLock.WriteLock lock = getWriteLockNotHeld(key);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    public static <T> @Nullable T getWithSyncNullable(String key, Supplier<@Nullable T> supplier) {
        final ReentrantReadWriteLock.WriteLock lock = getWriteLockNotHeld(key);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    public static void getWithSyncVoid(String key, Runnable runnable) {
        final ReentrantReadWriteLock.WriteLock lock = getWriteLockNotHeld(key);
        try {
            lock.lock();
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    /** Reads a state of a Task under its read lock. Reading while holding its write lock is allowed. */
    public static <T> T getWithReadSync(String key, Supplier<T> supplier) {
        final ReentrantReadWriteLock.ReadLock lock = getReadLock(key);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    public static void getWithReadSyncVoid(String key, Runnable runnable) {
        final ReentrantReadWriteLock.ReadLock lock = getReadLock(key);
        try {
            lock.lock();
            runnable.run();
        } finally {
            lock.unlock();
        }
    }
}
