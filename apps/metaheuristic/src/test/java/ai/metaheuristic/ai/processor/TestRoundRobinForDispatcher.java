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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.dispatcher.RoundRobinForDispatcher;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRoundRobinForDispatcher {

    @Test
    public void test() {

        LinkedHashMap<String, DispatcherLookupExtendedService.DispatcherLookupExtended> lookupExtendedMap = new LinkedHashMap<>();

        DispatcherLookupExtendedService.DispatcherLookupExtended lle1 = new DispatcherLookupExtendedService.DispatcherLookupExtended();
        lle1.dispatcherLookup = new DispatcherLookupConfig.DispatcherLookup();
        lle1.dispatcherLookup.url = "url1";
        lookupExtendedMap.put( "url1", lle1);

        DispatcherLookupExtendedService.DispatcherLookupExtended lle2 = new DispatcherLookupExtendedService.DispatcherLookupExtended();
        lle2.dispatcherLookup = new DispatcherLookupConfig.DispatcherLookup();
        lle2.dispatcherLookup.url = "url2";
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
