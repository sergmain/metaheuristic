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
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTopLevelService;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.api.ConstsApi;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestRequestProcessorId {

    @Autowired
    public SouthbridgeService serverService;

    @Autowired
    public ProcessorCache processorCache;

    @Autowired
    public ProcessorTopLevelService processorTopLevelService;

    private Long processorId;

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
        assertNotNull(assignedProcessorId.getAssignedProcessorId());
        assertNotNull(assignedProcessorId.getAssignedSessionId());

        processorId = Long.valueOf(assignedProcessorId.getAssignedProcessorId());

        System.out.println("processorId: " + processorId);
    }

    @AfterEach
    public void afterPreparingExperiment() {
        log.info("Start after()");
        if (processorId !=null) {
            try {
                processorTopLevelService.deleteProcessorById(processorId);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    @Test
    public void testRequestProcessorId() {
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
        assertNotNull(assignedProcessorId.getAssignedProcessorId());
        assertNotNull(assignedProcessorId.getAssignedSessionId());

        System.out.println("processorId: " + assignedProcessorId.getAssignedProcessorId());
        System.out.println("sessionId: " + assignedProcessorId.getAssignedSessionId());

        processorId = Long.valueOf(assignedProcessorId.getAssignedProcessorId());

        Processor s = processorCache.findById(processorId);

        assertNotNull(s);
    }

    @Test
    public void testEmptySessionId() {
        ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req = new ProcessorCommParamsYaml.ProcessorRequest(ConstsApi.DEFAULT_PROCESSOR_CODE);
        processorComm.requests.add(req);

        req.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorId.toString(), null);

        String dispatcherResponse = serverService.processRequest(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm), "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);
        assertNotNull(d);
        assertNotNull(d.responses);
        assertEquals(1, d.responses.size());
        final DispatcherCommParamsYaml.ReAssignProcessorId reAssignedProcessorId = d.responses.get(0).getReAssignedProcessorId();
        assertNotNull(reAssignedProcessorId);

        assertNotNull(reAssignedProcessorId.getReAssignedProcessorId());
        assertNotNull(reAssignedProcessorId.getSessionId());
        // actually, only sessionId was changed, processorId must be the same

        Long processorIdForEmptySession = Long.valueOf(reAssignedProcessorId.getReAssignedProcessorId());

        assertEquals(processorId, processorIdForEmptySession);


        System.out.println("processorId: " + reAssignedProcessorId.getReAssignedProcessorId());
        System.out.println("sessionId: " + reAssignedProcessorId.getSessionId());

        Processor s = processorCache.findById(processorIdForEmptySession);

        assertNotNull(s);
    }
}
