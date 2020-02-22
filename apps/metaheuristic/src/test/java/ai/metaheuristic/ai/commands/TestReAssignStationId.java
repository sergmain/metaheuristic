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

import ai.metaheuristic.ai.mh.dispatcher..beans.Station;
import ai.metaheuristic.ai.mh.dispatcher..server.ServerService;
import ai.metaheuristic.ai.mh.dispatcher..station.StationCache;
import ai.metaheuristic.ai.yaml.communication.mh.dispatcher..DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.mh.dispatcher..DispatcherCommParamsYamlUtils;
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Serge
 * Date: 5/19/2019
 * Time: 3:14 AM
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("launchpad")
public class TestReAssignStationId {

    @Autowired
    public ServerService serverService;

    @Autowired
    public StationCache stationCache;

    private Long stationIdBefore;
    private String sessionIdBefore;

    @Before
    public void before() {
        StationCommParamsYaml stationComm = new StationCommParamsYaml();

        String launchpadResponse = serverService.processRequest(StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm), "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse);

        assertNotNull(d);
        assertNotNull(d.getAssignedStationId());
        assertNotNull(d.getAssignedStationId().getAssignedStationId());
        assertNotNull(d.getAssignedStationId().getAssignedSessionId());

        stationIdBefore = Long.valueOf(d.getAssignedStationId().getAssignedStationId());
        sessionIdBefore = d.getAssignedStationId().getAssignedSessionId();

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
    public void testRequestStationId() {

        // in this scenario we test that station has got a new re-assigned stationId

        final StationCommParamsYaml stationComm = new StationCommParamsYaml();
        stationComm.stationCommContext = new StationCommParamsYaml.StationCommContext(stationIdBefore.toString(), sessionIdBefore.substring(0, 4));
        final String stationYaml = StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm);
        String launchpadResponse = serverService.processRequest(stationYaml, "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse);

        assertNotNull(d);
        assertNotNull(d.getReAssignedStationId());
        assertNotNull(d.getReAssignedStationId().getReAssignedStationId());
        assertNotNull(d.getReAssignedStationId().getSessionId());

        Long stationId = Long.valueOf(d.getReAssignedStationId().getReAssignedStationId());

        Station s = stationCache.findById(stationId);

        assertNotNull(s);
    }
}
