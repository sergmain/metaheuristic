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

import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.server.ServerService;
import ai.metaheuristic.ai.launchpad.station.StationCache;
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
public class TestReAssignStationIdUnknownStationId {

    @Autowired
    public ServerService serverService;

    @Autowired
    public StationCache stationCache;

    private Long stationIdBefore;
    private String sessionIdBefore;

    private Long stationIdAfter;
    private String sessionIdAfter;

    private Long unknownStationId;

    @Before
    public void before() {

        for (int i = 0; i < 100; i++) {
            final long id = -1L - i;
            Station s = stationCache.findById(id);
            if (s==null) {
                unknownStationId = id;
                break;
            }
        }
        if (unknownStationId==null) {
            throw new IllegalStateException("Can't find id which isn't belong to any station");
        }

        StationCommParamsYaml stationComm = new StationCommParamsYaml();

        String launchpadResponse = serverService.processRequest(StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm), "127.0.0.1");

        LaunchpadCommParamsYaml launchpadComm = LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse);

        assertNotNull(launchpadComm);
        assertNotNull(launchpadComm.getAssignedStationId());
        assertNotNull(launchpadComm.getAssignedStationId().getAssignedStationId());
        assertNotNull(launchpadComm.getAssignedStationId().getAssignedSessionId());

        stationIdBefore = Long.valueOf(launchpadComm.getAssignedStationId().getAssignedStationId());
        sessionIdBefore = launchpadComm.getAssignedStationId().getAssignedSessionId();

        assertTrue(sessionIdBefore.length()>5);

        System.out.println("stationIdBefore: " + stationIdBefore);
        System.out.println("sessionIdBefore: " + sessionIdBefore);
    }

    @After
    public void afterPreparingExperiment() {
        log.info("Start after()");
        if (stationIdBefore!=null) {
            try {
                stationCache.deleteById(stationIdBefore);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    @Test
    public void testReAssignStationIdUnknownStationId() {

        // in this scenario we test that station has got a new re-assigned stationId

        StationCommParamsYaml stationComm = new StationCommParamsYaml();
        stationComm.stationCommContext = new StationCommParamsYaml.StationCommContext(unknownStationId.toString(), sessionIdBefore.substring(0, 4));


        String launchpadResponse = serverService.processRequest(StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm), "127.0.0.1");

        LaunchpadCommParamsYaml d = LaunchpadCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse);

        assertNotNull(d);
        assertNotNull(d.getReAssignedStationId());
        assertNotNull(d.getReAssignedStationId().getReAssignedStationId());
        assertNotNull(d.getReAssignedStationId().getSessionId());

        Long stationId = Long.valueOf(d.getReAssignedStationId().getReAssignedStationId());

        assertNotEquals(unknownStationId, stationId);

        Station s = stationCache.findById(stationId);

        assertNotNull(s);
    }
}
