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
import ai.metaheuristic.ai.dispatcher.repositories.StationsRepository;
import ai.metaheuristic.ai.dispatcher.server.ServerService;
import ai.metaheuristic.ai.dispatcher.station.StationCache;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.station.StationCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatusYamlUtils;
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
public class TestReAssignStationIdTimeoutSessionId {

    @Autowired
    public ServerService serverService;

    @Autowired
    public StationCache stationCache;

    @Autowired
    public StationsRepository stationsRepository;

    private Long stationIdBefore;
    private String sessionIdBefore;
    private long sessionCreatedOn;

    @Before
    public void before() {

        final StationCommParamsYaml stationComm = new StationCommParamsYaml();
        final String stationYaml = StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm);
        String launchpadResponse = serverService.processRequest(stationYaml, "127.0.0.1");

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

        Long stationId = stationIdBefore;
        Station s = stationsRepository.findByIdForUpdate(stationId);
        assertNotNull(s);

        StationStatusYaml ss = StationStatusYamlUtils.BASE_YAML_UTILS.to(s.status);
        assertNotEquals(0L, ss.sessionCreatedOn);
        assertEquals(sessionIdBefore, ss.sessionId);

        ss.sessionCreatedOn -= (ServerService.SESSION_TTL + 100000);
        sessionCreatedOn = ss.sessionCreatedOn;
        s.status = StationStatusYamlUtils.BASE_YAML_UTILS.toString(ss);

        Station s1 = stationCache.save(s);

        StationStatusYaml ss1 = StationStatusYamlUtils.BASE_YAML_UTILS.to(s1.status);
        assertEquals(ss.sessionCreatedOn, ss1.sessionCreatedOn);
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
    public void testReAssignStationIdTimeoutSessionId() {

        // in this scenario we test that a station has got a refreshed sessionId

        final StationCommParamsYaml stationComm = new StationCommParamsYaml();
        stationComm.stationCommContext = new StationCommParamsYaml.StationCommContext(stationIdBefore.toString(), sessionIdBefore);

        final String stationYaml = StationCommParamsYamlUtils.BASE_YAML_UTILS.toString(stationComm);
        String launchpadResponse = serverService.processRequest(stationYaml, "127.0.0.1");

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(launchpadResponse);

        assertNotNull(d);

        Station s = stationCache.findById(stationIdBefore);

        assertNotNull(s);
        StationStatusYaml ss = StationStatusYamlUtils.BASE_YAML_UTILS.to(s.status);
        assertNotEquals(0L, ss.sessionCreatedOn);
        assertNotEquals(sessionCreatedOn, ss.sessionCreatedOn);
        assertEquals(sessionIdBefore, ss.sessionId);
        assertTrue(ss.sessionCreatedOn > sessionCreatedOn);
    }
}
