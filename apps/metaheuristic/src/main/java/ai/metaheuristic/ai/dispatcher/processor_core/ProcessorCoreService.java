/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.processor_core;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.ProcessorCore;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTransactionService;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorCoreRepository;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.DispatcherApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * @author Serge
 * Date: 4/30/2022
 * Time: 11:54 PM
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ProcessorCoreService {

    private final ProcessorCoreRepository processorCoreRepository;
    private final ProcessorCoreCache coreCache;

    @Transactional
    public Long getNewProcessorCoreId(@Nullable Long processorId) {
        CoreStatusYaml csy = new CoreStatusYaml();
        final ProcessorCore p = createProcessorCore(processorId, csy);
        return p.id;
    }

    @Transactional
    public ProcessorCore createProcessorCore(@Nullable Long processorId, CoreStatusYaml ss) {
        ProcessorCore p = new ProcessorCore();
        p.processorId = processorId;
        p.updatedOn = System.currentTimeMillis();
        p.updateParams(ss);
        return coreCache.save(p);
    }

    @Transactional
    public Long reassignProcessorCoreId(@Nullable Long processorId) {
        CoreStatusYaml csy = new CoreStatusYaml();
        ProcessorCore p = createProcessorCore(processorId, csy);

        return p.id;
    }


}
