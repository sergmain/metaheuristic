/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
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
public class TestRequestProcessorId {

    @Autowired
    public SouthbridgeService serverService;

    @Autowired
    public ProcessorCache processorCache;

    private Long processorId;

    @BeforeEach
    public void before() {
        ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();

        String dispatcherResponse = serverService.processRequest(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm), "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(d);
        assertNotNull(d.getAssignedProcessorId());
        assertNotNull(d.getAssignedProcessorId().getAssignedProcessorId());
        assertNotNull(d.getAssignedProcessorId().getAssignedSessionId());

        processorId = Long.valueOf(d.getAssignedProcessorId().getAssignedProcessorId());

        System.out.println("processorId: " + processorId);
    }

    @AfterEach
    public void afterPreparingExperiment() {
        log.info("Start after()");
        if (processorId !=null) {
            try {
                processorCache.deleteById(processorId);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    @Test
    public void testRequestProcessorId() {
        ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();

        String dispatcherResponse = serverService.processRequest(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm), "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);


        assertNotNull(d);
        assertNotNull(d.getAssignedProcessorId());
        assertNotNull(d.getAssignedProcessorId().getAssignedProcessorId());
        assertNotNull(d.getAssignedProcessorId().getAssignedSessionId());

        System.out.println("processorId: " + d.getAssignedProcessorId().getAssignedProcessorId());
        System.out.println("sessionId: " + d.getAssignedProcessorId().getAssignedSessionId());

        processorId = Long.valueOf(d.getAssignedProcessorId().getAssignedProcessorId());

        Processor s = processorCache.findById(processorId);

        assertNotNull(s);
    }

    @Test
    public void testEmptySessionId() {
        ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        processorComm.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorId.toString(), null);

        String dispatcherResponse = serverService.processRequest(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm), "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);
        assertNotNull(d);
        assertNotNull(d.getReAssignedProcessorId());
        assertNotNull(d.getReAssignedProcessorId().getReAssignedProcessorId());
        assertNotNull(d.getReAssignedProcessorId().getSessionId());
        // actually, only sessionId was changed, processorId must be the same

        Long processorIdForEmptySession = Long.valueOf(d.getReAssignedProcessorId().getReAssignedProcessorId());

        assertEquals(processorId, processorIdForEmptySession);


        System.out.println("processorId: " + d.getReAssignedProcessorId().getReAssignedProcessorId());
        System.out.println("sessionId: " + d.getReAssignedProcessorId().getSessionId());

        Processor s = processorCache.findById(processorIdForEmptySession);

        assertNotNull(s);
    }
}
