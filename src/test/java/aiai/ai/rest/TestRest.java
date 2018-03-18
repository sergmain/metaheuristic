/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.rest;

import aiai.ai.Consts;
import aiai.ai.beans.InviteResult;
import aiai.ai.comm.Command;
import aiai.ai.comm.ExchangeData;
import aiai.ai.comm.Protocol;
import aiai.ai.core.JsonService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.Cookie;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class, AuthenticationProviderForTests.class, TestRest.JsonTestController.class})
public class TestRest {

    private MockMvc mockMvc;

    @Autowired
    private JsonService jsonService;

    @RestController
    public static class JsonTestController {

        // This isn't test
        // see testNearMessages()
        @GetMapping("/rest-anon/test/message")
        public NewMessage getMessage() {
            NewMessage m = new NewMessage();
            m.setId("42");
            m.setT("test msg");
            return m;
        }
    }

    // тестируем, что сообщения маршалятся в json
    @Test
    public void testNearMessages() throws Exception {
        MvcResult result = mockMvc.perform(get("/rest-anon/test/message"))
                .andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();
        System.out.println(content);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Before
    public void setup() {
        this.mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    public void testUnauthorizedAccessToTest() throws Exception {
        mockMvc.perform(get("/rest-auth/test"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME));
    }

    @Test
    public void testAnonymousAccessToTest() throws Exception {
        mockMvc.perform(get("/rest-anon/test"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME));
    }

    @Test
    @WithUserDetails("admin")
    public void whenTestAdminCredentials_thenOk() throws Exception {
        MvcResult result = mockMvc.perform(get("/rest-auth/test"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME)).andReturn();

        Cookie[] cookies = result.getResponse().getCookies();
        Assert.assertNotNull(cookies);
        Assert.assertEquals(0, cookies.length);

        mockMvc.perform(get("/rest-auth/test"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME));

    }

    @Test
//    @WithUserDetails("admin")
    public void testSimpleCommunicationWithServer() throws Exception {
        ExchangeData dataReqest = new ExchangeData(new Protocol.Ok());
        String jsonReqest = jsonService.getMapper().writeValueAsString(dataReqest);
        MvcResult result = mockMvc.perform(post("/rest-anon/srv").contentType(Consts.APPLICATION_JSON_UTF8)
                .content(jsonReqest))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME)).andReturn();

        String json = result.getResponse().getContentAsString();
        System.out.println("json = " + json);
        ExchangeData data = jsonService.getMapper().readValue(json, ExchangeData.class);

        Assert.assertNotNull(data);
        Assert.assertTrue(data.isSuccess());

    }

    @Test
//    @WithUserDetails("admin")
    public void testRegisterInvite() throws Exception {
        ExchangeData dataReqest = new ExchangeData(new Protocol.RegisterInvite("invite-123"));
        String jsonReqest = jsonService.getMapper().writeValueAsString(dataReqest);
        MvcResult result = mockMvc.perform(post("/rest-anon/srv").contentType(Consts.APPLICATION_JSON_UTF8)
                .content(jsonReqest))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME)).andReturn();

        String json = result.getResponse().getContentAsString();
        System.out.println("json = " + json);
        ExchangeData data = jsonService.getMapper().readValue(json, ExchangeData.class);

        Assert.assertNotNull(data);
        Assert.assertTrue(data.isSuccess());
        Assert.assertEquals(1, data.getCommands().size());
        Command command = data.getCommands().get(0);
        Assert.assertEquals(Command.Type.RegisterInviteResult, command.getType());
        Protocol.RegisterInviteResult registerInviteResult = (Protocol.RegisterInviteResult)command;
        InviteResult inviteResult = registerInviteResult.getInviteResult();
        Assert.assertNotNull(inviteResult);
        Assert.assertNotNull(inviteResult.getUsername());
        Assert.assertNotNull(inviteResult.getToken());
        Assert.assertNotNull(inviteResult.getPassword());

    }

}
