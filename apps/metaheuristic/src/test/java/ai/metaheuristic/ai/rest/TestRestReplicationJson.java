/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.rest;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.dispatcher.replication.ReplicationSourceService;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.ai.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author Serge
 * Date: 10/21/2020
 * Time: 10:18 AM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class})
@ActiveProfiles("dispatcher")
@DirtiesContext
@AutoConfigureCache
public class TestRestReplicationJson {

    @Autowired
    private ReplicationSourceService replicationSourceService;

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    public void setup() {
        this.mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithUserDetails("asset_rest")
    public void test() throws Exception {

        ReplicationData.AssetStateResponse assets = replicationSourceService.currentAssets();

        String s = JsonUtils.getMapper().writeValueAsString(assets);
        assertFalse(s.contains("infoMessagesAsList"));
        assertFalse(s.contains("errorMessagesAsList"));
        assertFalse(s.contains("errorMessagesAsStr"));

        ReplicationData.AssetStateResponse response = JsonUtils.getMapper().readValue(s, ReplicationData.AssetStateResponse.class);
        assertNull(response.getErrorMessages());

        MvcResult result = mockMvc.perform(get("/rest/v1/replication/current-assets")
                .contentType(Consts.APPLICATION_JSON_UTF8)
        )
                .andExpect(status().isOk()).andReturn();

        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(content.contains("infoMessagesAsList"));
        assertFalse(content.contains("errorMessagesAsList"));
        assertFalse(content.contains("errorMessagesAsStr"));

        assets = JsonUtils.getMapper().readValue(content, ReplicationData.AssetStateResponse.class);


        result = mockMvc.perform(get("/test/asset-with-error")
                .contentType(Consts.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk()).andReturn();

        content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assets = JsonUtils.getMapper().readValue(content, ReplicationData.AssetStateResponse.class);
        assertNotNull(assets.errorMessages, content);
        assertNull(assets.infoMessages);
        assertEquals(1, assets.errorMessages.size());
        assertEquals("asset-error", assets.errorMessages.get(0));

        result = mockMvc.perform(get("/test/asset-with-info")
                .contentType(Consts.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk()).andReturn();

        content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assets = JsonUtils.getMapper().readValue(content, ReplicationData.AssetStateResponse.class);
        assertNotNull(assets.infoMessages);
        assertNull(assets.errorMessages);
        assertEquals(1, assets.infoMessages.size());
        assertEquals("asset-info", assets.infoMessages.get(0));

        result = mockMvc.perform(get("/test/asset-with-info-and-error")
                .contentType(Consts.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk()).andReturn();

        content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assets = JsonUtils.getMapper().readValue(content, ReplicationData.AssetStateResponse.class);
        assertNotNull(assets.infoMessages);
        assertNotNull(assets.errorMessages);
        assertEquals(1, assets.infoMessages.size());
        assertEquals(1, assets.errorMessages.size());
        assertEquals("asset-info", assets.infoMessages.get(0));
        assertEquals("asset-error", assets.errorMessages.get(0));

        result = mockMvc.perform(get("/test/asset-ok-info")
                .contentType(Consts.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk()).andReturn();

        content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assets = JsonUtils.getMapper().readValue(content, ReplicationData.AssetStateResponse.class);
        assertNotNull(assets.infoMessages);
        assertNull(assets.errorMessages);

        assertEquals(1, assets.infoMessages.size());

        assertNotNull(assets.functions);
        assertNotNull(assets.companies);
        assertNotNull(assets.sourceCodeUids);
        assertNotNull(assets.usernames);

        assertEquals(2, assets.functions.size());
        assertEquals(2, assets.companies.size());
        assertEquals(2, assets.sourceCodeUids.size());
        assertEquals(2, assets.usernames.size());


        int i=0;
    }


}
