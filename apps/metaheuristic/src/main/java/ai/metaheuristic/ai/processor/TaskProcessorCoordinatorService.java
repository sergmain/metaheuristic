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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.processor.data.ProcessorData;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.processor.variable_providers.VariableProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class TaskProcessorCoordinatorService {

    private final Globals globals;
    private final ProcessorTaskService processorTaskService;
    private final CurrentExecState currentExecState;
    private final DispatcherLookupExtendedService dispatcherLookupExtendedService;
    private final MetadataParams metadataService;
    private final ProcessorEnvironment processorEnvironment;
    private final ProcessorService processorService;
    private final VariableProviderFactory resourceProviderFactory;
    private final GitSourcingService gitSourcingService;

    // key -coreCode, value
    private final Map<String, TaskProcessor> taskProcessors = new HashMap<>();

    public void fixedDelay() {
        if (globals.testing) {
            return;
        }
        if (!globals.processor.enabled) {
            return;
        }
        log.info("#415.020 Start processing task by cores. taskProcessors.size(): {}", taskProcessors.size());

        for (ProcessorData.ProcessorCoreAndProcessorIdAndDispatcherUrlRef core : metadataService.getAllEnabledRefsForCores()) {
            TaskProcessor taskProcessor = taskProcessors.computeIfAbsent( core.coreCode,
                    o -> new TaskProcessor(globals, processorTaskService, currentExecState, dispatcherLookupExtendedService, metadataService, processorEnvironment, processorService, resourceProviderFactory, gitSourcingService));
            new Thread(()-> taskProcessor.process(core), "task-processor-"+core.coreCode).start();
        }
    }
}