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

package ai.metaheuristic.ai.dispatcher.repositories;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.beans.ProcessorCore;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTxService;
import ai.metaheuristic.ai.yaml.core_status.CoreStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.GtiUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.jspecify.annotations.Nullable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Sergio Lissner
 * Date: 12/24/2023
 * Time: 5:22 PM
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
public class ProcessorCoreRepositoryTest {

    public static final String HOME_CORE_1 = "/home/core-1";
    public static final String HOME_CORE_2 = "/home/core-2";

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @BeforeAll
    static void setSystemProperties() {
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

    @Autowired private ProcessorCoreRepository processorCoreRepository;
    @Autowired private ProcessorCoreRepository processorRepository;
    @Autowired private ProcessorTxService processorTxService;

    Processor processor = null;
    ProcessorCore processorCore1 = null;
    ProcessorCore processorCore2 = null;

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

        CoreStatusYaml csy1 = new CoreStatusYaml(HOME_CORE_1, null, null);
        processorCore1 = processorTxService.createProcessorCore("Core #1", csy1, processor.id);

        CoreStatusYaml csy2 = new CoreStatusYaml(HOME_CORE_2, null, null);
        processorCore2 = processorTxService.createProcessorCore("Core #2", csy2, processor.id);

    }

    @AfterEach
    public void afterEach() {
        if (processor!=null) {
            try {
                processorRepository.deleteById(processor.id);
            } catch (Throwable th) {
                //
            }
        }
        deleteProcessorCore(processorCore1);
        deleteProcessorCore(processorCore2);
    }

    private void deleteProcessorCore(@Nullable ProcessorCore core) {
        if (core !=null) {
            try {
                processorCoreRepository.deleteById(core.id);
            } catch (Throwable th) {
                //
            }
        }
    }

    @Test
    public void test_findIdsAndCodesByProcessorId() {


        List<ProcessorData.ProcessorCore> cores = processorCoreRepository.findIdsAndCodesByProcessorId(processor.id);



        assertEquals(2, cores.size());

        ProcessorData.ProcessorCore pc1 = cores.stream().filter(core->core.code().equals(HOME_CORE_1)).findFirst().orElseThrow();
        ProcessorData.ProcessorCore pc2 = cores.stream().filter(core->core.code().equals(HOME_CORE_2)).findFirst().orElseThrow();

        assertEquals(processorCore1.code, pc1.code());
        assertEquals(processorCore2.code, pc2.code());
    }

}
