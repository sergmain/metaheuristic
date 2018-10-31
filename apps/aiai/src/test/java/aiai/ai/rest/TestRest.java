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
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.InviteResult;
import aiai.ai.launchpad.beans.Station;
import aiai.ai.comm.Command;
import aiai.ai.comm.ExchangeData;
import aiai.ai.comm.Protocol;
import aiai.ai.core.JsonUtils;
import aiai.ai.launchpad.repositories.StationsRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class, AuthenticationProviderForTests.class, TestRest.JsonTestController.class})
@ActiveProfiles("launchpad")
public class TestRest {

    @RestController
    public static class JsonTestController {

        // This isn't test
        // see testNearMessages() below
        @GetMapping("/rest-anon/test/message")
        public NewMessage getMessage() {
            return new NewMessage("42", "test msg");
        }
    }
    private MockMvc mockMvc;
    @Autowired
    private StationsRepository stationsRepository;

    @Autowired
    private Globals globals;
    @Autowired
    private WebApplicationContext webApplicationContext;

    // тестируем, что сообщения маршалятся в json
    @Test
    public void testNearMessages() throws Exception {
        MvcResult result = mockMvc.perform(get("/rest-anon/test/message")).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();

        NewMessage m = new NewMessage("42", "test msg");

        String json = JsonUtils.getMapper().writeValueAsString(m);
        Assert.assertEquals(json, content);
        System.out.println(content);
    }

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
        if (globals.isSecureRestUrl) {
            return;
        }
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
        if (globals.isSecureRestUrl) {
            return;
        }
        ExchangeData dataReqest = new ExchangeData(new Protocol.Nop());
        String jsonReqest = JsonUtils.toJson(dataReqest);
        MvcResult result = mockMvc.perform(post("/rest-anon/srv").contentType(Consts.APPLICATION_JSON_UTF8)
                .content(jsonReqest))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME)).andReturn();

        String json = result.getResponse().getContentAsString();
        System.out.println("json = " + json);
        ExchangeData data = JsonUtils.getExchangeData(json);

        Assert.assertNotNull(data);
        Assert.assertTrue(data.isSuccess());

    }

    @Test
//    @WithUserDetails("admin")
    public void testRegisterInvite() throws Exception {
        ExchangeData dataReqest = new ExchangeData(new Protocol.RegisterInvite("invite-123"));
        Station s = new Station();
        stationsRepository.save(s);

        dataReqest.setStationId(s.getId().toString());
        String jsonReqest = JsonUtils.toJson(dataReqest);
        MvcResult result;
        if (globals.isSecureRestUrl) {
            result = mockMvc.perform(post("/rest-anon/srv").contentType(Consts.APPLICATION_JSON_UTF8)
                    .content(jsonReqest))
                    .andExpect(status().isForbidden()).andReturn();
        } else {

            result = mockMvc.perform(post("/rest-anon/srv").contentType(Consts.APPLICATION_JSON_UTF8)
                    .content(jsonReqest))
                    .andExpect(status().isOk())
                    .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME)).andReturn();
            String json = result.getResponse().getContentAsString();
            System.out.println("json = " + json);
            ExchangeData data = JsonUtils.getExchangeData(json);

            Assert.assertNotNull(data);
            Assert.assertTrue(data.isSuccess());
            // 2 - because we add current state of all experiments to all responses
            Assert.assertEquals(2, data.getCommands().size());
            Protocol.RegisterInviteResult registerInviteResult = data.getRegisterInviteResult();
            Assert.assertNotNull(registerInviteResult);
            InviteResult inviteResult = registerInviteResult.getInviteResult();
            Assert.assertNotNull(inviteResult);
            Assert.assertNotNull(inviteResult.getUsername());
            Assert.assertNotNull(inviteResult.getToken());
            Assert.assertNotNull(inviteResult.getPassword());
        }

        stationsRepository.delete(s);
    }

    @Test
    public void testEmptyStationIdWithoutSercuredRest() throws Exception {
        if (globals.isSecureRestUrl) {
            return;
        }
        ExchangeData dataReqest = new ExchangeData(new Protocol.RegisterInvite("invite-123"));
        String jsonReqest = JsonUtils.toJson(dataReqest);
        MvcResult result = mockMvc.perform(post("/rest-anon/srv").contentType(Consts.APPLICATION_JSON_UTF8)
                .content(jsonReqest))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.SESSIONID_NAME)).andReturn();

        String json = result.getResponse().getContentAsString();
        ExchangeData data = JsonUtils.getExchangeData(json);
        Assert.assertNotNull(data);
        Assert.assertTrue(data.isSuccess());
        Assert.assertEquals(1, data.getCommands().size());
        Command command = data.getCommands().get(0);
        Assert.assertEquals(Command.Type.AssignedStationId, command.getType());
        Protocol.AssignedStationId assignedStationId = (Protocol.AssignedStationId) command;
        Assert.assertNotNull(assignedStationId.getAssignedStationId());

        final long id = Long.parseLong(assignedStationId.getAssignedStationId());
        stationsRepository.deleteById(id);
        Assert.assertNull(stationsRepository.findById(id).orElse(null));
    }

    @Test
    public void testEmptyStationId() throws Exception {
        if (!globals.isSecureRestUrl) {
            return;
        }
        ExchangeData dataReqest = new ExchangeData(new Protocol.RegisterInvite("invite-123"));
        String jsonReqest = JsonUtils.toJson(dataReqest);
        mockMvc.perform(post("/rest-anon/srv").contentType(Consts.APPLICATION_JSON_UTF8)
                .content(jsonReqest))
                .andExpect(status().isForbidden());
    }

}
