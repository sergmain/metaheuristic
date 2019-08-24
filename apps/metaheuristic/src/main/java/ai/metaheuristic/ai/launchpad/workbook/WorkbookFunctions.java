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

package ai.metaheuristic.ai.launchpad.workbook;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 8/11/2019
 * Time: 10:56 AM
 */
public class WorkbookFunctions {

    private static final ConcurrentHashMap<Long, AtomicInteger> syncMap = new ConcurrentHashMap<>(100, 0.75f, 10);
    private static final ReentrantReadWriteLock.WriteLock writeLock = new ReentrantReadWriteLock().writeLock();

    @SuppressWarnings("Duplicates")
    static <T> T getWithSync(Long workbookId, Supplier<T> function) {
        final AtomicInteger obj;
        try {
            writeLock.lock();
            obj = syncMap.computeIfAbsent(workbookId, o -> new AtomicInteger());
        } finally {
            writeLock.unlock();
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (obj) {
            obj.incrementAndGet();
            try {
                return function.get();
            } finally {
                try {
                    writeLock.lock();
                    if (obj.get() == 1) {
                        syncMap.remove(workbookId);
                    }
                    obj.decrementAndGet();
                } finally {
                    writeLock.unlock();
                }
            }
        }
    }
}
