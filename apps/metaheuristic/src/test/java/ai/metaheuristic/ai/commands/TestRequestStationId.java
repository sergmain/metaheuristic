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

import ai.metaheuristic.ai.dispatcher.beans.Station;
import ai.metaheuristic.ai.dispatcher.server.ServerService;
import ai.metaheuristic.ai.dispatcher.station.StationCache;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.launchpad.LaunchpadCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYamlUtils;
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
@ActiveProfiles("launchpad")
public class TestRequestStationId {

    @Autowired
    public ServerService serverService;

    @Autowired
    public StationCache stationCache;

    private Long stationId;

    @Before
    public void before() {
        StationCommParamsYaml stationComm = new StationCommParamsYaml();

        String launchpadResponse = serverService.processRequest(StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm), "127.0.0.1");

        LaunchpadCommParamsYaml d = LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse);

        assertNotNull(d);
        assertNotNull(d.getAssignedStationId());
        assertNotNull(d.getAssignedStationId().getAssignedStationId());
        assertNotNull(d.getAssignedStationId().getAssignedSessionId());

        stationId = Long.valueOf(d.getAssignedStationId().getAssignedStationId());

        System.out.println("stationId: " + stationId);
    }

    @After
    public void afterPreparingExperiment() {
        log.info("Start after()");
        if (stationId!=null) {
            try {
                stationCache.deleteById(stationId);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    @Test
    public void testRequestStationId() {
        StationCommParamsYaml stationComm = new StationCommParamsYaml();

        String launchpadResponse = serverService.processRequest(StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm), "127.0.0.1");

        LaunchpadCommParamsYaml d = LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse);


        assertNotNull(d);
        assertNotNull(d.getAssignedStationId());
        assertNotNull(d.getAssignedStationId().getAssignedStationId());
        assertNotNull(d.getAssignedStationId().getAssignedSessionId());

        System.out.println("stationId: " + d.getAssignedStationId().getAssignedStationId());
        System.out.println("sessionId: " + d.getAssignedStationId().getAssignedSessionId());

        stationId = Long.valueOf(d.getAssignedStationId().getAssignedStationId());

        Station s = stationCache.findById(stationId);

        assertNotNull(s);
    }

    @Test
    public void testEmptySessionId() {
        StationCommParamsYaml stationComm = new StationCommParamsYaml();
        stationComm.stationCommContext = new StationCommParamsYaml.StationCommContext(stationId.toString(), null);

        String launchpadResponse = serverService.processRequest(StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm), "127.0.0.1");

        LaunchpadCommParamsYaml d = LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse);
        assertNotNull(d);
        assertNotNull(d.getReAssignedStationId());
        assertNotNull(d.getReAssignedStationId().getReAssignedStationId());
        assertNotNull(d.getReAssignedStationId().getSessionId());
        // actually, only sessionId was changed, stationId must be the same

        Long stationIdForEmptySession = Long.valueOf(d.getReAssignedStationId().getReAssignedStationId());

        assertEquals(stationId, stationIdForEmptySession);


        System.out.println("stationId: " + d.getReAssignedStationId().getReAssignedStationId());
        System.out.println("sessionId: " + d.getReAssignedStationId().getSessionId());

        Station s = stationCache.findById(stationIdForEmptySession);

        assertNotNull(s);
    }
}
