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

package ai.metaheuristic.ai.sec;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.utils.HttpUtils;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;

import static org.hamcrest.core.Every.everyItem;
import static org.hamcrest.core.StringContains.containsStringIgnoringCase;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author Sergio Lissner
 * Date: 10/22/2023
 * Time: 1:11 PM
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@AutoConfigureMockMvc
@Import({SpringSecurityWebAuxTestConfig.class, MultiHttpSecurityConfigTest.JsonTestController .class})
@AutoConfigureCache
public class MultiHttpSecurityConfigTest {

    public static final String NEW_NAME_XML = "new-name.xml";
    public static final String SOME_TEXT_DATA = "some-text-data";
    //    @Autowired
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

    @RestController
    public static class JsonTestController {
        // This isn't the test, actual test is MultiHttpSecurityConfigTest.testApiEndpointWithCORSCustomHeader() below
        @GetMapping(value= "/rest/test/message", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
        public HttpEntity<String> downloadVariable() {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            HttpUtils.setContentDisposition(httpHeaders, NEW_NAME_XML);
            HttpEntity<String> entity = new ResponseEntity<>(SOME_TEXT_DATA, httpHeaders, HttpStatus.OK);
            return entity;
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
    public void testApiEndpointWithCORSCustomHeader() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.options("/rest/test/message")
                .header("Origin", "http://localhost:8080"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.header().exists(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
            .andExpect(MockMvcResultMatchers.header().stringValues(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, everyItem(containsStringIgnoringCase(HttpHeaders.CONTENT_DISPOSITION))))
            .andExpect(MockMvcResultMatchers.header().stringValues(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, everyItem(containsStringIgnoringCase(Consts.X_AUTH_TOKEN))));

    }
}
