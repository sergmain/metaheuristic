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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.commons.CommonSync;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * it's a Spring based implementation because we need singleton
 *
 * @author Serge
 * Date: 8/11/2019
 * Time: 10:56 AM
 */
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
@Slf4j
public class ExecContextSyncService {

    private static final CommonSync<Long> commonSync = new CommonSync<>();

    public static void checkWriteLockPresent(Long execContextId) {
        if (!getWriteLock(execContextId).isHeldByCurrentThread()) {
            throw new IllegalStateException("#977.020 Must be locked by WriteLock");
        }
    }

    public static void checkWriteLockNotPresent(Long execContextId) {
        if (getWriteLock(execContextId).isHeldByCurrentThread()) {
            throw new IllegalStateException("#977.025 The thread was already locked by WriteLock");
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static ReentrantReadWriteLock.WriteLock getWriteLock(Long execContextId) {
        return commonSync.getWriteLock(execContextId);
    }

    private static ReentrantReadWriteLock.ReadLock getReadLock(Long execContextId) {
        return commonSync.getReadLock(execContextId);
    }

    public static <T> T getWithSync(Long execContextId, Supplier<T> supplier) {
        TxUtils.checkTxNotExists();
        checkWriteLockNotPresent(execContextId);

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock(execContextId);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    // ForCreation means that the presence of TX won't be checked
    @Nullable
    public static <T> T getWithSyncNullableForCreation(Long execContextId, Supplier<T> supplier) {
        checkWriteLockNotPresent(execContextId);

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
        TxUtils.checkTxNotExists();
        checkWriteLockNotPresent(execContextId);

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock(execContextId);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    public static void getWithSyncVoid(Long execContextId, Runnable runnable) {
        TxUtils.checkTxNotExists();
        checkWriteLockNotPresent(execContextId);

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock(execContextId);
        try {
            lock.lock();
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    public static <T> T getWithSyncReadOnly(ExecContextImpl execContext, Supplier<T> supplier) {
        TxUtils.checkTxNotExists();
        checkWriteLockNotPresent(execContext.id);

        final ReentrantReadWriteLock.ReadLock lock = getReadLock(execContext.id);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }
}
