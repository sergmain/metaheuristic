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

package ai.metaheuristic.ai.launchpad.task;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 7/27/2019
 * Time: 8:26 PM
 */
@SuppressWarnings("Duplicates")
@Slf4j
public class TaskFunctions {
    private static final ConcurrentHashMap<Long, AtomicInteger> syncMap = new ConcurrentHashMap<>(100, 0.75f, 10);

    static TaskImpl changeTaskReturnTask(Long taskId, Supplier<TaskImpl> function) {
        final AtomicInteger obj = syncMap.computeIfAbsent(taskId, o -> new AtomicInteger());
        obj.incrementAndGet();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return function.get();
            }
            finally {
                if (obj.get()==1) {
                    syncMap.remove(taskId);
                }
                obj.decrementAndGet();
            }
        }
    }

    static Enums.UploadResourceStatus changeTaskReturnUploadResourceStatus(Long taskId, Supplier<Enums.UploadResourceStatus> function) {
        final AtomicInteger obj = syncMap.computeIfAbsent(taskId, o -> new AtomicInteger());
        obj.incrementAndGet();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            try {
                return function.get();
            }
            finally {
                if (obj.get()==1) {
                    syncMap.remove(taskId);
                }
                obj.decrementAndGet();
            }
        }
    }
}
