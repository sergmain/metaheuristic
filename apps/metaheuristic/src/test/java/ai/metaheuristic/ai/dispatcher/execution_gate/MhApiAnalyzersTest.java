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

package ai.metaheuristic.ai.dispatcher.execution_gate;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.utils.FunctionAnalyzerUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * mh-api-analyzers.yaml - the analyzer rules MH ships for every Function: the real resource as the Dispatcher
 * reads it at start, the validation it is held to, and where its rules stand among the other two sources.
 *
 * @author Sergio Lissner
 * Date: 10/26/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class MhApiAnalyzersTest {

    private static final String VALID = """
            version: 1
            analyzers:
              - name: r1
                regex:
                  - 'status 429'
                timeout: 20min
                incrementTries: false
                scope: api
              - name: r2
                regex:
                  - 'usage limit'
                timeout: 1h
                incrementTries: true
                scope: processor
            """;

    private static FunctionConfigYaml.Analyzer analyzer(String name, String regex) {
        return new FunctionConfigYaml.Analyzer(name, new ArrayList<>(List.of(regex)), "1h", false, EnumsApi.GateScope.function);
    }

    private static List<String> names(List<FunctionConfigYaml.Analyzer> analyzers) {
        return analyzers.stream().map(a -> a.name).toList();
    }

    private static List<FunctionConfigYaml.Analyzer> shipped() {
        return ExecutionGateUtils.readApiAnalyzers(ExecutionGateUtils.MH_API_ANALYZERS_YAML);
    }

    // ---------- the real resource ----------

    @Test
    public void test_shippedResource_readsSixRulesInDeclaredOrder() {
        assertEquals(List.of("api-quota-exhausted", "api-provider-unavailable", "api-credentials-rejected",
                        "cli-usage-limit-chatgpt", "cli-usage-limit-cc", "cli-auth-expired-cc"),
                names(shipped()));
    }

    @Test
    public void test_shippedResource_everyFieldOfARuleIsRead() {
        final FunctionConfigYaml.Analyzer quota = shipped().get(0);
        assertEquals(List.of("Anthropic API call failed with status 429", "\"type\":\"rate_limit_error\""), quota.regex);
        assertEquals("20min", quota.timeout);
        assertFalse(quota.incrementTries);
        assertEquals(EnumsApi.GateScope.api, quota.scope);

        final FunctionConfigYaml.Analyzer credentials = shipped().get(2);
        assertEquals("30min", credentials.timeout);
        assertTrue(credentials.incrementTries, "api-credentials-rejected spends a retry");

        final FunctionConfigYaml.Analyzer authExpired = shipped().get(5);
        assertEquals(List.of("OAuth session expired and could not be refreshed"), authExpired.regex);
        assertEquals("30min", authExpired.timeout);
        assertFalse(authExpired.incrementTries);
        assertEquals(EnumsApi.GateScope.processor, authExpired.scope);
    }

    @Test
    public void test_shippedResource_matchesTheApiExecutorsFailureLine() {
        final FunctionConfigYaml.Analyzer hit = FunctionAnalyzerUtils.firstHit(shipped(),
                "API response status: 429\n777.100 Anthropic API call failed with status 429; body={\"type\":\"error\"}");
        assertNotNull(hit);
        assertEquals("api-quota-exhausted", hit.name);
    }

    @Test
    public void test_shippedResource_matchesAnOverloadedBody() {
        final FunctionConfigYaml.Analyzer hit = FunctionAnalyzerUtils.firstHit(shipped(),
                "body={\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}");
        assertNotNull(hit);
        assertEquals("api-provider-unavailable", hit.name);
    }

    @Test
    public void test_shippedResource_matchesChatGptLimit_withTypographicApostrophe() {
        final FunctionConfigYaml.Analyzer hit = FunctionAnalyzerUtils.firstHit(shipped(),
                "You\u2019ve hit your usage limit. Try again at Jul 22nd, 2026 9:15 PM.");
        assertNotNull(hit);
        assertEquals("cli-usage-limit-chatgpt", hit.name);
    }

    @Test
    public void test_shippedResource_matchesClaudeCodeLimitAndExpiredLogin() {
        final FunctionConfigYaml.Analyzer limit = FunctionAnalyzerUtils.firstHit(shipped(), "Claude usage limit reached. Your limit will reset at 5pm");
        assertNotNull(limit);
        assertEquals("cli-usage-limit-cc", limit.name);

        final FunctionConfigYaml.Analyzer auth = FunctionAnalyzerUtils.firstHit(shipped(),
                "Failed to authenticate: OAuth session expired and could not be refreshed");
        assertNotNull(auth);
        assertEquals("cli-auth-expired-cc", auth.name);
    }

    @Test
    public void test_shippedResource_ordinaryFailureMatchesNothing() {
        assertNull(FunctionAnalyzerUtils.firstHit(shipped(), "java.lang.NullPointerException at Foo.bar(Foo.java:12)"));
    }

    @Test
    public void test_shippedResource_eachReadIsAPrivateImmutableCopy() {
        final List<FunctionConfigYaml.Analyzer> first = shipped();
        final List<FunctionConfigYaml.Analyzer> second = shipped();

        assertThrows(UnsupportedOperationException.class, () -> first.add(analyzer("x", "x")));
        first.get(0).name = "changed";
        assertEquals("api-quota-exhausted", second.get(0).name, "a caller's change must not reach another reader");
    }

    @Test
    public void test_missingResource_failsWithItsName() {
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ExecutionGateUtils.readApiAnalyzers("no-such-mh-api-analyzers.yaml"));
        assertTrue(e.getMessage().startsWith("01.322.140 "), e.getMessage());
        assertTrue(e.getMessage().contains("no-such-mh-api-analyzers.yaml"), e.getMessage());
    }

    // ---------- parsing and validation ----------

    @Test
    public void test_parse_valid() {
        final List<FunctionConfigYaml.Analyzer> actual = ExecutionGateUtils.parseApiAnalyzers(VALID);

        assertEquals(List.of("r1", "r2"), names(actual));
        final FunctionConfigYaml.Analyzer r2 = actual.get(1);
        assertEquals(List.of("usage limit"), r2.regex);
        assertEquals("1h", r2.timeout);
        assertTrue(r2.incrementTries);
        assertEquals(EnumsApi.GateScope.processor, r2.scope);
    }

    @Test
    public void test_parse_emptyAnalyzersKey_meansNoRules() {
        assertEquals(List.of(), ExecutionGateUtils.parseApiAnalyzers("version: 1\nanalyzers:\n"));
        assertEquals(List.of(), ExecutionGateUtils.parseApiAnalyzers("version: 1\n"));
    }

    @Test
    public void test_parse_operatorOnlyScope_rejected() {
        final CheckIntegrityFailedException e = assertThrows(CheckIntegrityFailedException.class,
                () -> ExecutionGateUtils.parseApiAnalyzers(VALID.replace("scope: api", "scope: company")));
        assertTrue(e.getMessage().startsWith("01.956.020 "), e.getMessage());
        assertTrue(e.getMessage().contains("r1"), e.getMessage());
    }

    @Test
    public void test_parse_globalScope_rejected() {
        assertThrows(CheckIntegrityFailedException.class,
                () -> ExecutionGateUtils.parseApiAnalyzers(VALID.replace("scope: processor", "scope: global")));
    }

    @Test
    public void test_parse_unreadableTimeout_rejected() {
        final CheckIntegrityFailedException e = assertThrows(CheckIntegrityFailedException.class,
                () -> ExecutionGateUtils.parseApiAnalyzers(VALID.replace("timeout: 1h", "timeout: an hour")));
        assertTrue(e.getMessage().contains("r2"), e.getMessage());
    }

    @Test
    public void test_parse_regexNotCompiling_rejected() {
        final CheckIntegrityFailedException e = assertThrows(CheckIntegrityFailedException.class,
                () -> ExecutionGateUtils.parseApiAnalyzers(VALID.replace("'status 429'", "'status (429'")));
        assertTrue(e.getMessage().contains("doesn't compile"), e.getMessage());
    }

    @Test
    public void test_parse_noRegex_rejected() {
        final String yaml = VALID.replace("""
                    regex:
                      - 'usage limit'
                """, "");
        final CheckIntegrityFailedException e = assertThrows(CheckIntegrityFailedException.class,
                () -> ExecutionGateUtils.parseApiAnalyzers(yaml));
        assertTrue(e.getMessage().contains("can never match"), e.getMessage());
    }

    @Test
    public void test_parse_everyBrokenRuleReported() {
        final String yaml = VALID.replace("scope: api", "scope: company").replace("timeout: 1h", "timeout: soon");
        final CheckIntegrityFailedException e = assertThrows(CheckIntegrityFailedException.class,
                () -> ExecutionGateUtils.parseApiAnalyzers(yaml));
        assertTrue(e.getMessage().contains("r1"), e.getMessage());
        assertTrue(e.getMessage().contains("r2"), e.getMessage());
    }

    @Test
    public void test_parse_blank_rejected() {
        assertThrows(Throwable.class, () -> ExecutionGateUtils.parseApiAnalyzers("   "));
    }

    // ---------- join ----------

    @Test
    public void test_join_operatorThenFunctionThenMh() {
        final List<FunctionConfigYaml.Analyzer> actual = ExecutionGateUtils.joinAnalyzers(
                List.of(analyzer("op1", "a"), analyzer("op2", "b")),
                List.of(analyzer("fn1", "c")),
                List.of(analyzer("mh1", "d"), analyzer("mh2", "e")));

        assertEquals(List.of("op1", "op2", "fn1", "mh1", "mh2"), names(actual));
    }

    @Test
    public void test_join_firstHitPrecedence_onTheSameConsole() {
        final String console = "quota exhausted";
        final List<FunctionConfigYaml.Analyzer> op = List.of(analyzer("op", "quota"));
        final List<FunctionConfigYaml.Analyzer> fn = List.of(analyzer("fn", "quota"));
        final List<FunctionConfigYaml.Analyzer> mh = List.of(analyzer("mh", "quota"));

        assertEquals("op", hitName(ExecutionGateUtils.joinAnalyzers(op, fn, mh), console));
        assertEquals("fn", hitName(ExecutionGateUtils.joinAnalyzers(List.of(), fn, mh), console));
        assertEquals("mh", hitName(ExecutionGateUtils.joinAnalyzers(List.of(), List.of(), mh), console));
    }

    @Test
    public void test_join_identicalRulesAreKept_aLabelIsNotAnIdentity() {
        final List<FunctionConfigYaml.Analyzer> actual = ExecutionGateUtils.joinAnalyzers(
                List.of(), List.of(analyzer("same", "x")), List.of(analyzer("same", "x")));
        assertEquals(2, actual.size());
    }

    @Test
    public void test_join_allEmpty_isEmpty() {
        assertEquals(List.of(), ExecutionGateUtils.joinAnalyzers(List.of(), List.of(), List.of()));
    }

    @Test
    public void test_join_resultIsImmutable() {
        final List<FunctionConfigYaml.Analyzer> actual = ExecutionGateUtils.joinAnalyzers(
                List.of(), List.of(), List.of(analyzer("mh", "x")));
        assertThrows(UnsupportedOperationException.class, () -> actual.add(analyzer("y", "y")));
    }

    private static String hitName(List<FunctionConfigYaml.Analyzer> analyzers, String console) {
        final FunctionConfigYaml.Analyzer hit = FunctionAnalyzerUtils.firstHit(analyzers, console);
        assertNotNull(hit);
        return hit.name;
    }
}
