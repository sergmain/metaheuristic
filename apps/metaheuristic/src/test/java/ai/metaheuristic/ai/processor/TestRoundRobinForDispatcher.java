/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.commons.dispatcher_schedule.DispatcherSchedule;
import ai.metaheuristic.ai.processor.dispatcher_selection.RoundRobinDispatcherSelection;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRoundRobinForDispatcher {

    @Test
    public void test() {

        LinkedHashMap<DispatcherUrl, DispatcherLookupExtendedParams.DispatcherLookupExtended> lookupExtendedMap = new LinkedHashMap<>();

        DispatcherUrl url1 = new DispatcherUrl("url1");
        final DispatcherLookupParamsYaml.DispatcherLookup lookup1 = new DispatcherLookupParamsYaml.DispatcherLookup();
        lookup1.restUsername = "username";
        lookup1.restPassword = "password";
        DispatcherLookupExtendedParams.DispatcherLookupExtended lle1 = new DispatcherLookupExtendedParams.DispatcherLookupExtended(
                url1, lookup1, DispatcherSchedule.createDispatcherSchedule(null));
        lle1.dispatcherLookup.url = url1.url;
        lookupExtendedMap.put( url1, lle1);

        DispatcherUrl url2 = new DispatcherUrl("url2");
        final DispatcherLookupParamsYaml.DispatcherLookup lookup2 = new DispatcherLookupParamsYaml.DispatcherLookup();
        lookup2.restUsername = "username";
        lookup2.restPassword = "password";
        DispatcherLookupExtendedParams.DispatcherLookupExtended lle2 = new DispatcherLookupExtendedParams.DispatcherLookupExtended(
                url2, lookup2, DispatcherSchedule.createDispatcherSchedule(null));
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
