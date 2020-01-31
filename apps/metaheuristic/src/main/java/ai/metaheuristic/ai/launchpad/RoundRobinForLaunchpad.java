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

package ai.metaheuristic.ai.launchpad;

import ai.metaheuristic.ai.station.LaunchpadLookupExtendedService;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RoundRobinForLaunchpad {

    private final Map<String, AtomicBoolean> urls;

    public RoundRobinForLaunchpad(Map<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> launchpads) {
        Map<String, AtomicBoolean> map = new HashMap<>();
        for (Map.Entry<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> entry : launchpads.entrySet()) {
            LaunchpadLookupConfig.LaunchpadLookup launchpadLookup = entry.getValue().launchpadLookup;
            if (launchpadLookup.disabled) {
                log.info("launchpad {} is disabled", launchpadLookup.url);
                continue;
            }
            log.info("launchpad {} was added to round-robin", launchpadLookup.url);
            map.putIfAbsent(launchpadLookup.url, new AtomicBoolean(true));
        }
        urls = Collections.unmodifiableMap(map);
    }

    public Set<String> getActiveLaunchpads() {
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
