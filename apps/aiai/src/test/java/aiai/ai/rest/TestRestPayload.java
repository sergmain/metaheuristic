package aiai.ai.rest;

import aiai.ai.Globals;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.auth.BasicUserPrincipal;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class, AuthenticationProviderForTests.class})
@ActiveProfiles("launchpad")
public class TestRestPayload {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Globals globals;

    @Autowired
    private AuthenticationProviderForTests authenticationProviderForTests;

    @Before
    public void setup() {
        this.mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithUserDetails("rest")
    public void testRestPayload() throws Exception {
/*
        @SuppressWarnings("unused") @PathVariable("random-part") String randomPart,
        @SuppressWarnings("unused") String stationId,
        @SuppressWarnings("unused") Long taskId,
        String code) throws IOException {
*/
        //
//        final String url = "/rest-anon/payload/resource/DATA/qwe321.bin";
        final String url = "/rest-auth/payload/resource/DATA/f8ce9508-15-114784-aaa-task-114783-ml_model.bin";
        //noinspection ConstantConditions
        assertTrue(url.endsWith(".bin"));
//        globals.isSecureLaunchpadRestUrl = false;

        MvcResult result = mockMvc.perform(
                get(url + "?stationId=15&taskId=114784&code=aaa-task-114783-ml_model.bin")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
        )
                .andExpect(status().isGone())
                .andReturn();

    }

    private static HttpHeaders getAuthHttpHeaders(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        byte[] encodedAuth = Base64.encodeBase64((username+":"+password).getBytes(StandardCharsets.US_ASCII));
        String authHeader = "Basic " + new String(encodedAuth);
        headers.set("Authorization", authHeader);
        return headers;
    }

}
