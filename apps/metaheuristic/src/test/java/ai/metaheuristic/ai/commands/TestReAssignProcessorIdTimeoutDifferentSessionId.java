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

package ai.metaheuristic.ai.commands;

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTransactionService;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.DispatcherApiData;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 5/19/2019
 * Time: 3:14 AM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
@DirtiesContext
@AutoConfigureCache
public class TestReAssignProcessorIdTimeoutDifferentSessionId {

    private static final long EXPIRED_SESSION_CREATED_ON = 1L;
    @Autowired
    public SouthbridgeService serverService;

    @Autowired
    public ProcessorCache processorCache;

    @Autowired
    public ProcessorRepository processorRepository;

    @Autowired
    public ProcessorTopLevelService processorTopLevelService;

    @Autowired
    public ProcessorTransactionService processorTransactionService;

    @Autowired
    public TxSupportForTestingService txSupportForTestingService;

    private Long processorIdBefore;
    private String sessionIdBefore;

    @BeforeEach
    public void before() {

        ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm.requests.add(req);

        String dispatcherResponse = serverService.processRequest(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm), "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(d);
        assertNotNull(d.responses);
        assertEquals(1, d.responses.size());
        final DispatcherCommParamsYaml.AssignedProcessorId assignedProcessorId = d.responses.get(0).getAssignedProcessorId();
        assertNotNull(assignedProcessorId);
        assertNotNull(assignedProcessorId.assignedProcessorId);
        assertNotNull(assignedProcessorId.assignedSessionId);

        processorIdBefore = Long.valueOf(assignedProcessorId.assignedProcessorId);
        sessionIdBefore = assignedProcessorId.assignedSessionId;

        assertTrue(sessionIdBefore.length()>5);

        System.out.println("processorIdBefore: " + processorIdBefore);
        System.out.println("sessionIdBefore: " + sessionIdBefore);

        Long processorId = processorIdBefore;
        Processor s = processorCache.findById(processorId);
        assertNotNull(s);

        DispatcherApiData.ProcessorSessionId s1 = processorTransactionService.reassignProcessorId(null, null);
        assertNotEquals(sessionIdBefore, s1.sessionId);

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
    public void testReAssignProcessorIdDifferentSessionId() {

        // in this scenario we test that a processor has got a refreshed sessionId

        setSessionAsExpired();

        ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm.requests.add(req);

        final String newSessionId = sessionIdBefore + '-';
        req.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdBefore.toString(), newSessionId);

        String dispatcherResponse = serverService.processRequest(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm), "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);


        assertNotNull(d);
        assertNotNull(d.responses);
        assertEquals(1, d.responses.size());
        final DispatcherCommParamsYaml.ReAssignProcessorId reAssignedProcessorId = d.responses.get(0).getReAssignedProcessorId();
        assertNotNull(reAssignedProcessorId);
        assertNotNull(reAssignedProcessorId.reAssignedProcessorId);
        assertNotNull(reAssignedProcessorId.sessionId);

        final Long processorId = Long.valueOf(reAssignedProcessorId.getReAssignedProcessorId());
        assertEquals(processorIdBefore, processorId);
        assertNotEquals(newSessionId, reAssignedProcessorId.getSessionId());

        Processor s = processorCache.findById(processorId);

        assertNotNull(s);
        ProcessorStatusYaml ss = s.getProcessorStatusYaml();

        // 0L is default for long type
        assertNotEquals(0L, ss.sessionCreatedOn);

        // 1L was used as an expired value
        assertNotEquals(EXPIRED_SESSION_CREATED_ON, ss.sessionCreatedOn);

        assertEquals(reAssignedProcessorId.sessionId, ss.sessionId);
        assertTrue(ss.sessionCreatedOn > EXPIRED_SESSION_CREATED_ON);
    }

    private void setSessionAsExpired() {
        final Processor processor = processorCache.findById(processorIdBefore);
        assertNotNull(processor);
        ProcessorStatusYaml ss = processor.getProcessorStatusYaml();
        ss.sessionCreatedOn = EXPIRED_SESSION_CREATED_ON;

        processor.updateParams(ss);
        txSupportForTestingService.saveProcessor(processor);
    }
}
