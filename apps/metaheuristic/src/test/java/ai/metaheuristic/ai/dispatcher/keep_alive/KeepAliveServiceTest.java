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

package ai.metaheuristic.ai.dispatcher.keep_alive;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.ProcessorCore;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTxService;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorCoreRepository;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveResponseParamYaml;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.lang.Nullable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.UUID;

import static ai.metaheuristic.ai.dispatcher.keep_alive.KeepAliveService.coreMetadataDifferent;
import static ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml.Core;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sergio Lissner
 * Date: 10/29/2023
 * Time: 9:26 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class KeepAliveServiceTest {
    public static final String HOME_CORE_1 = "/home/core-1";
    public static final String HOME_CORE_2 = "/home/core-2";

    @Autowired private ProcessorCoreRepository processorCoreRepository;
    @Autowired private ProcessorCoreRepository processorRepository;
    @Autowired private ProcessorTxService processorTxService;
    @Autowired private KeepAliveService keepAliveService;

    Processor processor = null;

    @BeforeEach
    public void beforeEach() {
        ProcessorStatusYaml.Env envYaml = new ProcessorStatusYaml.Env();
        envYaml.quotas.disabled = true;

        ProcessorStatusYaml psy = new ProcessorStatusYaml(envYaml,
            new GtiUtils.GitStatusInfo(EnumsApi.GitStatus.not_found), "",
            ""+ UUID.randomUUID(), System.currentTimeMillis(),
            Consts.UNKNOWN_INFO, Consts.UNKNOWN_INFO, null, false,
            TaskParamsYamlUtils.UTILS.getDefault().getVersion(), EnumsApi.OS.unknown, Consts.UNKNOWN_INFO, null, null);

        processor = processorTxService.createProcessor("processor for testing", "127.0.0.1", psy);

    }

    @AfterEach
    public void afterEach() {
        if (processor!=null) {
            try {
                List<ProcessorData.ProcessorCore> cores = processorCoreRepository.findIdsAndCodesByProcessorId(processor.id);
                for (ProcessorData.ProcessorCore core : cores) {
                    try {
                        deleteProcessorCore(core);
                    } catch (Exception e) {
                        //
                    }
                }
                processorRepository.deleteById(processor.id);
            } catch (Throwable th) {
                //
            }
        }
    }

    private void deleteProcessorCore(ProcessorData.ProcessorCore core) {
        try {
            processorCoreRepository.deleteById(core.id());
        } catch (Throwable th) {
            //
        }
    }

    @Test
    public void test_processInfoAboutCores_all_null() {
        KeepAliveRequestParamYaml req = new KeepAliveRequestParamYaml();
        KeepAliveRequestParamYaml.Core core1 = new Core(HOME_CORE_1, null, HOME_CORE_1, null);
        KeepAliveRequestParamYaml.Core core2 = new Core(HOME_CORE_2, null, HOME_CORE_2, null);
        req.cores.add(core1);
        req.cores.add(core2);

        KeepAliveResponseParamYaml resp = new KeepAliveResponseParamYaml();

        // do
        keepAliveService.processInfoAboutCores(processor.id, req, System.currentTimeMillis()+100_000, resp);


        assertEquals(2, resp.response.coreInfos.size());
        KeepAliveResponseParamYaml.CoreInfo ci1 = resp.response.coreInfos.stream().filter(core->core.code.equals(HOME_CORE_1)).findFirst().orElseThrow();
        KeepAliveResponseParamYaml.CoreInfo ci2 = resp.response.coreInfos.stream().filter(core->core.code.equals(HOME_CORE_2)).findFirst().orElseThrow();

        assertNotNull(ci1.code);
        assertNotNull(ci1.coreId);
        assertNotNull(ci2.code);
        assertNotNull(ci2.coreId);
        assertNotEquals(ci1.coreId, ci2.coreId);
    }

    @Test
    public void test_processInfoAboutCores_all_start_with_old_dispatcher_yaml() {

        Long maxId = getMaxCoreId();

        KeepAliveRequestParamYaml req = new KeepAliveRequestParamYaml();
        KeepAliveRequestParamYaml.Core core1 = new Core(HOME_CORE_1, null, HOME_CORE_1, null);
        KeepAliveRequestParamYaml.Core core2 = new Core(HOME_CORE_2, maxId+1, HOME_CORE_2, null);
        req.cores.add(core1);
        req.cores.add(core2);

        KeepAliveResponseParamYaml resp = new KeepAliveResponseParamYaml();

        // do
        keepAliveService.processInfoAboutCores(processor.id, req, System.currentTimeMillis()+100_000, resp);


        assertEquals(2, resp.response.coreInfos.size());
        KeepAliveResponseParamYaml.CoreInfo ci1 = resp.response.coreInfos.stream().filter(core->core.code.equals(HOME_CORE_1)).findFirst().orElseThrow();
        KeepAliveResponseParamYaml.CoreInfo ci2 = resp.response.coreInfos.stream().filter(core->core.code.equals(HOME_CORE_2)).findFirst().orElseThrow();

        assertNotNull(ci1.code);
        assertNotNull(ci1.coreId);
        assertNotNull(ci2.code);
        assertNotNull(ci2.coreId);
        assertNotEquals(ci1.coreId, ci2.coreId);
    }

    private Long getMaxCoreId() {
        Long maxId = processorRepository.getMaxCoreId();
        if (maxId==null) {
            // test on empty db
            CoreStatusYaml csy2 = new CoreStatusYaml(HOME_CORE_2, null, null);
            ProcessorCore processorCore = processorTxService.createProcessorCore("Core #2", csy2, processor.id);
            maxId = processorCore.id;
        }

        return maxId;
    }
}
