/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.batch;

import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.Batch;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * @author Serge
 * Date: 10/18/2019
 * Time: 03:28 PM
 */
@Service
@RequiredArgsConstructor
@Profile("launchpad")
public class BatchSyncService {

    private final BatchRepository batchRepository;

    private static final ConcurrentHashMap<Long, AtomicInteger> syncMap = new ConcurrentHashMap<>(100, 0.75f, 10);
    private static final ReentrantReadWriteLock.WriteLock writeLock = new ReentrantReadWriteLock().writeLock();

    @SuppressWarnings("Duplicates")
    <T> T getWithSync(Long batchId, LaunchpadContext execContext, Function<Batch, T> function) {
        final AtomicInteger obj;
        try {
            writeLock.lock();
            obj = syncMap.computeIfAbsent(batchId, o -> new AtomicInteger());
        } finally {
            writeLock.unlock();
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            obj.incrementAndGet();
            try {
                Batch batch = batchRepository.findByIdForUpdate(batchId, execContext.account.companyId);
                return batch == null ? null : function.apply(batch);
            } finally {
                try {
                    writeLock.lock();
                    if (obj.get() == 1) {
                        syncMap.remove(batchId);
                    }
                    obj.decrementAndGet();
                } finally {
                    writeLock.unlock();
                }
            }
        }
    }

    @SuppressWarnings("Duplicates")
    <T> T getWithSyncReadOnly(Batch batch, LaunchpadContext execContext, Function<LaunchpadContext, T> function) {
        if (batch==null) {
            return null;
        }
        final AtomicInteger obj;
        try {
            writeLock.lock();
            obj = syncMap.computeIfAbsent(batch.id, o -> new AtomicInteger());
        } finally {
            writeLock.unlock();
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            obj.incrementAndGet();
            try {
                return function.apply(execContext);
            } finally {
                try {
                    writeLock.lock();
                    if (obj.get() == 1) {
                        syncMap.remove(batch.id);
                    }
                    obj.decrementAndGet();
                } finally {
                    writeLock.unlock();
                }
            }
        }
    }
}
