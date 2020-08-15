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

package ai.metaheuristic.ai.dispatcher.batch;

import ai.metaheuristic.ai.dispatcher.CommonSync;
import ai.metaheuristic.ai.dispatcher.beans.Batch;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 10/18/2019
 * Time: 03:28 PM
 */
@SuppressWarnings("unused")
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class BatchSyncService {

    private final BatchRepository batchRepository;
    private static final CommonSync<Long> commonSync = new CommonSync<>();

    public void getWithSyncVoid(Long batchId, Consumer<Batch> function) {
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getLock(batchId);
        try {
            lock.lock();
//                Batch batch = batchRepository.findByIdForUpdate(batchId, execContext.account.companyId);
            Batch batch = batchRepository.findByIdForUpdate(batchId);
            if (batch!=null) {
                function.accept(batch);
            }
        } finally {
            lock.unlock();
        }
    }

    public <T> T getWithSync(Long batchId, Function<Batch, T> function) {
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getLock(batchId);
        try {
            lock.lock();
            Batch batch = batchRepository.findByIdForUpdate(batchId);
            return function.apply(batch);
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public <T> T getWithSyncNullable(Long batchId, Function<Batch, T> function) {
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getLock(batchId);
        try {
            lock.lock();
            Batch batch = batchRepository.findByIdForUpdate(batchId);
            return batch == null ? null : function.apply(batch);
        } finally {
            lock.unlock();
        }
    }

    public <T> T getWithSyncReadOnly(Long batchId, Supplier<T> function) {
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getLock(batchId);
        try {
            lock.lock();
            return function.get();
        } finally {
            lock.unlock();
        }
    }
}
