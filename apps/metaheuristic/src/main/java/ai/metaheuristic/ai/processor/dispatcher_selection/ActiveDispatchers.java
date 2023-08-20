/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.dispatcher_selection;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.ProcessorAndCoreData;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupParamsYaml;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Serge
 * Date: 4/7/2021
 * Time: 8:15 PM
 */
@Slf4j
public class ActiveDispatchers {

    // key - , value - true if this DispatcherUrl is active
    private final Map<ProcessorAndCoreData.DispatcherUrl, AtomicBoolean> urls;

    public ActiveDispatchers(Map<ProcessorAndCoreData.DispatcherUrl, DispatcherLookupExtendedParams.DispatcherLookupExtended> dispatchers,
                             String info, Enums.DispatcherSelectionStrategy selectionStrategy) {

        LinkedHashMap<ProcessorAndCoreData.DispatcherUrl, AtomicBoolean> map = new LinkedHashMap<>();
        List<DispatcherLookupParamsYaml.DispatcherLookup> dispatcherLookupList = new ArrayList<>();

        for (Map.Entry<ProcessorAndCoreData.DispatcherUrl, DispatcherLookupExtendedParams.DispatcherLookupExtended> entry : dispatchers.entrySet() ) {
            DispatcherLookupParamsYaml.DispatcherLookup dispatcherLookup = entry.getValue().dispatcherLookup;
            if (dispatcherLookup.disabled) {
                log.info("{}, dispatcher {} is disabled", info, dispatcherLookup.url);
                continue;
            }
            log.info("{}, dispatcher {} was added to selection", info, dispatcherLookup.url);
            dispatcherLookupList.add(dispatcherLookup);
        }

        sortListOfDispatchers(selectionStrategy, dispatcherLookupList);

        for (DispatcherLookupParamsYaml.DispatcherLookup dispatcherLookup : dispatcherLookupList) {
            map.putIfAbsent(new ProcessorAndCoreData.DispatcherUrl(dispatcherLookup.url), new AtomicBoolean(true));
        }

        urls = Collections.unmodifiableMap(map);
    }

    public static void sortListOfDispatchers(Enums.DispatcherSelectionStrategy selectionStrategy, List<DispatcherLookupParamsYaml.DispatcherLookup> dispatcherLookupList) {
        if (selectionStrategy == Enums.DispatcherSelectionStrategy.priority) {
            dispatcherLookupList.sort((o, o1) -> Integer.compare(o1.priority, o.priority));
        }
        else if (selectionStrategy == Enums.DispatcherSelectionStrategy.alphabet) {
            dispatcherLookupList.sort(Comparator.comparing(o -> o.url));
        }
        else {
            throw new IllegalStateException("unknown strategy");
        }
    }

    public Map<ProcessorAndCoreData.DispatcherUrl, AtomicBoolean> getActiveDispatchers() {
        return urls;
    }


}
