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

package ai.metaheuristic.ai.sec_web;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
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

import javax.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author Serge
 * Date: 7/10/2019
 * Time: 1:29 AM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class})
@ActiveProfiles("dispatcher")
@DirtiesContext
@AutoConfigureCache
public class TestAccessRestriction {

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
    public void testUnauthorizedAccessToTest() throws Exception {
        mockMvc.perform(get("/rest/v1/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME));
    }

    @Test
    @WithUserDetails("data_rest")
    public void testAnonymousAccessToTest() throws Exception {
        mockMvc.perform(get("/rest/v1/test"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME));
    }

    @Test
    @WithUserDetails("data_rest")
    public void whenTestAdminCredentials_thenOk() throws Exception {
        MvcResult result = mockMvc.perform(get("/rest/v1/test"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME)).andReturn();

        Cookie[] cookies = result.getResponse().getCookies();
        assertNotNull(cookies);
        assertEquals(0, cookies.length);

        mockMvc.perform(get("/rest/v1/test"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME));
    }

    @Test
    @WithUserDetails("data_rest")
    public void testSimpleCommunicationWithServer() throws Exception {
        ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        ProcessorCommParamsYaml.ProcessorRequest req = new ProcessorCommParamsYaml.ProcessorRequest();
        processorComm.requests.add(req);

        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm);

        MvcResult result = mockMvc.perform(post("/rest/v1/srv-v2/qwe321").contentType(Consts.APPLICATION_JSON_UTF8)
                .content(processorYaml))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME)).andReturn();

        String dispatcherYaml = result.getResponse().getContentAsString();
        System.out.println("yaml = " + dispatcherYaml);

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherYaml);


        assertNotNull(d);
        assertTrue(d.isSuccess());

    }
}

