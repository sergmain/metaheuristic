/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.commons.dispatcher_schedule.DispatcherSchedule;
import ai.metaheuristic.ai.processor.dispatcher_selection.RoundRobinDispatcherSelection;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRoundRobinForDispatcher {

    @Test
    public void test() {

        LinkedHashMap<DispatcherUrl, DispatcherLookupExtendedService.DispatcherLookupExtended> lookupExtendedMap = new LinkedHashMap<>();

        DispatcherUrl url1 = new DispatcherUrl("url1");
        DispatcherLookupExtendedService.DispatcherLookupExtended lle1 = new DispatcherLookupExtendedService.DispatcherLookupExtended(
                url1, new DispatcherLookupParamsYaml.DispatcherLookup(), DispatcherSchedule.createDispatcherSchedule(null));
        lle1.dispatcherLookup.url = url1.url;
        lookupExtendedMap.put( url1, lle1);

        DispatcherUrl url2 = new DispatcherUrl("url2");
        DispatcherLookupExtendedService.DispatcherLookupExtended lle2 = new DispatcherLookupExtendedService.DispatcherLookupExtended(
                url2, new DispatcherLookupParamsYaml.DispatcherLookup(), DispatcherSchedule.createDispatcherSchedule(null));
        lle2.dispatcherLookup.url = url2.url;
        lookupExtendedMap.put( url2, lle2);

        RoundRobinDispatcherSelection rr = new RoundRobinDispatcherSelection(lookupExtendedMap, "RoundRobin for testing");

        DispatcherUrl url = rr.next();
        assertEquals(new DispatcherUrl("url1"), url);

        rr.reset();

        url = rr.next();
        assertEquals(new DispatcherUrl("url1"), url);

        url = rr.next();
        assertEquals(new DispatcherUrl("url2"), url);

        url = rr.next();
        assertEquals(new DispatcherUrl("url1"), url);


    }
}
