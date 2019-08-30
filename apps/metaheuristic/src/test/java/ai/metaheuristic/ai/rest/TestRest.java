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
import ai.metaheuristic.ai.core.JsonUtils;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
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
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.Cookie;

import java.io.IOException;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class, TestRest.JsonTestController.class})
@ActiveProfiles("launchpad")
public class TestRest {

    @RestController
    public static class JsonTestController {

        // This isn't the test
        // see testNearMessages() below
        @GetMapping("/rest/test/message")
        public NewMessage getMessage() {
            return new NewMessage("42", "test msg");
        }
    }
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    // let's test the case with marshalling message to json
    @Test
    @WithUserDetails("data_rest")
    public void testNearMessages() throws Exception {
        MvcResult result = mockMvc
                .perform(get("/rest/test/message"))
                .andExpect(status().isOk())
                .andReturn();
        String content = result.getResponse().getContentAsString();

        NewMessage m = new NewMessage("42", "test msg");

        String json = JsonUtils.getMapper().writeValueAsString(m);
        Assert.assertEquals(json, content);
        System.out.println(content);
    }


    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testJsonFromEmpty_1() throws IOException {
        System.out.println("testJsonFromEmpty_1()");
        String json = "";

        thrown.expect(MismatchedInputException.class);
        JsonUtils.getMapper().readValue(json, NewMessage.class);
    }

    @Before
    public void setup() {
        this.mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
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
        Assert.assertNotNull(cookies);
        Assert.assertEquals(0, cookies.length);

        mockMvc.perform(get("/rest/v1/test"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME));
    }

    @Test
    @WithUserDetails("data_rest")
    public void testSimpleCommunicationWithServer() throws Exception {
        ExchangeData dataRequest = new ExchangeData(new Protocol.Nop());
        String jsonRequest = JsonUtils.toJson(dataRequest);
        MvcResult result = mockMvc.perform(post("/rest/v1/srv/qwe321").contentType(Consts.APPLICATION_JSON_UTF8)
                .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME)).andReturn();

        String json = result.getResponse().getContentAsString();
        System.out.println("json = " + json);
        ExchangeData data = JsonUtils.getExchangeData(json);

        Assert.assertNotNull(data);
        Assert.assertTrue(data.isSuccess());

    }
}
