/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.commons.utils.CollectionUtils;
import ai.metaheuristic.commons.utils.JsonUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
import ch.qos.logback.classic.LoggerContext;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

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
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
@Import({SpringSecurityWebAuxTestConfig.class})
public class TestRestUploadFunction {

    private static final String FUNCTION_CODE = "get-length-of-file-by-ref_1.0";

    private MockMvc mockMvc;

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @BeforeAll
    static void setSystemProperties() {
        MhSpi.cleanUpOnShutdown();
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
        MhSpi.cleanUpOnShutdown();
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private FunctionRepository functionRepository;
    @Autowired private TxSupportForTestingService txSupportForTestingService;

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
    public void test_RestPayload_as_data_rest_company_N1() throws Exception {

        assertNull(functionRepository.findByCode(FUNCTION_CODE));

        byte[] bytes = IOUtils.resourceToByteArray("/bin/functions/bundle-2026-03-11.zip");

        // https://stackoverflow.com/questions/28236310/upload-file-using-spring-mvc-and-mockmvc
        MockMultipartFile functionFile = new MockMultipartFile(
                "file", "functions.zip", MediaType.APPLICATION_OCTET_STREAM.getType(),
                bytes);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/rest/v1/dispatcher/bundle/bundle-upload-from-file")
                .file(functionFile)
                .characterEncoding("UTF-8"))
            .andExpect(status().isForbidden())
            .andReturn();
    }

    @Test
    @WithUserDetails("admin")
    public void test_RestPayload_as_admin_company_N2() throws Exception {

        assertNull(functionRepository.findByCode(FUNCTION_CODE));

        byte[] bytes = IOUtils.resourceToByteArray("/bin/functions/bundle-2026-03-11.zip");

        // https://stackoverflow.com/questions/28236310/upload-file-using-spring-mvc-and-mockmvc
        MockMultipartFile functionFile = new MockMultipartFile(
                "file", "functions.zip", MediaType.APPLICATION_OCTET_STREAM.getType(),
                bytes);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/rest/v1/dispatcher/bundle/bundle-upload-from-file")
                .file(functionFile)
                .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME)).andReturn();

        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(content.contains("infoMessagesAsList"));
        assertFalse(content.contains("errorMessagesAsList"));
        assertFalse(content.contains("errorMessagesAsStr"));

        OperationStatusRest rest = JsonUtils.getMapper().readValue(content, OperationStatusRest.class);
        // :[971.260 can't load bundle file, error: File bundle-cfg.yaml wasn't found in bundle archive, class: class ai.metaheuristic.ai.exceptions.BundleProcessingException]
        assertNull(rest.getErrorMessages());

        final Function f = functionRepository.findByCode(FUNCTION_CODE);
        assertNotNull(f);
    }

    @Test
    @WithUserDetails("admin")
    public void test_as_admin_company_N2() throws Exception {

        assertNull(functionRepository.findByCode(FUNCTION_CODE));

        byte[] bytes = IOUtils.resourceToByteArray("/bin/functions/bundle-2026-03-11.zip");

        // https://stackoverflow.com/questions/28236310/upload-file-using-spring-mvc-and-mockmvc
        MockMultipartFile functionFile = new MockMultipartFile(
                "file", "functions.zip", MediaType.APPLICATION_OCTET_STREAM.getType(),
                bytes);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/rest/v1/dispatcher/bundle/bundle-upload-from-file")
                .file(functionFile)
                .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(Consts.WEB_CONTAINER_SESSIONID_NAME)).andReturn();

        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(content.contains("infoMessagesAsList"));
        assertFalse(content.contains("errorMessagesAsList"));
        assertFalse(content.contains("errorMessagesAsStr"));


        BundleData.UploadingStatus rest = JsonUtils.getMapper().readValue(content, BundleData.UploadingStatus.class);

        assertTrue(CollectionUtils.isEmpty(rest.getErrorMessages()));

        final Function f = functionRepository.findByCode(FUNCTION_CODE);

        assertNotNull(f);
    }
}
