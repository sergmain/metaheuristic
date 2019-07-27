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

import ai.metaheuristic.ai.comm.ExchangeData;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.repositories.StationsRepository;
import ai.metaheuristic.ai.launchpad.server.ServerService;
import ai.metaheuristic.ai.launchpad.station.StationCache;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
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
public class TestReAssignStationIdTimeoutDifferentSessionId {

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

        ExchangeData d = serverService.processRequest(new ExchangeData(), "127.0.0.1");

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

        StationStatus ss = StationStatusUtils.to(s.status);
        assertNotEquals(0L, ss.sessionCreatedOn);
        assertEquals(sessionIdBefore, ss.sessionId);

        ss.sessionCreatedOn -= (ServerService.SESSION_TTL + 100000);
        sessionCreatedOn = ss.sessionCreatedOn;
        s.status = StationStatusUtils.toString(ss);

        Station s1 = stationCache.save(s);

        StationStatus ss1 = StationStatusUtils.to(s1.status);
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
    public void testReAssignStationIdDifferentSessionId() {

        // in this scenario we test that a station has got a refreshed sessionId

        final ExchangeData data = new ExchangeData();
        final String newSessionId = sessionIdBefore + '-';
        data.initRequestToLaunchpad(stationIdBefore.toString(), newSessionId);

        ExchangeData d = serverService.processRequest(data, "127.0.0.1");

        assertNotNull(d);
        assertNotNull(d.getReAssignedStationId());
        assertNotNull(d.getReAssignedStationId().getReAssignedStationId());
        assertNotNull(d.getReAssignedStationId().getSessionId());

        final Long stationId = Long.valueOf(d.getReAssignedStationId().getReAssignedStationId());
        assertEquals(stationIdBefore, stationId);
        assertNotEquals(newSessionId, d.getReAssignedStationId().getSessionId());

        Station s = stationCache.findById(stationId);

        assertNotNull(s);
        StationStatus ss = StationStatusUtils.to(s.status);
        assertNotEquals(0L, ss.sessionCreatedOn);
        assertNotEquals(sessionCreatedOn, ss.sessionCreatedOn);
        assertEquals(d.getReAssignedStationId().getSessionId(), ss.sessionId);
        assertTrue(ss.sessionCreatedOn > sessionCreatedOn);
    }
}
