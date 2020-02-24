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
import ai.metaheuristic.ai.dispatcher.server.ServerService;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 5/19/2019
 * Time: 3:14 AM
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
public class TestRequestStationId {

    @Autowired
    public ServerService serverService;

    @Autowired
    public ProcessorCache processorCache;

    private Long stationId;

    @Before
    public void before() {
        ProcessorCommParamsYaml stationComm = new ProcessorCommParamsYaml();

        String dispatcherResponse = serverService.processRequest(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm), "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(d);
        assertNotNull(d.getAssignedProcessorId());
        assertNotNull(d.getAssignedProcessorId().getAssignedProcessorId());
        assertNotNull(d.getAssignedProcessorId().getAssignedSessionId());

        stationId = Long.valueOf(d.getAssignedProcessorId().getAssignedProcessorId());

        System.out.println("stationId: " + stationId);
    }

    @After
    public void afterPreparingExperiment() {
        log.info("Start after()");
        if (stationId!=null) {
            try {
                processorCache.deleteById(stationId);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    @Test
    public void testRequestStationId() {
        ProcessorCommParamsYaml stationComm = new ProcessorCommParamsYaml();

        String dispatcherResponse = serverService.processRequest(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm), "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);


        assertNotNull(d);
        assertNotNull(d.getAssignedProcessorId());
        assertNotNull(d.getAssignedProcessorId().getAssignedProcessorId());
        assertNotNull(d.getAssignedProcessorId().getAssignedSessionId());

        System.out.println("stationId: " + d.getAssignedProcessorId().getAssignedProcessorId());
        System.out.println("sessionId: " + d.getAssignedProcessorId().getAssignedSessionId());

        stationId = Long.valueOf(d.getAssignedProcessorId().getAssignedProcessorId());

        Processor s = processorCache.findById(stationId);

        assertNotNull(s);
    }

    @Test
    public void testEmptySessionId() {
        ProcessorCommParamsYaml stationComm = new ProcessorCommParamsYaml();
        stationComm.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(stationId.toString(), null);

        String dispatcherResponse = serverService.processRequest(ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm), "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);
        assertNotNull(d);
        assertNotNull(d.getReAssignedProcessorId());
        assertNotNull(d.getReAssignedProcessorId().getReAssignedProcessorId());
        assertNotNull(d.getReAssignedProcessorId().getSessionId());
        // actually, only sessionId was changed, stationId must be the same

        Long stationIdForEmptySession = Long.valueOf(d.getReAssignedProcessorId().getReAssignedProcessorId());

        assertEquals(stationId, stationIdForEmptySession);


        System.out.println("stationId: " + d.getReAssignedProcessorId().getReAssignedProcessorId());
        System.out.println("sessionId: " + d.getReAssignedProcessorId().getSessionId());

        Processor s = processorCache.findById(stationIdForEmptySession);

        assertNotNull(s);
    }
}
