/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.commons;

import lombok.Data;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Serge
 * Date: 8/14/2020
 * Time: 3:24 PM
 */
public class CommonSync<T> {

    private static final int MAX_SYNC_MAP_SIZE = 10000;
    // private static final long TEN_MINUTES_TO_MILLS = TimeUnit.MINUTES.toMillis(10);
    private static final long ONE_HOUR_TO_MILLS = TimeUnit.HOURS.toMillis(1);

    @Data
    public static class TimedLock {
        public volatile long mills;
        public ReentrantReadWriteLock lock;

        public TimedLock() {
            this.mills = System.currentTimeMillis();
            this.lock = new ReentrantReadWriteLock();
        }

        public TimedLock(ReentrantReadWriteLock lock) {
            this.mills = System.currentTimeMillis();
            this.lock = lock;
        }
    }

    private final LinkedHashMap<T, TimedLock> map = new LinkedHashMap<>(100) {
        protected boolean removeEldestEntry(Map.Entry<T, TimedLock> eldest) {
            // TODO p0 2023-07-30 there is a possible situation
            //  when a number of active locks greater than MAX_SYNC_MAP_SIZE and the last lock is active,
            //  then deleting this lock (the last lock in map) and in the same time requesting a new lock for the same T
            //  will lead to situation when there are 2 WriteLock for one T
            return this.size()>MAX_SYNC_MAP_SIZE || System.currentTimeMillis() - eldest.getValue().mills > ONE_HOUR_TO_MILLS;
        }
    };

    public ReentrantReadWriteLock.WriteLock getWriteLock(T id) {
        return getLock(id).writeLock();
    }

    public ReentrantReadWriteLock.ReadLock getReadLock(T id) {
        return getLock(id).readLock();
    }

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public ReentrantReadWriteLock getLock(T id) {
        writeLock.lock();
        try {
            final TimedLock timedLock = map.computeIfAbsent(id, (o) ->new TimedLock());
            timedLock.mills = System.currentTimeMillis();
            return timedLock.lock;
        } finally {
            writeLock.unlock();
        }
    }


}
