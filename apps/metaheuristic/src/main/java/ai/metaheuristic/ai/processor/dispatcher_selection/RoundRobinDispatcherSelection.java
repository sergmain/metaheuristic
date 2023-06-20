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

package ai.metaheuristic.ai.processor.dispatcher_selection;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.processor_environment.DispatcherLookupExtendedParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;

@Slf4j
public class RoundRobinDispatcherSelection {

    private final ActiveDispatchers activeDispatchers;

    public RoundRobinDispatcherSelection(Map<DispatcherUrl, DispatcherLookupExtendedParams.DispatcherLookupExtended> dispatchers, String info) {
        activeDispatchers = new ActiveDispatchers(dispatchers, info, Enums.DispatcherSelectionStrategy.alphabet);
    }

    @Nullable
    public DispatcherUrl next() {
        DispatcherUrl url = findNext();
        if (url != null) {
            return url;
        }
        reset();
        url = findNext();
        return url;
    }

    public void reset() {
        for (Map.Entry<DispatcherUrl, AtomicBoolean> entry : activeDispatchers.getActiveDispatchers().entrySet()) {
            entry.getValue().set(true);
        }
    }

    @Nullable
    private DispatcherUrl findNext() {
        DispatcherUrl url = null;
        for (Map.Entry<DispatcherUrl, AtomicBoolean> entry : activeDispatchers.getActiveDispatchers().entrySet()) {
            if (entry.getValue().get()) {
                entry.getValue().set(false);
                url = entry.getKey();
                break;
            }
        }
        return url;
    }
}
