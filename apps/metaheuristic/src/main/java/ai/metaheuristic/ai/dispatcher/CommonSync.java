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

package ai.metaheuristic.ai.dispatcher;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Serge
 * Date: 8/14/2020
 * Time: 3:24 PM
 */
public class CommonSync<T> {

    private static final long TEN_MINUTES_TO_MILLS = TimeUnit.MINUTES.toMillis(10);
    private static final long ONE_HOUR_TO_MILLS = TimeUnit.HOURS.toMillis(1);

    @Data
    public static class TimedLock {
        public long mills;
        public ReentrantReadWriteLock.WriteLock lock;

        public TimedLock(ReentrantReadWriteLock.WriteLock lock) {
            this.mills = System.currentTimeMillis();
            this.lock = lock;
        }
    }

    private long lastCheckMills = 0L;
    private final Map<T, TimedLock> map = new HashMap<>(100);

    public synchronized ReentrantReadWriteLock.WriteLock getLock(T id) {
        if (System.currentTimeMillis() - lastCheckMills > TEN_MINUTES_TO_MILLS) {
            lastCheckMills = System.currentTimeMillis();
            List<T> ids = new ArrayList<>();
            for (Map.Entry<T, TimedLock> entry : map.entrySet()) {
                if (id.equals(entry.getKey())) {
                    entry.getValue().mills = System.currentTimeMillis();
                    continue;
                }
                if (System.currentTimeMillis() - entry.getValue().mills > ONE_HOUR_TO_MILLS) {
                    ids.add(entry.getKey());
                }
            }
            for (T idForRemoving : ids) {
                map.remove(idForRemoving);
            }
        }

        final TimedLock timedLock = map.computeIfAbsent(id, o -> new TimedLock(new ReentrantReadWriteLock().writeLock()));
        timedLock.mills = System.currentTimeMillis();
        return timedLock.lock;
    }


}
