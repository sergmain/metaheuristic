/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher_selection;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.dispatcher_selection.ActiveDispatchers;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 11/28/2021
 * Time: 11:17 PM
 */
public class TestActiveDispatchers {

    @Test
    public void testSorting() {
        DispatcherLookup d1 = new DispatcherLookup();
        d1.url = "url1";
        d1.priority = 1;

        DispatcherLookup d2 = new DispatcherLookup();
        d2.url = "url2";
        d2.priority = 10;

        List<DispatcherLookup> list = new ArrayList<>(List.of(d1, d2));
        ActiveDispatchers.sortListOfDispatchers(Enums.DispatcherSelectionStrategy.priority, list);

        assertEquals("url2", list.get(0).url);
        assertEquals("url1", list.get(1).url);

        list = new ArrayList<>(List.of(d2, d1));
        ActiveDispatchers.sortListOfDispatchers(Enums.DispatcherSelectionStrategy.priority, list);

        assertEquals("url2", list.get(0).url);
        assertEquals("url1", list.get(1).url);

        list = new ArrayList<>(List.of(d2, d1));
        ActiveDispatchers.sortListOfDispatchers(Enums.DispatcherSelectionStrategy.alphabet, list);

        assertEquals("url1", list.get(0).url);
        assertEquals("url2", list.get(1).url);

        list = new ArrayList<>(List.of(d2, d1));
        ActiveDispatchers.sortListOfDispatchers(Enums.DispatcherSelectionStrategy.alphabet, list);

        assertEquals("url1", list.get(0).url);
        assertEquals("url2", list.get(1).url);


    }
}
