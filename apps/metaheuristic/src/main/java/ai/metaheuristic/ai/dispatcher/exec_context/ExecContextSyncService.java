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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.CommonSync;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 8/11/2019
 * Time: 10:56 AM
 */
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
@Slf4j
public class ExecContextSyncService {

    private final ExecContextRepository execContextRepository;
    private static final CommonSync<Long> commonSync = new CommonSync<>();

    public <T> T getWithSync(Long execContextId, Function<ExecContextImpl, T> function) {
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getWriteLock(execContextId);
        try {
            lock.lock();
            ExecContextImpl execContext = execContextRepository.findByIdForUpdate(execContextId);
            return function.apply(execContext);
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public <T> T getWithSyncNullable(Long execContextId, Function<ExecContextImpl, T> function) {
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getWriteLock(execContextId);
        try {
            lock.lock();
            ExecContextImpl execContext = execContextRepository.findByIdForUpdate(execContextId);
            return execContext == null ? null : function.apply(execContext);
        } finally {
            lock.unlock();
        }
    }

    public <T> T getWithSyncReadOnly(Long execContextId, Supplier<T> function) {
        final ReentrantReadWriteLock.ReadLock lock = commonSync.getReadLock(execContextId);
        try {
            lock.lock();
            return function.get();
        } finally {
            lock.unlock();
        }
    }
}
