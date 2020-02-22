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

import ai.metaheuristic.ai.station.DispatcherLookupExtendedService;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RoundRobinForDispatcher {

    private final Map<String, AtomicBoolean> urls;

    public RoundRobinForDispatcher(Map<String, DispatcherLookupExtendedService.DispatcherLookupExtended> dispatchers) {
        Map<String, AtomicBoolean> map = new HashMap<>();
        for (Map.Entry<String, DispatcherLookupExtendedService.DispatcherLookupExtended> entry : dispatchers.entrySet()) {
            DispatcherLookupConfig.DispatcherLookup dispatcherLookup = entry.getValue().dispatcherLookup;
            if (dispatcherLookup.disabled) {
                log.info("dispatcher {} is disabled", dispatcherLookup.url);
                continue;
            }
            log.info("dispatcher {} was added to round-robin", dispatcherLookup.url);
            map.putIfAbsent(dispatcherLookup.url, new AtomicBoolean(true));
        }
        urls = Collections.unmodifiableMap(map);
    }

    public Set<String> getActiveDispatchers() {
        return urls.keySet();
    }

    public String next() {
        String url = findNext();
        if (url != null) {
            return url;
        }
        reset();
        url = findNext();
        if (url != null) {
            return url;
        }
        return null;
    }

    public void reset() {
        for (Map.Entry<String, AtomicBoolean> entry : urls.entrySet()) {
            entry.getValue().set(true);
        }
    }

    private String findNext() {
        String url = null;
        for (Map.Entry<String, AtomicBoolean> entry : urls.entrySet()) {
            if (entry.getValue().get()) {
                entry.getValue().set(false);
                url = entry.getKey();
                break;
            }
        }
        return url;
    }
}
