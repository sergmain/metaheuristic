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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * @author Serge
 * Date: 7/27/2019
 * Time: 8:26 PM
 */
@SuppressWarnings({"Duplicates", "SynchronizationOnLocalVariableOrMethodParameter"})
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class TaskSyncService {

    private final TaskRepository taskRepository;

    private static final Map<Long, AtomicInteger> syncMap = new HashMap<>(100);
    private static final ReentrantReadWriteLock.WriteLock writeLock = new ReentrantReadWriteLock().writeLock();

    public @Nullable <T> T getWithSync(Long taskId, Function<TaskImpl, T> function) {
        final AtomicInteger obj;
        try {
            writeLock.lock();
            obj = syncMap.computeIfAbsent(taskId, o -> new AtomicInteger());
        } finally {
            writeLock.unlock();
        }
        synchronized (obj) {
            obj.incrementAndGet();
            try {
                TaskImpl task = taskRepository.findByIdForUpdate(taskId);
                if (task==null) {
                    log.warn("#306.010 Can't find Task for Id: {}", taskId);
                    return null;
                }
                return function.apply(task);
            } finally {
                try {
                    writeLock.lock();
                    if (obj.get() == 1) {
                        syncMap.remove(taskId);
                    }
                    obj.decrementAndGet();
                } finally {
                    writeLock.unlock();
                }
            }
        }
    }
}
