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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Globals;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/21/2020
 * Time: 10:40 AM
 */
@Service
@EnableScheduling
@Slf4j
@Profile("processor")
public class DispatcherRequestorHolderService {

    public final Map<String, DispatcherRequestor> dispatcherRequestorMap = new HashMap<>();

    private final MetadataService metadataService;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final CurrentExecState currentExecState;
    private final ProcessorCommandProcessor processorCommandProcessor;

    public DispatcherRequestorHolderService(Globals globals,
            ProcessorService processorService, ProcessorTaskService processorTaskService, MetadataService metadataService,
                                            CurrentExecState currentExecState,
                                            DispatcherLookupExtendedService dispatcherLookupExtendedService,
                                            ProcessorCommandProcessor processorCommandProcessor
    ) {

        this.processorCommandProcessor = processorCommandProcessor;
        this.metadataService = metadataService;
        this.dispatcherLookupExtendedService = dispatcherLookupExtendedService;
        this.currentExecState = currentExecState;


        for (Map.Entry<String, DispatcherLookupExtendedService.DispatcherLookupExtended> entry : dispatcherLookupExtendedService.lookupExtendedMap.entrySet()) {
            final DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher = entry.getValue();
            final DispatcherRequestor requestor = new DispatcherRequestor(dispatcher.dispatcherLookup.url, globals,
                    processorTaskService, processorService, this.metadataService, this.currentExecState,
                    this.dispatcherLookupExtendedService, this.processorCommandProcessor);

            dispatcherRequestorMap.put(dispatcher.dispatcherLookup.url, requestor);
        }
    }

}
