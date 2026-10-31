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

package ai.metaheuristic.ai.dispatcher.rest.v1;

import ai.metaheuristic.ai.MhComplexTestConfig;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.Variable;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepositoryForTest;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.dispatcher.variable.VariableTxService;
import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * {@code POST /rest/v1/dispatcher/source-code/exec-context-with-variables-add-commit}, driven for real: the dispatcher
 * servlet, the security filter chain and {@code @PreAuthorize}, JSON binding, the ExecContext creator, the variable
 * store and Task production - on the shared V3 harness (no {@code @Import}, the harness's own profile set, so the
 * shared context is not forked).
 *
 * <p>The caller is the harness's real {@code Account} - the principal {@code UserContextService} reads the company
 * from - with the role the endpoint requires stated on the Authentication. No doubles, per RULE-NO-MOCKITO.md.
 *
 * <p>Fixture: {@code exec-context-input-variables-1.0.mhsc}, inputs {@code alpha} (not nullable) and {@code beta?}.
 */
@SpringBootTest(classes = MhComplexTestConfig.class)
@ActiveProfiles({"dispatcher", "h2", "test", "mh-test-lm"})
@Execution(ExecutionMode.SAME_THREAD)
@AutoConfigureCache
public class ExecContextWithVariablesRestControllerTest extends PreparingSourceCode {

    private static final String URL = "/rest/v1/dispatcher/source-code/exec-context-with-variables-add-commit";

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ExecContextCache execContextCacheLocal;
    @Autowired private VariableTxService variableTxServiceLocal;
    @Autowired private TxSupportForTestingService txSupportForTestingServiceLocal;
    @Autowired private TaskRepositoryForTest taskRepositoryForTestLocal;

    private MockMvc mockMvc;

    @Override
    public SourceCodeUriAndLang getSourceCodeAndLang() {
        return new SourceCodeUriAndLang(
                "/source_code/mhsc/exec-context-input-variables-1.0.mhsc", EnumsApi.SourceCodeLang.mhsc, null);
    }

    @BeforeEach
    public void setupMockMvc() {
        mockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
    }

    @Test
    public void test_valuesLandAsTheBodySaysAndTasksAreProduced() throws Exception {
        final String body = "{\"sourceCodeId\": " + getSourceCode().id + ", \"variables\": {\"alpha\": \"alpha-value\", \"beta\": null}}";
        final String answer = postAs("ROLE_DATA", body, 200);

        assertFalse(answer.contains("errorMessages"), "PHASE #1: creation must succeed, answer: " + answer);
        final long execContextId = ((Number) JsonPath.read(answer, "$.execContextId")).longValue();
        assertEquals(getSourceCode().id.longValue(), ((Number) JsonPath.read(answer, "$.sourceCodeId")).longValue(),
                "PHASE #1: the answer names the SourceCode it instantiated");
        final ExecContextImpl ec = execContextCacheLocal.findById(execContextId, true);
        assertNotNull(ec, "PHASE #1: the ExecContext the answer names must exist");
        setExecContextForTest(ec);
        assertEquals(getCompany().getUniqueId(), ec.companyId, "PHASE #1: created in the caller's company");

        assertEquals("alpha-value", contentOf("alpha", execContextId), "PHASE #2: alpha must hold the body's text");
        final Variable beta = txSupportForTestingServiceLocal.findVariableInAllInternalContexts(
                "beta", CommonConsts.TOP_LEVEL_CONTEXT_ID, execContextId);
        assertNotNull(beta, "PHASE #2: beta must exist in the top-level context");
        assertTrue(beta.inited, "PHASE #2: beta must be initialized");
        assertTrue(beta.nullified, "PHASE #2: a JSON null must create beta NULLIFIED, not as the text of a null");

        assertFalse(taskRepositoryForTestLocal.findByExecContextIdAsList(execContextId).isEmpty(),
                "PHASE #3: Tasks must have been produced");
    }

    @Test
    public void test_aJsonNumberArrivesAsItsText() throws Exception {
        final String body = "{\"sourceCodeId\": " + getSourceCode().id + ", \"variables\": {\"alpha\": 42, \"beta\": \"b\"}}";
        final String answer = postAs("ROLE_DATA", body, 200);

        assertFalse(answer.contains("errorMessages"), "PHASE #1: creation must succeed, answer: " + answer);
        final long execContextId = ((Number) JsonPath.read(answer, "$.execContextId")).longValue();
        setExecContextForTest(execContextCacheLocal.findById(execContextId, true));
        assertEquals("42", contentOf("alpha", execContextId), "PHASE #2: a JSON number is stored as its text");
        assertEquals("b", contentOf("beta", execContextId), "PHASE #2: beta must hold the body's text");
    }

    @Test
    public void test_missingValueIsRefusedNamingItAndNothingIsCreated() throws Exception {
        final String body = "{\"sourceCodeId\": " + getSourceCode().id + ", \"variables\": {\"alpha\": \"alpha-value\"}}";
        final String answer = postAs("ROLE_DATA", body, 200);

        assertTrue(answer.contains("\"errorMessages\""), "PHASE #1: the answer must carry the refusal, was: " + answer);
        final List<String> errors = JsonPath.read(answer, "$.errorMessages");
        final String all = String.join(" | ", errors);
        assertTrue(all.contains("562.120"), "PHASE #1: expected 562.120, was: " + all);
        assertTrue(all.contains("beta"), "PHASE #1: the refusal must name the input with no value, was: " + all);
        assertNull(JsonPath.read(answer, "$.execContextId"), "PHASE #2: no ExecContext is named when nothing was created: " + answer);
    }

    @Test
    public void test_noSourceCodeIdIsRefused() throws Exception {
        final String answer = postAs("ROLE_DATA", "{\"variables\": {\"alpha\": \"alpha-value\"}}", 200);

        assertTrue(answer.contains("\"errorMessages\""), "PHASE #1: the answer must carry the refusal, was: " + answer);
        final List<String> errors = JsonPath.read(answer, "$.errorMessages");
        assertEquals(1, errors.size(), "PHASE #1: one refusal, was: " + errors);
        assertTrue(errors.getFirst().startsWith("01.571.020"), "PHASE #1: expected 01.571.020, was: " + errors);
    }

    @Test
    public void test_aRoleWithoutAdminOrDataIsForbidden() throws Exception {
        final String body = "{\"sourceCodeId\": " + getSourceCode().id + ", \"variables\": {\"alpha\": \"alpha-value\", \"beta\": null}}";
        postAs("ROLE_OPERATOR", body, 403);
    }

    private String postAs(String role, String body, int expectedStatus) throws Exception {
        final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                getAccount(), null, List.of(new SimpleGrantedAuthority(role)));
        final MvcResult result = mockMvc.perform(post(URL)
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String contentOf(String name, Long execContextId) {
        final Variable v = txSupportForTestingServiceLocal.findVariableInAllInternalContexts(
                name, CommonConsts.TOP_LEVEL_CONTEXT_ID, execContextId);
        assertNotNull(v, "variable '" + name + "' must exist in the top-level context");
        return variableTxServiceLocal.getVariableDataAsString(v.id);
    }
}
