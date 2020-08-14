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

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
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

    private final @NonNull ExecContextRepository execContextRepository;

    private static final ConcurrentHashMap<Long, ReentrantReadWriteLock.WriteLock> syncMap = new ConcurrentHashMap<>(100);

    @NonNull
    <T> T getWithSync(@NonNull Long execContextId, @NonNull Function<ExecContextImpl, @lombok.NonNull T> function) {
        final ReentrantReadWriteLock.WriteLock lock = syncMap.computeIfAbsent(execContextId, o -> new ReentrantReadWriteLock().writeLock());
        try {
            lock.lock();
//            log.info("#010.010 ENTER SYNC, execContextId: {}, counter: {}", execContextId, lock);
            ExecContextImpl execContext = execContextRepository.findByIdForUpdate(execContextId);
            return function.apply(execContext);
        } finally {
//            log.info("#010.020 LEAVE SYNC, execContextId: {}, counter: {}", execContextId, lock);
            lock.unlock();
        }
    }

    @Nullable
    <T> T getWithSyncNullable(@NonNull Long execContextId, Function<ExecContextImpl, T> function) {
        final ReentrantReadWriteLock.WriteLock lock = syncMap.computeIfAbsent(execContextId, o -> new ReentrantReadWriteLock().writeLock());
        try {
            lock.lock();
//            log.info("#010.030 ENTER SYNC, execContextId: {}, counter: {}", execContextId, lock);
            ExecContextImpl execContext = execContextRepository.findByIdForUpdate(execContextId);
            return execContext == null ? null : function.apply(execContext);
        } finally {
//            log.info("#010.040 LEAVE SYNC, execContextId: {}, counter: {}", execContextId, lock);
            lock.unlock();
        }
    }

    @NonNull
    <T> T getWithSyncReadOnly(@NonNull ExecContextImpl execContext, @NonNull Supplier<T> function) {
        final ReentrantReadWriteLock.WriteLock lock = syncMap.computeIfAbsent(execContext.id, o -> new ReentrantReadWriteLock().writeLock());
        try {
            lock.lock();
//            log.info("#010.050 ENTER SYNC, execContextId: {}, counter: {}", execContext.id, lock);
            return function.get();
        } finally {
//            log.info("#010.060 LEAVE SYNC, execContextId: {}, counter: {}", execContext.id, lock);
            lock.unlock();
        }
    }
}
