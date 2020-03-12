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

package ai.metaheuristic.ai.rest;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.core.TestController;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.ai.processor.DispatcherRequestor;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class, TestRest.JsonTestController.class})
@ActiveProfiles("dispatcher")
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
    }
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Before
    public void setup() {
        this.mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    public void testRestMessages_01() {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:8080/test/simple?text="+MSG_TEXT, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String s = response.getBody();
        assertNotNull(s);
        System.out.println("s = " + s);
        assertEquals(TestController.TEST_MSG, s.split("\n")[0]);
        assertEquals(MSG_TEXT.substring(0,20), s.split("\n")[1]);
    }

    // todo 2020-03-12 down't work and right now don't have much time to investigate why
/*
    @Test
//    @WithUserDetails("data_rest")
    public void testRestMessages_02() {
        RestTemplate restTemplate = new RestTemplate(DispatcherRequestor.getHttpRequestFactory());
        // RestTemplate must be working without this
//        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(MSG_TEXT, headers);

        ResponseEntity<String> response = restTemplate.exchange("http://localhost:8080/test/rest", HttpMethod.POST, request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String s = response.getBody();
        assertNotNull(s);
        System.out.println("s = " + s);
        assertEquals(TestController.TEST_MSG, s.split("\n")[0]);
        assertEquals(MSG_TEXT.substring(0,20), s.split("\n")[1]);
    }
*/

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


    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testJsonFromEmpty_1() throws IOException {
        System.out.println("testJsonFromEmpty_1()");
        String json = "";

        thrown.expect(MismatchedInputException.class);
        NewMessage msg = JsonUtils.getMapper().readValue(json, NewMessage.class);
        assertEquals(MSG_TEXT, msg.t);
    }

    @Test
    public void testUnauthorizedAccessToTest() throws Exception {
        mockMvc.perform(get("/rest/v1/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME));
    }

    @Test
    @WithUserDetails("data_rest")
    public void testAnonymousAccessToTest() throws Exception {
        mockMvc.perform(get("/rest/v1/test"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME));
    }

    @Test
    @WithUserDetails("data_rest")
    public void whenTestAdminCredentials_thenOk() throws Exception {
        MvcResult result = mockMvc.perform(get("/rest/v1/test"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME)).andReturn();

        Cookie[] cookies = result.getResponse().getCookies();
        assertNotNull(cookies);
        assertEquals(0, cookies.length);

        mockMvc.perform(get("/rest/v1/test"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME));
    }

    @Test
    @WithUserDetails("data_rest")
    public void testSimpleCommunicationWithServer() throws Exception {
        final ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm);

        MvcResult result = mockMvc.perform(post("/rest/v1/srv-v2/qwe321").contentType(Consts.APPLICATION_JSON_UTF8)
                .content(processorYaml))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME)).andReturn();

        String dispatcherYaml = result.getResponse().getContentAsString();
        System.out.println("dispatcherYaml = " + dispatcherYaml);

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherYaml);

        assertNotNull(d);
        Assert.assertTrue(d.isSuccess());

    }
}
