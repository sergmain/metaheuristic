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

package ai.metaheuristic.ai.commands;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTxService;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.data.DispatcherApiData;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 5/19/2019
 * Time: 3:14 AM
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureCache
@Slf4j
public class TestReAssignProcessorIdTimeoutSessionId {

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

    @Autowired public SouthbridgeService serverService;
    @Autowired public ProcessorCache processorCache;
    @Autowired public ProcessorRepository processorRepository;
    @Autowired public ProcessorTopLevelService processorTopLevelService;
    @Autowired public ProcessorTxService processorTransactionService;

    private Long processorIdBefore;
    private String sessionIdBefore;
    private long sessionCreatedOn;

    @BeforeEach
    public void before() {

        final ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();

        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm);
        String dispatcherResponse = serverService.processRequest(processorYaml, "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(d);
        final DispatcherCommParamsYaml.AssignedProcessorId assignedProcessorId = d.response.getAssignedProcessorId();
        assertNotNull(assignedProcessorId);
        assertNotNull(assignedProcessorId.getAssignedProcessorId());
        assertNotNull(assignedProcessorId.getAssignedSessionId());

        processorIdBefore = Long.valueOf(assignedProcessorId.getAssignedProcessorId());
        sessionIdBefore = assignedProcessorId.getAssignedSessionId();

        assertTrue(sessionIdBefore.length()>5);

        System.out.println("processorIdBefore: " + processorIdBefore);
        System.out.println("sessionIdBefore: " + sessionIdBefore);

        Long processorId = processorIdBefore;
        Processor s = processorCache.findById(processorId);
        assertNotNull(s);

        ProcessorStatusYaml psy = s.getProcessorStatusYaml();
        assertNotEquals(0L, psy.sessionCreatedOn);
        assertEquals(sessionIdBefore, psy.sessionId);

        psy.sessionCreatedOn -= (Consts.SESSION_TTL + 100000);
        sessionCreatedOn = psy.sessionCreatedOn;
        s.updateParams(psy);

        DispatcherApiData.ProcessorSessionId s1 = processorTransactionService.reassignProcessorId(null, null);

    }

    @AfterEach
    public void afterPreparingExperiment() {
        log.info("Start after()");
        if (processorIdBefore !=null) {
            try {
                processorTopLevelService.deleteProcessorById(processorIdBefore);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    @Test
    public void testReAssignProcessorIdTimeoutSessionId() {

        // in this scenario we test that a processor has got a refreshed sessionId

        final ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();

        processorComm.request.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdBefore, sessionIdBefore);

        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm);
        String dispatcherResponse = serverService.processRequest(processorYaml, "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(d);

        Processor s = processorCache.findById(processorIdBefore);

        assertNotNull(s);
        ProcessorStatusYaml ss = s.getProcessorStatusYaml();
        assertNotEquals(0L, ss.sessionCreatedOn);
        assertNotEquals(sessionCreatedOn, ss.sessionCreatedOn);
        assertEquals(sessionIdBefore, ss.sessionId);
        assertTrue(ss.sessionCreatedOn > sessionCreatedOn);
    }
}
