/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import ai.metaheuristic.ai.spi.MhSpi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureMockMvc
@Import({SpringSecurityWebAuxTestConfig.class})
public class ExecContextRestControllerMockMvcTest {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @org.junit.jupiter.api.BeforeAll
    static void setSystemProperties() {
        MhSpi.cleanUpOnShutdown();
        System.setProperty("mh.home", tempDir.toAbsolutePath().toString());
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    // prod code now routes through ExecContextTopLevelService.execContextDescs,
    // which calls execContextCache.findById(id, true) per id — so mock the cache.
    @MockitoBean
    private ExecContextCache execContextCache;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        MhSpi.cleanUpOnShutdown();
        this.mockMvc = webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    private static ExecContextImpl buildExecContextWithDesc(String desc) {
        ExecContextImpl ec = new ExecContextImpl();
        ExecContextParamsYaml p = new ExecContextParamsYaml();
        p.sourceCodeUid = "test-src";
        p.desc = desc;
        ec.updateParams(p);
        return ec;
    }

    @Test
    @WithUserDetails("data_rest")
    public void test_execContextDescs() throws Exception {
        ExecContextImpl ec101 = buildExecContextWithDesc("first desc");
        ExecContextImpl ec102 = buildExecContextWithDesc("second desc");

        when(execContextCache.findById(eq(101L), anyBoolean())).thenReturn(ec101);
        when(execContextCache.findById(eq(102L), anyBoolean())).thenReturn(ec102);

        String body = "{\"ids\":[101,102]}";

        mockMvc.perform(post("/rest/v1/dispatcher/source-code/exec-context-descs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.items[0].id").value(101))
            .andExpect(jsonPath("$.items[0].desc").value("first desc"))
            .andExpect(jsonPath("$.items[1].id").value(102))
            .andExpect(jsonPath("$.items[1].desc").value("second desc"));
    }
}
