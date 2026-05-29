/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.shutdown;

import ai.metaheuristic.ai.dispatcher.task.TaskQueueService;

import javax.cache.CacheManager;
import javax.cache.Caching;

/**
 * @author Sergio Lissner
 * Date: 3/15/2026
 * Time: 10:24 AM
 */
public class MhShutdown {

    public static void cleanUp() {
        TaskQueueService.resetQueue();

        // Get the JCache manager (configured via ehcache.xml or properties)
        CacheManager jcacheManager = Caching.getCachingProvider().getCacheManager();
        jcacheManager.getCacheNames().forEach(name -> {
            try {
                javax.cache.Cache<?, ?> cache = jcacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            } catch (Throwable ex) {
                //
            }
        });

    }
}
