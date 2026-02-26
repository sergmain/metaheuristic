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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
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
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @AfterAll
    static void cleanupLogging() {
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

    @Test
    @WithUserDetails("data_rest")
    public void test_upload_big_file_2(@TempDir Path tempDir) throws Exception {
        System.out.println("serverPort: " + serverPort);
        for (int i = 0; i < SIZE_FOR_UPLOADING; i++) {
            bytes[i] = (byte) i;
        }
        Path tempFile = tempDir.resolve("temp-file.bin");
        try (OutputStream os = Files.newOutputStream(tempFile)) {
            os.write(bytes);
            os.flush();
        }
        final String uploadRestUrl  = "http://localhost:8080" + CommonConsts.REST_V1_URL + Consts.UPLOAD_REST_URL;
        String randonPart = "/123-1-1";
        final String uri = uploadRestUrl + randonPart;

        final MultipartEntityBuilder builder = MultipartEntityBuilder.create()
            .setMode(HttpMultipartMode.EXTENDED)
            .setCharset(StandardCharsets.UTF_8)
            .addTextBody("processorId", "1")
            .addTextBody("taskId", "1")
            .addTextBody("variableId", "1")
            .addTextBody("nullified", "false")
            .addBinaryBody("file", tempFile.toFile(), ContentType.APPLICATION_OCTET_STREAM, "filename");

        HttpEntity entity = builder.build();

        Request request = Request.post(uri)
            .connectTimeout(Timeout.ofSeconds(5))
            .responseTimeout(Timeout.ofSeconds(60))
            .body(entity);

        final Executor executor = HttpClientExecutor.getExecutor(uri, "data_rest", "123");

        Response response = executor.execute(request);
        String json = response.returnContent().asString(StandardCharsets.UTF_8);
        Enums.UploadVariableStatus status = JsonUtils.getMapper().readValue(json, Enums.UploadVariableStatus.class);

        assertEquals(OK, status);
    }
}
