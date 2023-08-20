/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.commons.CommonConsts;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class, TestRest.JsonTestController.class})
//@ActiveProfiles({"dispatcher", "mysql"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestRest {

    private static final String MSG_TEXT = "test msg, ИИИ, 日本語, natürlich";

    @RestController
    public static class JsonTestController {

        // This isn't the test
        // see testNearMessages() below
        @GetMapping("/rest/test/message")
        public NewMessage getMessage() {
            return new NewMessage("42", MSG_TEXT);
        }

        @GetMapping("/rest/test/simple")
        public String getMessage(@RequestParam String text) {
            return text;
        }
    }
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
    @WithUserDetails("data_rest")
    public void testRestMessages_01() throws Exception {
        MvcResult result = mockMvc.perform(
                get("http://localhost:8080/rest/test/simple?text="+CommonConsts.MULTI_LANG_STRING))
                .andExpect(status().isOk()).andReturn();

        String s = result.getResponse().getContentAsString();
        assertNotNull(s);
        System.out.println("s = " + s);
        assertEquals(CommonConsts.MULTI_LANG_STRING, s.split("\n")[0]);
    }

    // let's test the case with marshalling message to json
    @Test
    @WithUserDetails("data_rest")
    public void testNearMessages() throws Exception {
        MvcResult result = mockMvc
                .perform(get("/rest/test/message"))
                .andExpect(status().isOk())
                .andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        NewMessage m = new NewMessage("42", MSG_TEXT);

        String json = JsonUtils.getMapper().writeValueAsString(m);
        assertEquals(json, content);
        System.out.println(content);
    }


    @Test
    public void testJsonFromEmpty_1() {
        System.out.println("testJsonFromEmpty_1()");
        String json = "";

        assertThrows(MismatchedInputException.class, ()->JsonUtils.getMapper().readValue(json, NewMessage.class));
//        assertEquals(MSG_TEXT, msg.t);
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
        final ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();

        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm);

        MvcResult result = mockMvc.perform(post("/rest/v1/srv-v2/qwe321").contentType(Consts.APPLICATION_JSON_UTF8)
                .content(processorYaml))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME)).andReturn();

        String dispatcherYaml = result.getResponse().getContentAsString();
        System.out.println("dispatcherYaml = " + dispatcherYaml);

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherYaml);

        assertNotNull(d);
        assertTrue(d.isSuccess());

    }
}
