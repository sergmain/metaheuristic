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

//    private static final ConcurrentHashMap<Long, ReentrantReadWriteLock.WriteLock> syncMap = new ConcurrentHashMap<>(100);

    private static final CommonSync commonSync = new CommonSync();

    <T> T getWithSync(Long execContextId, Function<ExecContextImpl, T> function) {
//        final ReentrantReadWriteLock.WriteLock lock = syncMap.computeIfAbsent(execContextId, o -> new ReentrantReadWriteLock().writeLock());
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getLock(execContextId);
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
    <T> T getWithSyncNullable(Long execContextId, Function<ExecContextImpl, T> function) {
//        final ReentrantReadWriteLock.WriteLock lock = syncMap.computeIfAbsent(execContextId, o -> new ReentrantReadWriteLock().writeLock());
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getLock(execContextId);
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

    <T> T getWithSyncReadOnly(Long execContextId, Supplier<T> function) {
//        final ReentrantReadWriteLock.WriteLock lock = syncMap.computeIfAbsent(execContext.id, o -> new ReentrantReadWriteLock().writeLock());
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getLock(execContextId);
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
