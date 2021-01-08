/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.ai.utils.JsonUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author Serge
 * Date: 10/21/2020
 * Time: 8:18 AM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@Import({SpringSecurityWebAuxTestConfig.class})
@ActiveProfiles("dispatcher")
@DirtiesContext
@AutoConfigureCache
public class TestRestUploadFunction {

    private static final String FUNCTION_CODE = "get-length-of-file-by-ref-for-test:1.0";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FunctionRepository functionRepository;

    @Autowired
    private TxSupportForTestingService txSupportForTestingService;

    @BeforeEach
    public void setup() {
        this.mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        Function f = functionRepository.findByCode(FUNCTION_CODE);
        if (f!=null) {
            txSupportForTestingService.deleteFunctionById(f.id);
        }
    }

    @Test
    @WithUserDetails("data_rest")
    public void testRestPayload_asRest() throws Exception {

        assertNull(functionRepository.findByCode(FUNCTION_CODE));

        byte[] bytes = IOUtils.resourceToByteArray("/bin/functions/functions-param-as-file.zip");

        // https://stackoverflow.com/questions/28236310/upload-file-using-spring-mvc-and-mockmvc
        MockMultipartFile functionFile = new MockMultipartFile(
                "file", "functions.zip", MediaType.APPLICATION_OCTET_STREAM.getType(),
                bytes);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/rest/v1/dispatcher/function/function-upload-from-file")
                .file(functionFile)
                .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME)).andReturn();

        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(content.contains("infoMessagesAsList"));
        assertFalse(content.contains("errorMessagesAsList"));
        assertFalse(content.contains("errorMessagesAsStr"));

        OperationStatusRest rest = JsonUtils.getMapper().readValue(content, OperationStatusRest.class);
        assertNull(rest.getErrorMessages());
        assertEquals(EnumsApi.OperationStatus.OK, rest.status);

        final Function f = functionRepository.findByCode(FUNCTION_CODE);
        assertNotNull(f);


    }
}
