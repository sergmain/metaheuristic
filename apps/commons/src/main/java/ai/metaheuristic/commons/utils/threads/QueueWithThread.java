/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.commons.utils.threads;

import lombok.SneakyThrows;
import org.springframework.lang.Nullable;

import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Sergio Lissner
 * Date: 9/21/2023
 * Time: 1:44 PM
 */
public class QueueWithThread<T> {

    static class SomeClass {
        private Semaphore semaphore = new Semaphore(100);

        @SneakyThrows
        public void execute() {
            semaphore.acquire();
            try {
                //
            } finally {
                semaphore.release();
            }
        }
    }

    private final LinkedList<T> events = new LinkedList<>();

    @Nullable
    public Thread thread;
    public final boolean checkForDouble;

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public QueueWithThread(boolean checkForDouble) {
        this.checkForDouble = checkForDouble;
    }

    public int size() {
        readLock.lock();
        try {
            return events.size();
        } finally {
            readLock.unlock();
        }
    }

    public boolean isEmpty() {
        readLock.lock();
        try {
            return events.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    public boolean contains(T event) {
        readLock.lock();
        try {
            return events.contains(event);
        } finally {
            readLock.unlock();
        }
    }

    public void add(T event) {
        writeLock.lock();
        try {
            if (checkForDouble) {
                if (events.contains(event)) {
                    return;
                }
            }
            events.add(event);
        } finally {
            writeLock.unlock();
        }
    }

    @Nullable
    public T pollFirst() {
        writeLock.lock();
        try {
            return events.pollFirst();
        } finally {
            writeLock.unlock();
        }
    }

    public T get(int i) {
        readLock.lock();
        try {
            return events.get(i);
        } finally {
            readLock.unlock();
        }
    }

    public void clear() {
        writeLock.lock();
        try {
            events.clear();
        } finally {
            writeLock.unlock();
        }
    }

    public boolean canBeRemoved() {
        return events.isEmpty() && thread == null;
    }
}
