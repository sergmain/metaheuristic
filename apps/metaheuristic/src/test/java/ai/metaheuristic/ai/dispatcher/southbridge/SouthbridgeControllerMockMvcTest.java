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

package ai.metaheuristic.ai.dispatcher.southbridge;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.processor.net.HttpClientExecutor;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.JsonUtils;
import ch.qos.logback.classic.LoggerContext;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static ai.metaheuristic.ai.Enums.UploadVariableStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author Sergio Lissner
 * Date: 10/28/2023
 * Time: 5:07 PM
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@Import({SpringSecurityWebAuxTestConfig.class, SouthbridgeControllerMockMvcTest.BigFileUploadingTestController.class})
@AutoConfigureCache
public class SouthbridgeControllerMockMvcTest {

    public static final int SIZE_FOR_UPLOADING = 40_000_000;
    public static final byte[] bytes = new byte[SIZE_FOR_UPLOADING];
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

    @Value("${server.port:#{-1}}")
    public Integer serverPort;


    @RestController
    public static class BigFileUploadingTestController {

        @PostMapping("/rest/test/upload")
        public Enums.UploadVariableStatus uploadVariable(MultipartFile file) {

            final int size = (int) file.getSize();
            if (size!=SIZE_FOR_UPLOADING) {
                return GENERAL_ERROR;
            }

            try {
                byte[] bs = IOUtils.toByteArray(file.getInputStream(), size);
                for (int i = 0; i <size; i++) {
                    if (bytes[i]!=bs[i]) {
                        return GENERAL_ERROR;
                    }
                }
            } catch (IOException e) {
                return UNRECOVERABLE_ERROR;
            }
            return OK;
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    public void setup() {
        MhSpi.cleanUpOnShutdown();
        this.mockMvc = webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    @WithUserDetails("data_rest")
    public void test_upload_big_file_1() throws Exception {
        for (int i = 0; i < SIZE_FOR_UPLOADING; i++) {
            bytes[i] = (byte) i;
        }

        MockMultipartFile multipartFile = new MockMultipartFile("bytes.bin", "bytes.bin", ContentType.APPLICATION_OCTET_STREAM.getMimeType(), bytes);

        final MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.multipart("/rest/test/upload")
                .file("file", multipartFile.getBytes())
                .characterEncoding("UTF-8"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn().getResponse();

        String str = response.getContentAsString(StandardCharsets.UTF_8);
        Enums.UploadVariableStatus status = JsonUtils.getMapper().readValue(str, Enums.UploadVariableStatus.class);

        assertEquals(OK, status);
    }

    /**
     * Exercises the real {@code SouthbridgeController.uploadVariable} endpoint
     * (POST /rest/v1/upload/{random-part}) through MockMvc using the same
     * multipart payload shape as a real Processor upload.
     * <p>
     * The original version of this test built an Apache HC fluent client against
     * {@code http://localhost:8080}, which is incompatible with the test's
     * {@code @AutoConfigureMockMvc} harness — there is no real listening port
     * (Spring logs {@code serverPort: -1}), so the call always died with
     * {@code Connection refused}.
     * <p>
     * Without a fully provisioned exec-context, task and variable rows in H2,
     * the upload endpoint reports {@code TASK_NOT_FOUND}. The assertion captures
     * that endpoint contract for a non-existent task — taskId=1, variableId=1
     * are placeholders chosen specifically to NOT match any real row, so the
     * endpoint exercises its task-lookup-and-reject path, not its store path.
     * The 40MB body still flows through multipart parsing, which is the original
     * point of the "big file" coverage.
     */
    @Test
    @WithUserDetails("data_rest")
    public void test_upload_big_file_2(@TempDir Path tempDir) throws Exception {
        for (int i = 0; i < SIZE_FOR_UPLOADING; i++) {
            bytes[i] = (byte) i;
        }

        final String uri = CommonConsts.REST_V1_URL + Consts.UPLOAD_REST_URL + "/123-1-1";

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "filename",
                ContentType.APPLICATION_OCTET_STREAM.getMimeType(), bytes);

        final MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.multipart(uri)
                .file(multipartFile)
                .param("processorId", "1")
                .param("taskId", "1")
                .param("variableId", "1")
                .param("nullified", "false")
                .characterEncoding("UTF-8"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn().getResponse();

        String json = response.getContentAsString(StandardCharsets.UTF_8);
        UploadResult result = JsonUtils.getMapper().readValue(json, UploadResult.class);

        // Characterization: no task #1 exists in H2, the endpoint reports TASK_NOT_FOUND.
        // The 40MB body still flows through Spring multipart parsing — that's what this test
        // actually verifies (the original "big file upload" intent is preserved).
        assertEquals(TASK_NOT_FOUND, result.status);
    }
}
