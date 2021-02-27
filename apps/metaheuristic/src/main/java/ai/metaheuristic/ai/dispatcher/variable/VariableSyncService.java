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

package ai.metaheuristic.ai.dispatcher.variable;

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
 * Date: 2/26/2021
 * Time: 11:35 PM
 */
@SuppressWarnings({"WeakerAccess", "unused"})
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class VariableSyncService {

    private static final CommonSync<Long> commonSync = new CommonSync<>();

    public void checkWriteLockPresent(Long variableId) {
        if (!getWriteLock(variableId).isHeldByCurrentThread()) {
            throw new IllegalStateException("#973.020 Must be locked by WriteLock");
        }
    }

    public void checkWriteLockNotPresent(Long variableId) {
        if (getWriteLock(variableId).isHeldByCurrentThread()) {
            throw new IllegalStateException("#973.025 The thread was already locked by WriteLock");
        }
    }

    @SuppressWarnings("WeakerAccess")
    public ReentrantReadWriteLock.WriteLock getWriteLock(Long taskId) {
        return commonSync.getWriteLock(taskId);
    }

    private ReentrantReadWriteLock.ReadLock getReadLock(Long taskId) {
        return commonSync.getReadLock(taskId);
    }

    public <T> T getWithSync(Long variableId, Supplier<T> supplier) {
        TxUtils.checkTxNotExists();
        checkWriteLockNotPresent(variableId);

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock(variableId);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    // ForCreation means that the presence of TX won't be checked
    public <T> T getWithSyncForCreation(Long variableId, Supplier<T> supplier) {
        checkWriteLockNotPresent(variableId);

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock(variableId);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    // ForCreation means that the presence of TX won't be checked
    @Nullable
    public <T> T getWithSyncNullableForCreation(Long variableId, Supplier<T> supplier) {
        checkWriteLockNotPresent(variableId);

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock(variableId);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public <T> T getWithSyncNullable(Long variableId, Supplier<T> supplier) {
        checkWriteLockNotPresent(variableId);

        final ReentrantReadWriteLock.WriteLock lock = getWriteLock(variableId);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }
}
