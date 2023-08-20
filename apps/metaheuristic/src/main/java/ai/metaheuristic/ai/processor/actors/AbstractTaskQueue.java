/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
package ai.metaheuristic.ai.processor.actors;

import org.springframework.lang.Nullable;

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class AbstractTaskQueue<T> {

    private final LinkedList<T> QUEUE = new LinkedList<>();

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public int queueSize(){
        readLock.lock();
        try {
            return QUEUE.size();
        } finally {
            readLock.unlock();
        }
    }

    public void add(T t){
        readLock.lock();
        try {
            if (QUEUE.contains(t)) {
                return;
            }
        } finally {
            readLock.unlock();
        }
        writeLock.lock();
        try {
            if (!QUEUE.contains(t)) {
                QUEUE.add(t);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Nullable
    public T poll() {
        writeLock.lock();
        try {
            return QUEUE.pollFirst();
        } finally {
            writeLock.unlock();
        }
    }
}
