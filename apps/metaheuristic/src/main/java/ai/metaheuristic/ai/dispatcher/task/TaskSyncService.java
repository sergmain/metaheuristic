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

import ai.metaheuristic.ai.dispatcher.CommonSync;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * @author Serge
 * Date: 7/27/2019
 * Time: 8:26 PM
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class TaskSyncService {

    private final TaskRepository taskRepository;
    private static final CommonSync<Long> commonSync = new CommonSync<>();

    public @Nullable <T> T getWithSync(Long taskId, Function<TaskImpl, T> function) {
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getLock(taskId);
        try {
            lock.lock();
            TaskImpl task = taskRepository.findByIdForUpdate(taskId);
            if (task==null) {
                log.warn("#306.010 Can't find Task for Id: {}", taskId);
                return null;
            }
            return function.apply(task);
        } finally {
            lock.unlock();
        }
    }
}
