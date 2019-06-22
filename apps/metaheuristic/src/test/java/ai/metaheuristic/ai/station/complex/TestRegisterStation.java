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

package ai.metaheuristic.ai.station.complex;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.comm.ExchangeData;
import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.core.JsonUtils;
import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.ai.launchpad.repositories.StationsRepository;
import ai.metaheuristic.ai.rest.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.ai.station.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.env.EnvYaml;
import ai.metaheuristic.ai.yaml.station_status.StationStatus;
import ai.metaheuristic.ai.yaml.station_status.StationStatusUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:47 AM
 */
@SuppressWarnings("FieldCanBeLocal")
@RunWith(SpringRunner.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class})
@ActiveProfiles("launchpad")
@Slf4j
public class TestRegisterStation {

    private MockMvc mockMvc;

    private String stationIdAsStr = null;
    private Long stationId = null;
    private Set<Long> stationIds =new HashSet<>();
    private String sessionId = null;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private StationsRepository stationsRepository;

    @Before
    public void setup() {
        this.mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @After
    public void afterTest() {
        for (Long id : stationIds) {
            try {
                stationsRepository.deleteById(id);
            } catch (Throwable th) {
                log.error("Error", th);
            }
        }
    }

    @Test
    @WithUserDetails("rest")
    public void testRestPayload_asRest() throws Exception {

        ExchangeData data = new ExchangeData();

        ExchangeData ed = requestServer(data);

        assertNotNull(ed.getAssignedStationId());
        assertNotNull(ed.getAssignedStationId().getAssignedStationId());
        assertFalse(ed.getAssignedStationId().getAssignedStationId().isBlank());
        assertNotNull(ed.getAssignedStationId().getAssignedSessionId());
        assertFalse(ed.getAssignedStationId().getAssignedSessionId().isBlank());

        stationIdAsStr = ed.getAssignedStationId().getAssignedStationId();
        stationId = Long.valueOf(stationIdAsStr);
        stationIds.add(stationId);
        sessionId = ed.getAssignedStationId().getAssignedSessionId();

        // init stationId and sessionId must be first operation. Otherwise, commands won't be inited correctly.
        data.initRequestToLaunchpad(stationIdAsStr, sessionId);

        final Protocol.StationTaskStatus stationTaskStatus = new Protocol.StationTaskStatus(new ArrayList<>());
        data.setCommand(stationTaskStatus);

        final StationStatus ss = new StationStatus(
                new EnvYaml(),
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.installed, "Git 1.0.0", null),
                "0:00 - 23:59",
                sessionId,
                System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, true,
                1);
        final Protocol.ReportStationStatus reportStationStatus = new Protocol.ReportStationStatus(ss);

        data.setCommand(reportStationStatus);
        data.setCommand(new Protocol.RequestTask(false));
        data.setCommand(new Protocol.CheckForMissingOutputResources());

        ed = requestServer(data);
        if (ed.getAssignedStationId()!=null) {
            // collect for deletion
            stationIds.add(Long.valueOf(ed.getAssignedStationId().getAssignedStationId()));
        }
        assertNull(ed.getAssignedStationId());

        Station s = stationsRepository.findById(stationId).orElse(null);
        assertNotNull(s);

        StationStatus ss1 = StationStatusUtils.to(s.status);
        assertEquals(ss, ss1);

        //noinspection unused
        int i=0;
    }

    public ExchangeData requestServer(ExchangeData data) throws Exception {
        final String url = "/rest/v1/srv/"+ UUID.randomUUID().toString();
        MvcResult result = mockMvc
                .perform(buildPostRequest(data, url))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        System.out.println(content);

        return JsonUtils.getExchangeData(content);
    }

    public MockHttpServletRequestBuilder buildPostRequest(ExchangeData data, String url) throws IOException {
        return MockMvcRequestBuilders
                .post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtils.toJson(data));
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Test
    @WithUserDetails("user")
    public void testRestPayload_asUser() throws Exception {
        ExchangeData data = new ExchangeData();

        final String url = "/rest/v1/srv/"+ UUID.randomUUID().toString();
        mockMvc.perform(buildPostRequest(data, url))
                .andExpect(status().isForbidden());

    }
}
