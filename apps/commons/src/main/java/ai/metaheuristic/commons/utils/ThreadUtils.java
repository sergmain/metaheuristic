/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.commons.utils;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * @author Sergio Lissner
 * Date: 11/3/2022
 * Time: 12:08 PM
 */
public class ThreadUtils {

    public static class CommonThreadLocker<T> {
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

        private final Supplier<T> supplier;

        public CommonThreadLocker(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        private T holder = null;

        public T get() {
            try {
                readLock.lock();
                if (holder != null) {
                    return holder;
                }
            } finally {
                readLock.unlock();
            }

            try {
                writeLock.lock();
                if (holder == null) {
                    holder = supplier.get();
                }
            } finally {
                writeLock.unlock();
            }
            return holder;
        }
    }

}
