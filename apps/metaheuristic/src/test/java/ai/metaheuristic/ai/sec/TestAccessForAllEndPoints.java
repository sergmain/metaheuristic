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

package ai.metaheuristic.ai.sec;

import ai.metaheuristic.ai.Consts;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author Serge
 * Date: 7/25/2019
 * Time: 10:40 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class})
@ActiveProfiles("dispatcher")
public class TestAccessForAllEndPoints {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    public void setup() {
        this.mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private enum AccessMethod {POST, GET}

    @Data
    public static class AccessUrl {
        public final String url;
        public final AccessMethod accessMethod;
    }

    private static final AccessUrl[] ACCOUNT_URLS = new AccessUrl[]{
            new AccessUrl("/dispatcher/account/accounts", AccessMethod.GET),
            new AccessUrl("/dispatcher/account/accounts-part", AccessMethod.POST),
            new AccessUrl("/dispatcher/account/account-password-edit-commit", AccessMethod.POST),
            new AccessUrl("/dispatcher/account/account-password-edit/1", AccessMethod.GET),
            new AccessUrl("/dispatcher/account/account-edit-commit", AccessMethod.POST),
            new AccessUrl("/dispatcher/account/account-edit/1", AccessMethod.GET),
            new AccessUrl("/dispatcher/account/account-add-commit", AccessMethod.POST),
            new AccessUrl("/dispatcher/account/account-add", AccessMethod.GET)
    };

    private static final AccessUrl[] ACCOUNT_REST_URLS = new AccessUrl[]{
            new AccessUrl("/rest/v1/dispatcher/account/accounts", AccessMethod.GET),
            new AccessUrl("/rest/v1/dispatcher/account/account/1", AccessMethod.GET),
            new AccessUrl("/rest/v1/dispatcher/account/account-add-commit", AccessMethod.POST),
            new AccessUrl("/rest/v1/dispatcher/account/account-edit-commit", AccessMethod.POST),
            new AccessUrl("/rest/v1/dispatcher/account/account-password-edit-commit", AccessMethod.POST),
            new AccessUrl("/rest/v1/dispatcher/account/account-account-role-commit", AccessMethod.POST)
    };

    private static final AccessUrl[] EXPERIMENT_RESULT_URLS = new AccessUrl[]{
            new AccessUrl("/dispatcher/experiment-result/experiment-results", AccessMethod.GET),
            new AccessUrl("/dispatcher/experiment-result/experiment-results-part", AccessMethod.POST),
            new AccessUrl("/dispatcher/experiment-result/experiment-result-info/1", AccessMethod.GET),
            new AccessUrl("/dispatcher/experiment-result/experiment-result-delete/1", AccessMethod.GET),
            new AccessUrl("/dispatcher/experiment-result/experiment-result-delete-commit", AccessMethod.POST),
            new AccessUrl("/dispatcher/experiment-result/experiment-result-upload-from-file", AccessMethod.POST),
            new AccessUrl("/dispatcher/experiment-result/experiment-result-export/experiment-result-1.yaml", AccessMethod.GET),
            new AccessUrl("/dispatcher/experiment-result/experiment-result-export-import/1", AccessMethod.GET),
            new AccessUrl("/dispatcher/experiment-result/experiment-result-feature-progress/1/1/1", AccessMethod.GET),
            new AccessUrl("/dispatcher/experiment-result/experiment-result-feature-plot-data-part/1/1/1/1/1/part", AccessMethod.POST),
            new AccessUrl("/dispatcher/experiment-result/experiment-result-feature-progress-console-part/1/1", AccessMethod.POST),
            new AccessUrl("/dispatcher/experiment-result/experiment-result-feature-progress-part/1/1/1/1/part", AccessMethod.POST),
    };

    private static final AccessUrl[] EXPERIMENT_RESULT_REST_URLS = new AccessUrl[]{
            new AccessUrl("/rest/v1/dispatcher/experiment-result/experiment-results", AccessMethod.GET),
            new AccessUrl("/rest/v1/dispatcher/experiment-result/experiment-result-info/1", AccessMethod.GET),
            new AccessUrl("/rest/v1/dispatcher/experiment-result/experiment-result-delete-commit", AccessMethod.POST),
            new AccessUrl("/rest/v1/dispatcher/experiment-result/experiment-result-feature-progress/1/1/1", AccessMethod.POST),
            new AccessUrl("/rest/v1/dispatcher/experiment-result/experiment-result-feature-plot-data-part/1/1/1/1/1/part", AccessMethod.POST),
            new AccessUrl("/rest/v1/dispatcher/experiment-result/experiment-result-feature-progress-console-part/1/1", AccessMethod.POST),
            new AccessUrl("/rest/v1/dispatcher/experiment-result/experiment-result-feature-progress-part/1/1/1/1/part", AccessMethod.POST)
    };

    private static final AccessUrl[] SERVER_REST_URLS = new AccessUrl[]{
            new AccessUrl("/rest/v1/srv/1", AccessMethod.POST),
            new AccessUrl("/rest/v1/srv-v2/1", AccessMethod.POST),
            new AccessUrl("/rest/v1/payload/resource/1/1", AccessMethod.GET),
            new AccessUrl("/rest/v1/payload/resource/variable/1/1", AccessMethod.GET),
            new AccessUrl("/rest/v1/upload/1", AccessMethod.POST),
            new AccessUrl("/rest/v1/payload/function-config/1", AccessMethod.POST),
            new AccessUrl("/rest/v1/test", AccessMethod.GET),
    };

    // test anonymous access

    @Test
    public void testAnonymousAccessRestriction() throws Exception {
        checkRestAccessRestriction(SERVER_REST_URLS);
        checkAccessRestriction(EXPERIMENT_RESULT_URLS);
        checkRestAccessRestriction(EXPERIMENT_RESULT_REST_URLS);
        checkAccessRestriction(ACCOUNT_URLS);
        checkRestAccessRestriction(ACCOUNT_REST_URLS);
    }

    public void checkRestAccessRestriction(AccessUrl[] accountRestUrls) throws Exception {
        for (AccessUrl accessUrl : accountRestUrls) {
            System.out.println("accessUrl: " + accessUrl);
            ResultActions resultActions;
            if (accessUrl.accessMethod== AccessMethod.GET) {
                resultActions = mockMvc.perform(get(accessUrl.url));
            }
            else if (accessUrl.accessMethod== AccessMethod.POST) {
                resultActions = mockMvc.perform(post(accessUrl.url));
            }
            else {
                throw new IllegalStateException("Unknown http method: " + accessUrl.accessMethod);
            }
            resultActions.andExpect(status().isUnauthorized()).andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME));
        }
    }

    public void checkAccessRestriction(AccessUrl[] accountUrls) throws Exception {
        for (AccessUrl accessUrl : accountUrls) {
            System.out.println("accessUrl: " + accessUrl);
            if (accessUrl.accessMethod== AccessMethod.GET) {
                mockMvc.perform(get(accessUrl.url))
                        .andExpect(status().isFound())
                        .andExpect(redirectedUrlPattern("http://*/login"))
                        .andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME));
            }
            else if (accessUrl.accessMethod== AccessMethod.POST) {
                mockMvc.perform(post(accessUrl.url))
                        .andExpect(status().isForbidden())
                        .andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME));
            }
            else {
                throw new IllegalStateException("Unknown http method: " + accessUrl.accessMethod);
            }
        }
    }

    @Test
    @WithUserDetails("data_rest")
    public void testRestPayload_asRest() throws Exception {
        final String url = "/rest/v1/payload/resource/variable/f8ce9508-15-114784-aaa-task-114783-ml_model.bin";
        //noinspection ConstantConditions
        assertTrue(url.endsWith(".bin"));

        mockMvc.perform(
                get(url + "?processorId=15&id=42&chunkSize=10000000&chunkNum=0")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
        )
                .andExpect(status().isGone());
    }

    @Test
    @WithUserDetails("data")
    public void testRestPayload_asUser() throws Exception {
        final String url = "/rest/v1/payload/resource/variable/f8ce9508-15-114784-aaa-task-114783-ml_model.bin";
        //noinspection ConstantConditions
        assertTrue(url.endsWith(".bin"));

        mockMvc.perform(
                get(url + "?processorId=15&id=42&chunkSize=10000000&chunkNum=0")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
        )
                .andExpect(status().isForbidden());
    }

    // TODO 2019-07-25 add all end-points (rest and normal one) here to check access restriction

}
