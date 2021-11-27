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

package ai.metaheuristic.ai.dispatcher.processor;

import ai.metaheuristic.ai.dispatcher.commons.CommonSync;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 9/27/2020
 * Time: 8:26 PM
 */
@Slf4j
public class ProcessorSyncService {

    private static final CommonSync<Long> commonSync = new CommonSync<>();

    public static void checkWriteLockPresent(Long processorId) {
        if (!getWriteLock(processorId).isHeldByCurrentThread()) {
            throw new IllegalStateException("#302.005 Must be locked by WriteLock");
        }
    }

    public static ReentrantReadWriteLock.WriteLock getWriteLock(Long processorId) {
        return commonSync.getWriteLock(processorId);
    }

    private static ReentrantReadWriteLock.ReadLock getReadLock(Long processorId) {
        return commonSync.getReadLock(processorId);
    }

    public static <T> T getWithSync(Long processorId, Supplier<T> supplier) {
        TxUtils.checkTxNotExists();
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getWriteLock(processorId);
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public static <T> T getWithSyncNullable(Long processorId, Supplier<T> supplier) {
        return getWithSyncNullable(false, processorId, supplier);
    }

    public @Nullable
    static <T> T getWithSyncNullable(boolean debug, Long processorId, Supplier<T> supplier) {
        TxUtils.checkTxNotExists();
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getWriteLock(processorId);
        if (debug) {
            log.debug("WriteLock: " + lock);
        }
        try {
            lock.lock();
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    public static void getWithSyncVoid(Long processorId, Runnable runnable) {
        TxUtils.checkTxNotExists();
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getWriteLock(processorId);
        try {
            lock.lock();
            runnable.run();
        } finally {
            lock.unlock();
        }
    }
}
