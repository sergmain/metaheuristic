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

package ai.metaheuristic.ai.dispatcher.exec_context_task_state;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextVariableState;
import ai.metaheuristic.ai.dispatcher.commons.CommonSync;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 3/23/2021
 * Time: 6:08 AM
 */
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
@Slf4j
public class ExecContextTaskStateSyncService {

    private static final CommonSync<Long> commonSync = new CommonSync<>();

    public static void checkWriteLockPresent(Long execContextTaskStateId) {
        if (!getWriteLock(execContextTaskStateId).isHeldByCurrentThread()) {
            throw new IllegalStateException("#972.020 Must be locked by WriteLock");
        }
    }

    public static void checkWriteLockNotPresent(Long execContextTaskStateId) {
        if (getWriteLock(execContextTaskStateId).isHeldByCurrentThread()) {
            throw new IllegalStateException("#972.025 The thread was already locked by WriteLock");
        }
    }

    public static ReentrantReadWriteLock.WriteLock getWriteLock(Long execContextTaskStateId) {
        return commonSync.getWriteLock(execContextTaskStateId);
    }

    private static ReentrantReadWriteLock.ReadLock getReadLock(Long execContextTaskStateId) {
        return commonSync.getReadLock(execContextTaskStateId);
    }

    public static <T> T getWithSync(Long execContextTaskStateId, Supplier<T> supplier) {
        TxUtils.checkTxNotExists();
        checkWriteLockNotPresent(execContextTaskStateId);

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock(execContextTaskStateId);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    // ForCreation means that the presence of TX won't be checked
    @Nullable
    public static <T> T getWithSyncNullableForCreation(Long execContextTaskStateId, Supplier<T> supplier) {
        checkWriteLockNotPresent(execContextTaskStateId);

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock(execContextTaskStateId);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public static <T> T getWithSyncNullable(Long execContextTaskStateId, Supplier<T> supplier) {
        TxUtils.checkTxNotExists();
        checkWriteLockNotPresent(execContextTaskStateId);

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock(execContextTaskStateId);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    public static void getWithSyncVoid(Long execContextTaskStateId, Runnable runnable) {
        TxUtils.checkTxNotExists();
        checkWriteLockNotPresent(execContextTaskStateId);

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock(execContextTaskStateId);
        try {
            lock.lock();
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    public static <T> T getWithSyncReadOnly(ExecContextVariableState execContextVariableState, Supplier<T> supplier) {
        TxUtils.checkTxNotExists();
        checkWriteLockNotPresent(execContextVariableState.id);

        final ReentrantReadWriteLock.ReadLock lock = getReadLock(execContextVariableState.id);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

}
