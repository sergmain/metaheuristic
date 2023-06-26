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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.processor_environment.DispatcherLookupExtendedParams;
import ai.metaheuristic.ai.processor.processor_environment.ProcessorEnvironment;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static ai.metaheuristic.ai.processor.ProcessorAndCoreData.DispatcherUrl;

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

    @Data
    @RequiredArgsConstructor
    public static class Requesters {
        public final DispatcherRequestor dispatcherRequestor;
        public final ProcessorKeepAliveRequestor processorKeepAliveRequestor;
    }
    public final Map<DispatcherUrl, Requesters> dispatcherRequestorMap = new HashMap<>();

    public DispatcherRequestorHolderService(
            Globals globals,
            ProcessorService processorService, ProcessorTaskService processorTaskService,
            CurrentExecState currentExecState,
            ProcessorCommandProcessor processorCommandProcessor,
            ProcessorKeepAliveProcessor processorKeepAliveProcessor, ProcessorEnvironment processorEnvironment
    ) {

        for (Map.Entry<DispatcherUrl, DispatcherLookupExtendedParams.DispatcherLookupExtended> entry : processorEnvironment.dispatcherLookupExtendedService.lookupExtendedMap.entrySet()) {
            final DispatcherLookupExtendedParams.DispatcherLookupExtended dispatcher = entry.getValue();
            final DispatcherRequestor requestor = new DispatcherRequestor(dispatcher.getDispatcherUrl(), globals,
                    processorTaskService, processorService, processorEnvironment.metadataService, currentExecState,
                    processorEnvironment.dispatcherLookupExtendedService, processorCommandProcessor);

            final ProcessorKeepAliveRequestor keepAliveRequestor = new ProcessorKeepAliveRequestor(
                    dispatcher.dispatcherUrl, globals, processorService, processorKeepAliveProcessor, processorEnvironment);

            dispatcherRequestorMap.put(dispatcher.dispatcherUrl, new Requesters(requestor, keepAliveRequestor));
        }
    }

}
