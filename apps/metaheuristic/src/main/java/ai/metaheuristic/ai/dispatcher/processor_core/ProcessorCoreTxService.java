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

package ai.metaheuristic.ai.dispatcher.processor_core;

import ai.metaheuristic.ai.dispatcher.beans.ProcessorCore;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorCoreRepository;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Serge
 * Date: 4/30/2022
 * Time: 11:54 PM
 */
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ProcessorCoreTxService {

    private final ProcessorCoreRepository processorCoreRepository;

    @Transactional
    public ProcessorCore createProcessorCore(Long processorId, KeepAliveRequestParamYaml.Core core) {
        CoreStatusYaml ss = new CoreStatusYaml();
        ss.tags = core.tags;
        ss.code = core.coreCode;
        ss.currDir = core.coreDir;

        ProcessorCore pc = new ProcessorCore();
        pc.processorId = processorId;
        pc.updatedOn = System.currentTimeMillis();
        pc.code = core.coreCode;

        pc.updateParams(ss);
        return processorCoreRepository.save(pc);
    }

    @Transactional
    public void deleteOrphanProcessorCores(List<Long> ids) {
        processorCoreRepository.deleteByIds(ids);
    }

    @Transactional
    public void deleteProcessorCoreById(Long id) {
        processorCoreRepository.deleteById(id);
    }

    @Transactional
    public void updateCore(ProcessorCore processorCore, KeepAliveRequestParamYaml.Core core) {
        CoreStatusYaml status = processorCore.getCoreStatusYaml();
        status.currDir = core.coreDir;
        status.tags = core.tags;
        processorCore.updateParams(status);

        processorCoreRepository.save(processorCore);
    }
}
