/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.ai.processor.DispatcherLookupExtendedService;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.*;

@Slf4j
public class RoundRobinForDispatcher {

    private final Map<DispatcherServerUrl, AtomicBoolean> urls;

    public RoundRobinForDispatcher(Map<DispatcherServerUrl, DispatcherLookupExtendedService.DispatcherLookupExtended> dispatchers) {
        Map<DispatcherServerUrl, AtomicBoolean> map = new LinkedHashMap<>();
        for (Map.Entry<DispatcherServerUrl, DispatcherLookupExtendedService.DispatcherLookupExtended> entry : dispatchers.entrySet()) {
            DispatcherLookupConfig.DispatcherLookup dispatcherLookup = entry.getValue().dispatcherLookup;
            if (dispatcherLookup.disabled) {
                log.info("dispatcher {} is disabled", dispatcherLookup.getDispatcherUrl());
                continue;
            }
            log.info("dispatcher {} was added to round-robin", dispatcherLookup.getDispatcherUrl());
            map.putIfAbsent(dispatcherLookup.getDispatcherUrl(), new AtomicBoolean(true));
        }
        urls = Collections.unmodifiableMap(map);
    }

    public Set<DispatcherServerUrl> getActiveDispatchers() {
        return urls.keySet();
    }

    @Nullable
    public DispatcherServerUrl next() {
        DispatcherServerUrl url = findNext();
        if (url != null) {
            return url;
        }
        reset();
        url = findNext();
        return url;
    }

    public void reset() {
        for (Map.Entry<DispatcherServerUrl, AtomicBoolean> entry : urls.entrySet()) {
            entry.getValue().set(true);
        }
    }

    @Nullable
    private DispatcherServerUrl findNext() {
        DispatcherServerUrl url = null;
        for (Map.Entry<DispatcherServerUrl, AtomicBoolean> entry : urls.entrySet()) {
            if (entry.getValue().get()) {
                entry.getValue().set(false);
                url = entry.getKey();
                break;
            }
        }
        return url;
    }
}
