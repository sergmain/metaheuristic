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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import org.jspecify.annotations.Nullable;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Execution(CONCURRENT)
public class FunctionAnalyzerUtilsTest {

    // ---- parseTimeout, one per unit ----------------------------------------------------------

    @Test
    public void test_parseTimeout_millis() {
        assertEquals(Duration.ofMillis(500), FunctionAnalyzerUtils.parseTimeout("500ms"));
    }

    @Test
    public void test_parseTimeout_seconds() {
        assertEquals(Duration.ofSeconds(30), FunctionAnalyzerUtils.parseTimeout("30s"));
    }

    @Test
    public void test_parseTimeout_minutes() {
        assertEquals(Duration.ofMinutes(20), FunctionAnalyzerUtils.parseTimeout("20min"));
        assertEquals(Duration.ofMinutes(2), FunctionAnalyzerUtils.parseTimeout("2min"));
    }

    @Test
    public void test_parseTimeout_hours() {
        assertEquals(Duration.ofHours(1), FunctionAnalyzerUtils.parseTimeout("1h"));
    }

    @Test
    public void test_parseTimeout_days() {
        assertEquals(Duration.ofDays(1), FunctionAnalyzerUtils.parseTimeout("1d"));
    }

    @Test
    public void test_parseTimeout_minIsNotReadAsMilliseconds() {
        // 'ms', 's' and 'min' all overlap as suffixes, so this is the case a naive endsWith gets wrong
        assertNotEquals(FunctionAnalyzerUtils.parseTimeout("20min"), FunctionAnalyzerUtils.parseTimeout("20ms"));
        assertEquals(Duration.ofMinutes(20), FunctionAnalyzerUtils.parseTimeout("20min"));
        assertEquals(Duration.ofMillis(20), FunctionAnalyzerUtils.parseTimeout("20ms"));
    }

    @Test
    public void test_parseTimeout_surroundingWhitespaceIsTolerated() {
        assertEquals(Duration.ofMinutes(20), FunctionAnalyzerUtils.parseTimeout("  20min  "));
    }

    @Test
    public void test_parseTimeout_rejectsWhatItCannotRead() {
        // throwing beats defaulting: a typo must not silently withhold work for some other length of time
        assertThrows(IllegalStateException.class, () -> FunctionAnalyzerUtils.parseTimeout(""));
        assertThrows(IllegalStateException.class, () -> FunctionAnalyzerUtils.parseTimeout("20"));
        assertThrows(IllegalStateException.class, () -> FunctionAnalyzerUtils.parseTimeout("min"));
        assertThrows(IllegalStateException.class, () -> FunctionAnalyzerUtils.parseTimeout("20m"));
        assertThrows(IllegalStateException.class, () -> FunctionAnalyzerUtils.parseTimeout("-5s"));
        assertThrows(IllegalStateException.class, () -> FunctionAnalyzerUtils.parseTimeout("20 min"));
    }

    // ---- firstHit ----------------------------------------------------------------------------

    private static final String SAMPLE_CONSOLE = """
            starting run
            uploading context, 12841 tokens
            error: rate limit reached for this API key; please retry after 60 seconds
            exiting with code 1
            """;

    @Test
    public void test_firstHit_matchesSomewhereInsideTheOutput() {
        final FunctionConfigYaml.Analyzer a = analyzer("downtime", EnumsApi.GateScope.api, "20min", "rate limit reached");

        assertSame(a, FunctionAnalyzerUtils.firstHit(List.of(a), SAMPLE_CONSOLE));
    }

    @Test
    public void test_firstHit_missReturnsNull() {
        final FunctionConfigYaml.Analyzer a = analyzer("downtime", EnumsApi.GateScope.api, "20min", "no space left on device");

        assertNull(FunctionAnalyzerUtils.firstHit(List.of(a), SAMPLE_CONSOLE));
    }

    @Test
    public void test_firstHit_isCaseSensitiveUnlessTheAuthorAsksOtherwise() {
        assertNull(FunctionAnalyzerUtils.firstHit(List.of(analyzer("d", EnumsApi.GateScope.api, "1h", "RATE LIMIT REACHED")), SAMPLE_CONSOLE),
                "no implicit flags - a hidden CASE_INSENSITIVE would silently widen every pattern already written");
        assertNotNull(FunctionAnalyzerUtils.firstHit(List.of(analyzer("d", EnumsApi.GateScope.api, "1h", "(?i)RATE LIMIT REACHED")), SAMPLE_CONSOLE));
    }

    @Test
    public void test_firstHit_anyOneOfTheDeclaredPatternsIsEnough() {
        final FunctionConfigYaml.Analyzer a = analyzer("downtime", EnumsApi.GateScope.api, "20min", "never appears", "rate limit reached");

        assertSame(a, FunctionAnalyzerUtils.firstHit(List.of(a), SAMPLE_CONSOLE));
    }

    @Test
    public void test_firstHit_returnsTheFirstDeclaredAnalyzerThatMatches() {
        final FunctionConfigYaml.Analyzer first = analyzer("first", EnumsApi.GateScope.api, "20min", "error:");
        final FunctionConfigYaml.Analyzer second = analyzer("second", EnumsApi.GateScope.api, "1h", "rate limit reached");

        assertSame(first, FunctionAnalyzerUtils.firstHit(List.of(first, second), SAMPLE_CONSOLE));
    }

    @Test
    public void test_firstHit_toleratesNothingToLookAt() {
        final FunctionConfigYaml.Analyzer a = analyzer("downtime", EnumsApi.GateScope.api, "20min", "rate limit reached");

        assertNull(FunctionAnalyzerUtils.firstHit(null, SAMPLE_CONSOLE));
        assertNull(FunctionAnalyzerUtils.firstHit(List.of(), SAMPLE_CONSOLE));
        assertNull(FunctionAnalyzerUtils.firstHit(List.of(a), null));
        assertNull(FunctionAnalyzerUtils.firstHit(List.of(a), ""));
    }

    @Test
    public void test_firstHit_skipsAnUncompilableRegexRatherThanThrowing() {
        // this runs while handling a failure that already happened; one bad rule must not stop the rest
        final FunctionConfigYaml.Analyzer broken = analyzer("broken", EnumsApi.GateScope.api, "20min", "[unclosed");
        final FunctionConfigYaml.Analyzer good = analyzer("good", EnumsApi.GateScope.api, "20min", "rate limit reached");

        assertSame(good, FunctionAnalyzerUtils.firstHit(List.of(broken, good), SAMPLE_CONSOLE));
    }

    // ---- consolesInOrder / firstHitInExecResults ----------------------------------------------

    @Test
    public void test_consolesInOrder_execThenGeneralThenPreThenPost() {
        final FunctionApiData.FunctionExec fe = new FunctionApiData.FunctionExec();
        fe.exec = result("main output");
        fe.generalExec = result("general output");
        fe.preExecs = List.of(result("pre output"));
        fe.postExecs = List.of(result("post output"));

        assertEquals(List.of("main output", "general output", "pre output", "post output"),
                FunctionAnalyzerUtils.consolesInOrder(fe));
    }

    @Test
    public void test_consolesInOrder_skipsWhatIsAbsentOrBlank() {
        final FunctionApiData.FunctionExec fe = new FunctionApiData.FunctionExec();
        fe.exec = result("");
        fe.generalExec = null;
        fe.preExecs = null;
        fe.postExecs = List.of(result("post output"));

        assertEquals(List.of("post output"), FunctionAnalyzerUtils.consolesInOrder(fe));
    }

    @Test
    public void test_consolesInOrder_toleratesNoExecResultsAtAll() {
        assertTrue(FunctionAnalyzerUtils.consolesInOrder(null).isEmpty());
        assertTrue(FunctionAnalyzerUtils.consolesInOrder(new FunctionApiData.FunctionExec(null, null, null, null)).isEmpty());
    }

    @Test
    public void test_firstHitInExecResults_findsAMatchInAPreFunctionConsole() {
        final FunctionApiData.FunctionExec fe = new FunctionApiData.FunctionExec();
        fe.exec = result("nothing interesting");
        fe.preExecs = List.of(result("error: rate limit reached"));

        final FunctionConfigYaml.Analyzer a = analyzer("downtime", EnumsApi.GateScope.api, "20min", "rate limit reached");
        assertSame(a, FunctionAnalyzerUtils.firstHitInExecResults(List.of(a), fe));
    }

    @Test
    public void test_firstHitInExecResults_ordersByConsoleNotByAnalyzer() {
        // both analyzers match SOMETHING; the one matching the earlier console wins, so the outcome
        // doesn't depend on which failing step happens to be looked at first
        final FunctionApiData.FunctionExec fe = new FunctionApiData.FunctionExec();
        fe.exec = result("main: disk full");
        fe.postExecs = List.of(result("post: rate limit reached"));

        final FunctionConfigYaml.Analyzer rateLimit = analyzer("downtime", EnumsApi.GateScope.api, "20min", "rate limit reached");
        final FunctionConfigYaml.Analyzer diskFull = analyzer("host-broken", EnumsApi.GateScope.processor, "1h", "disk full");

        assertSame(diskFull, FunctionAnalyzerUtils.firstHitInExecResults(List.of(rateLimit, diskFull), fe));
    }

    @Test
    public void test_firstHitInExecResults_noMatchReturnsNull() {
        final FunctionApiData.FunctionExec fe = new FunctionApiData.FunctionExec();
        fe.exec = result("all good");

        assertNull(FunctionAnalyzerUtils.firstHitInExecResults(List.of(analyzer("d", EnumsApi.GateScope.api, "1h", "rate limit")), fe));
        assertNull(FunctionAnalyzerUtils.firstHitInExecResults(null, fe));
    }

    // ---- the "Vault has no entry" rule -------------------------------------------------------
    //
    // A Dispatcher restart LOCKS every company's Vault (the master passphrase is never persisted), and a
    // Processor fetching a sealed secret against a locked Vault gets the same 410/GONE as a genuinely absent
    // key - reported as "01.812.040 / 01.812.041 Vault has no entry ...". Retrying does not help either case: a
    // locked Vault clears only when a human unlocks it, and a missing key clears only when it is added. So the
    // rule that matches this console gives a FREE retry (incrementTries=false) and, at api scope, withholds
    // exactly the Functions needing that (companyId, keyCode) until the block lifts. This is the console the
    // rule must match, taken verbatim from DownloadSealedSecretService (01.812.040 and its HttpResponseException
    // twin 01.812.041 - the codes migrated to the 01.812.NNN scheme; the (01\.)? in the regex keeps a legacy
    // 812.04x matching too).

    /** The rule as application.properties ships it: api scope, free retry, matching either Vault-missing code. */
    private static FunctionConfigYaml.Analyzer vaultMissingAnalyzer() {
        return analyzer("vault locked or key missing", EnumsApi.GateScope.api, "2min", "(01\\.)?812\\.04[01] Vault has no entry");
    }

    @Test
    public void test_vaultMissingRule_matchesBothEmittedCodes() {
        final FunctionConfigYaml.Analyzer a = vaultMissingAnalyzer();

        assertSame(a, FunctionAnalyzerUtils.firstHit(List.of(a),
                "01.812.040 Vault has no entry for companyId=2, keyCode=RG_API_AUTH. Task #429 is finished with error."));
        assertSame(a, FunctionAnalyzerUtils.firstHit(List.of(a),
                "01.812.041 Vault has no entry for companyId=2, keyCode=RG_API_AUTH. Task #430 is finished with error."));
    }

    @Test
    public void test_vaultMissingRule_isAFreeRetryAtApiScope() {
        final FunctionConfigYaml.Analyzer a = vaultMissingAnalyzer();

        // a locked Vault is never the Task's fault, so its retry must not be spent
        assertFalse(a.incrementTries, "a locked Vault or a missing key is never the Task's fault");
        // api scope so the block covers everything needing that credential, not one Function and not the whole Processor
        assertEquals(EnumsApi.GateScope.api, a.scope);
        // short, because unlike a timed CC limit a locked Vault clears only on a human unlock: re-probe soon after
        assertEquals(Duration.ofMinutes(2), FunctionAnalyzerUtils.parseTimeout(a.timeout));
    }

    @Test
    public void test_vaultMissingRule_doesNotFireOnAnUnrelatedVaultLogLine() {
        // an ordinary informational line that merely says "Vault" must not withhold work
        assertNull(FunctionAnalyzerUtils.firstHit(List.of(vaultMissingAnalyzer()),
                "01.812.020 Unauthorized fetching sealed secret for companyId=2, keyCode=RG_API_AUTH"));
        assertNull(FunctionAnalyzerUtils.firstHit(List.of(vaultMissingAnalyzer()),
                "01.812.050 BAD_GATEWAY fetching sealed secret for companyId=2, keyCode=RG_API_AUTH; retry next cycle"));
    }

    private static FunctionApiData.SystemExecResult result(String console) {
        return new FunctionApiData.SystemExecResult("fn:1.0", false, 1, console);
    }

    // ---- checkScopeAllowedInDescriptor -------------------------------------------------------

    @Test
    public void test_checkScope_acceptsWhatAFunctionMayDeclare() {
        // processor IS declarable: in this security model the owner of the installation is the owner of
        // the Functions, so a descriptor setting processor scope is setting policy over its own fleet
        FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("a", EnumsApi.GateScope.api, "20min", "x"));
        FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("b", EnumsApi.GateScope.function, "20min", "x"));
        FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("c", EnumsApi.GateScope.processor, "20min", "x"));
    }

    @Test
    public void test_checkScope_rejectsDispatcherOnlyScopes() {
        assertThrows(IllegalStateException.class,
                () -> FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("a", EnumsApi.GateScope.global, "20min", "x")));
        assertThrows(IllegalStateException.class,
                () -> FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("b", EnumsApi.GateScope.company, "20min", "x")));
    }

    @Test
    public void test_checkScope_rejectsAMissingScope() {
        // an UNKNOWN scope needs no test any more: the field is an enum, so 'core' or a typo cannot be
        // constructed at all. Only the absent case is still reachable.
        assertThrows(IllegalStateException.class,
                () -> FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("b", null, "20min", "x")));
    }

    private static FunctionConfigYaml.Analyzer analyzer(String name, EnumsApi.@Nullable GateScope scope, String timeout, String... regex) {
        return new FunctionConfigYaml.Analyzer(name, new ArrayList<>(List.of(regex)), timeout, false, scope);
    }
}
