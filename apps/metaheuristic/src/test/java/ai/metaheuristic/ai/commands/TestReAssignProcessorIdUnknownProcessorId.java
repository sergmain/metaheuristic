/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTransactionService;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
public class TestReAssignProcessorIdUnknownProcessorId {

    @Autowired
    public SouthbridgeService serverService;

    @Autowired
    public ProcessorCache processorCache;

    @Autowired
    public ProcessorTransactionService processorTransactionService;

    private Long processorIdBefore;
    private String sessionIdBefore;

    private Long processorIdAfter;
    private String sessionIdAfter;

    private Long unknownProcessorId;

    @BeforeEach
    public void before() {

        for (int i = 0; i < 100; i++) {
            final long id = -1L - i;
            Processor s = processorCache.findById(id);
            if (s==null) {
                unknownProcessorId = id;
                break;
            }
        }
        if (unknownProcessorId ==null) {
            throw new IllegalStateException("Can't find id which isn't belong to any processor");
        }

        ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req = new ProcessorCommParamsYaml.ProcessorRequest(Consts.DEFAULT_PROCESSOR_CODE);
        processorComm.requests.add(req);

        String dispatcherResponse = serverService.processRequest(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm), "127.0.0.1");

        DispatcherCommParamsYaml dispatcherComm = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(dispatcherComm);
        assertNotNull(dispatcherComm.responses);
        assertEquals(1, dispatcherComm.responses.size());
        final DispatcherCommParamsYaml.AssignedProcessorId assignedProcessorId = dispatcherComm.responses.get(0).getAssignedProcessorId();
        assertNotNull(assignedProcessorId);
        assertNotNull(assignedProcessorId.getAssignedProcessorId());
        assertNotNull(assignedProcessorId.getAssignedSessionId());

        processorIdBefore = Long.valueOf(assignedProcessorId.getAssignedProcessorId());
        sessionIdBefore = assignedProcessorId.getAssignedSessionId();

        assertTrue(sessionIdBefore.length()>5);

        System.out.println("processorIdBefore: " + processorIdBefore);
        System.out.println("sessionIdBefore: " + sessionIdBefore);
    }

    @AfterEach
    public void afterPreparingExperiment() {
        log.info("Start after()");
        if (processorIdBefore !=null) {
            try {
                processorTransactionService.deleteById(processorIdBefore);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    @Test
    public void testReAssignProcessorIdUnknownProcessorId() {

        // in this scenario we test that processor has got a new re-assigned processorId

        ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req = new ProcessorCommParamsYaml.ProcessorRequest(Consts.DEFAULT_PROCESSOR_CODE);
        processorComm.requests.add(req);

        req.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(unknownProcessorId.toString(), sessionIdBefore.substring(0, 4));

        String dispatcherResponse = serverService.processRequest(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm), "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(d);
        assertNotNull(d.responses);
        assertEquals(1, d.responses.size());
        final DispatcherCommParamsYaml.ReAssignProcessorId reAssignedProcessorId = d.responses.get(0).getReAssignedProcessorId();
        assertNotNull(reAssignedProcessorId);
        assertNotNull(reAssignedProcessorId.getReAssignedProcessorId());
        assertNotNull(reAssignedProcessorId.getSessionId());

        Long processorId = Long.valueOf(reAssignedProcessorId.getReAssignedProcessorId());

        assertNotEquals(unknownProcessorId, processorId);

        Processor s = processorCache.findById(processorId);

        assertNotNull(s);
    }
}
