/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.station;

import ai.metaheuristic.ai.dispatcher.RoundRobinForDispatcher;
import ai.metaheuristic.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import org.junit.Test;

import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;

public class TestRoundRobinForLaunchpad {

    @Test
    public void test() {

        LinkedHashMap<String, DispatcherLookupExtendedService.LaunchpadLookupExtended> lookupExtendedMap = new LinkedHashMap<>();

        DispatcherLookupExtendedService.LaunchpadLookupExtended lle1 = new DispatcherLookupExtendedService.LaunchpadLookupExtended();
        lle1.launchpadLookup = new LaunchpadLookupConfig.LaunchpadLookup();
        lle1.launchpadLookup.url = "url1";
        lookupExtendedMap.put( "url1", lle1);

        DispatcherLookupExtendedService.LaunchpadLookupExtended lle2 = new DispatcherLookupExtendedService.LaunchpadLookupExtended();
        lle2.launchpadLookup = new LaunchpadLookupConfig.LaunchpadLookup();
        lle2.launchpadLookup.url = "url2";
        lookupExtendedMap.put( "url2", lle2);

        RoundRobinForDispatcher rr = new RoundRobinForDispatcher(lookupExtendedMap);

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
