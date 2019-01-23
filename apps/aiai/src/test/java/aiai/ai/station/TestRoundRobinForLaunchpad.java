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

package aiai.ai.station;

import aiai.ai.RoundRobinForLaunchpad;
import aiai.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class TestRoundRobinForLaunchpad {

    @Test
    public void test() {

        LinkedHashMap<String, LaunchpadLookupExtendedService.LaunchpadLookupExtended> lookupExtendedMap = new LinkedHashMap<>();

        LaunchpadLookupExtendedService.LaunchpadLookupExtended lle1 = new LaunchpadLookupExtendedService.LaunchpadLookupExtended();
        lle1.launchpadLookup = new LaunchpadLookupConfig.LaunchpadLookup();
        lle1.launchpadLookup.url = "url1";
        lookupExtendedMap.put( "url1", lle1);

        LaunchpadLookupExtendedService.LaunchpadLookupExtended lle2 = new LaunchpadLookupExtendedService.LaunchpadLookupExtended();
        lle2.launchpadLookup = new LaunchpadLookupConfig.LaunchpadLookup();
        lle2.launchpadLookup.url = "url2";
        lookupExtendedMap.put( "url2", lle2);

        RoundRobinForLaunchpad rr = new RoundRobinForLaunchpad(lookupExtendedMap);

        String url = rr.next();
        assertEquals("url1", url);

        rr.reset();

        url = rr.next();
        assertEquals("url1", url);

        url = rr.next();
        assertEquals("url2", url);

        url = rr.next();
        assertEquals("url1", url);


    }
}
