/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.signal_bus;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.sec.SpringSecurityWebAuxTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Plan 03 Part C Step C1 — REST controller.
 * data_rest test user resolves to a DispatcherContext with companyId=1.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@Import({SpringSecurityWebAuxTestConfig.class})
@ActiveProfiles({"dispatcher", "h2", "test"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class SignalBusRestControllerTest {

    @org.junit.jupiter.api.io.TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String dbUrl = "jdbc:h2:file:" + tempDir.resolve("db-h2/mh").toAbsolutePath()
            + ";DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.url", () -> dbUrl);
        registry.add("mh.home", () -> tempDir.toAbsolutePath().toString());
        registry.add("spring.profiles.active", () -> "dispatcher,h2,test");
    }

    @Autowired WebApplicationContext webApplicationContext;
    @Autowired SignalBus signalBus;

    private MockMvc mockMvc;

    // data_rest is seeded with companyId=1 by SpringSecurityWebAuxTestConfig
    private static final long DATA_REST_COMPANY_ID = 1L;

    @BeforeEach
    public void setup() {
        mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Test
    @WithUserDetails("data_rest")
    public void get_returnsEntriesNewerThanAfterRev() throws Exception {
        // arrange — publish directly into the bus for this company
        signalBus.put(SignalKind.BATCH, "rest-42",
            new ScopeRef(DATA_REST_COMPANY_ID), Map.of("state", 4), true);

        mockMvc.perform(get("/rest/v1/dispatcher/signals")
                .param("afterRev", "0")
                .param("kinds", "BATCH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serverRev").isNumber())
            .andExpect(jsonPath("$.signals[?(@.signalId == 'rest-42')].kind")
                .value("BATCH"))
            .andExpect(jsonPath("$.signals[?(@.signalId == 'rest-42')].info.state")
                .value(4))
            .andExpect(jsonPath("$.signals[?(@.signalId == 'rest-42')].terminal")
                .value(true));
    }

    @Test
    @WithUserDetails("data_rest")
    public void get_withAfterRevAtCurrent_returnsNoNewSignals() throws Exception {
        // arrange — publish, capture current serverRev, then poll with afterRev=serverRev
        signalBus.put(SignalKind.BATCH, "rest-43",
            new ScopeRef(DATA_REST_COMPANY_ID), Map.of("state", 4), true);
        long rev = signalBus.query(
            new ScopeRef(DATA_REST_COMPANY_ID), 0L,
            java.util.Set.of(SignalKind.BATCH), java.util.List.of()).serverRev();

        mockMvc.perform(get("/rest/v1/dispatcher/signals")
                .param("afterRev", String.valueOf(rev))
                .param("kinds", "BATCH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.signals[?(@.signalId == 'rest-43')]").isEmpty())
            .andExpect(jsonPath("$.serverRev").value(rev));
    }

    @Test
    @WithUserDetails("data_rest")
    public void get_scopeFiltersByCompanyId() throws Exception {
        // arrange — same signalId in two different companies
        signalBus.put(SignalKind.BATCH, "scoped-99",
            new ScopeRef(DATA_REST_COMPANY_ID), Map.of("state", 4), true);
        signalBus.put(SignalKind.BATCH, "other-99",
            new ScopeRef(9_999_999L), Map.of("state", 4), true);

        mockMvc.perform(get("/rest/v1/dispatcher/signals")
                .param("afterRev", "0")
                .param("kinds", "BATCH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.signals[?(@.signalId == 'scoped-99')]").exists())
            .andExpect(jsonPath("$.signals[?(@.signalId == 'other-99')]").doesNotExist());
    }

    @Test
    @WithUserDetails("data_rest")
    public void get_topicGlobFilter() throws Exception {
        // arrange — two EXEC_CONTEXT signals with different sourceCodeUid prefixes
        ScopeRef scope = new ScopeRef(DATA_REST_COMPANY_ID);
        signalBus.put(SignalKind.EXEC_CONTEXT, "ec-glob-1", scope,
            Map.of("infoBank", "DRONE", "sourceCodeUid", "mhdg-rg-flat-1.0.0"), false);
        signalBus.put(SignalKind.EXEC_CONTEXT, "ec-glob-2", scope,
            Map.of("infoBank", "DRONE", "sourceCodeUid", "cv-redundancy-1.0.0"), false);

        mockMvc.perform(get("/rest/v1/dispatcher/signals")
                .param("afterRev", "0")
                .param("kinds", "EXEC_CONTEXT")
                .param("topics", "execContext.DRONE.cv-*.state"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.signals[?(@.signalId == 'ec-glob-2')]").exists())
            .andExpect(jsonPath("$.signals[?(@.signalId == 'ec-glob-1')]").doesNotExist());
    }

    @Test
    @WithUserDetails("data_rest")
    public void get_unknownKindIgnored_returnsWarning() throws Exception {
        mockMvc.perform(get("/rest/v1/dispatcher/signals")
                .param("afterRev", "0")
                .param("kinds", "BATCH,NONSENSE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.infoMessages[0]").value(containsString("SIG.110")));
    }
}
