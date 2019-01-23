/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai;

import aiai.ai.station.LaunchpadLookupExtendedService;
import aiai.ai.utils.holders.BoolHolder;
import aiai.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class RoundRobinForLaunchpad {

    public Map<String, BoolHolder> urls = new HashMap<>();

    public RoundRobinForLaunchpad(Map<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> launchpads) {
        for (Map.Entry<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> entry : launchpads.entrySet()) {
            LaunchpadLookupConfig.LaunchpadLookup launchpadLookup = entry.getValue().launchpadLookup;
            if (launchpadLookup.disabled) {
                log.info("launchpad {} is disabled", launchpadLookup.url);
                continue;
            }
            log.info("launchpad {} was added to round-robin", launchpadLookup.url);
            this.urls.putIfAbsent(launchpadLookup.url, new BoolHolder(true));
        }
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
        throw new IllegalStateException("Can't produce next url");
    }

    public void reset() {
        for (Map.Entry<String, BoolHolder> entry : urls.entrySet()) {
            entry.getValue().value = true;
        }
    }

    private String findNext() {
        String url = null;
        for (Map.Entry<String, BoolHolder> entry : urls.entrySet()) {
            if (entry.getValue().value) {
                entry.getValue().value = false;
                url = entry.getKey();
                break;
            }
        }
        return url;
    }
}
