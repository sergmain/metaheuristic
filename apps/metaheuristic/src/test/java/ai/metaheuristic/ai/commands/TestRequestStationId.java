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
    public StationsRepository stationsRepository;

    private Long stationId;

    private Long stationIdForEmptySession;

    @Before
    public void before() {

    }

    @After
    public void afterPreparingExperiment() {
        log.info("Start after()");
        if (stationId!=null) {
            try {
                stationsRepository.deleteById(stationId);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }

        if (stationIdForEmptySession!=null) {
            try {
                stationsRepository.deleteById(stationIdForEmptySession);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    @Test
    public void testRequestStationId() {
        ExchangeData data = new ExchangeData();

        ExchangeData d = serverService.processRequest(data, "127.0.0.1");

        assertNotNull(d);
        assertNotNull(d.getAssignedStationId());
        assertNotNull(d.getAssignedStationId().getAssignedStationId());
        assertNotNull(d.getAssignedStationId().getAssignedSessionId());

        System.out.println("stationId: " + d.getAssignedStationId().getAssignedStationId());
        System.out.println("sessionId: " + d.getAssignedStationId().getAssignedSessionId());

        stationId = Long.valueOf(d.getAssignedStationId().getAssignedStationId());

        Station s = stationsRepository.findById(stationId).orElse(null);

        assertNotNull(s);
    }

    @Test
    public void testEmptySessionId() {
        final ExchangeData data = new ExchangeData();

        // value of stationId doesn't matter here
        data.initRequestToLaunchpad("123445", null);

        ExchangeData d = serverService.processRequest(data, "127.0.0.1");

        assertNotNull(d);
        assertNotNull(d.getAssignedStationId());
        assertNotNull(d.getAssignedStationId().getAssignedStationId());
        assertNotNull(d.getAssignedStationId().getAssignedSessionId());

        System.out.println("stationId: " + d.getAssignedStationId().getAssignedStationId());
        System.out.println("sessionId: " + d.getAssignedStationId().getAssignedSessionId());

        stationIdForEmptySession = Long.valueOf(d.getAssignedStationId().getAssignedStationId());

        Station s = stationsRepository.findById(stationIdForEmptySession).orElse(null);

        assertNotNull(s);
    }
}
