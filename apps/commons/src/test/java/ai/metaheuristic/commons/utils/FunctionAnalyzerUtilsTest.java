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
        final FunctionConfigYaml.Analyzer a = analyzer("downtime", "api", "20min", "rate limit reached");

        assertSame(a, FunctionAnalyzerUtils.firstHit(List.of(a), SAMPLE_CONSOLE));
    }

    @Test
    public void test_firstHit_missReturnsNull() {
        final FunctionConfigYaml.Analyzer a = analyzer("downtime", "api", "20min", "no space left on device");

        assertNull(FunctionAnalyzerUtils.firstHit(List.of(a), SAMPLE_CONSOLE));
    }

    @Test
    public void test_firstHit_isCaseSensitiveUnlessTheAuthorAsksOtherwise() {
        assertNull(FunctionAnalyzerUtils.firstHit(List.of(analyzer("d", "api", "1h", "RATE LIMIT REACHED")), SAMPLE_CONSOLE),
                "no implicit flags - a hidden CASE_INSENSITIVE would silently widen every pattern already written");
        assertNotNull(FunctionAnalyzerUtils.firstHit(List.of(analyzer("d", "api", "1h", "(?i)RATE LIMIT REACHED")), SAMPLE_CONSOLE));
    }

    @Test
    public void test_firstHit_anyOneOfTheDeclaredPatternsIsEnough() {
        final FunctionConfigYaml.Analyzer a = analyzer("downtime", "api", "20min", "never appears", "rate limit reached");

        assertSame(a, FunctionAnalyzerUtils.firstHit(List.of(a), SAMPLE_CONSOLE));
    }

    @Test
    public void test_firstHit_returnsTheFirstDeclaredAnalyzerThatMatches() {
        final FunctionConfigYaml.Analyzer first = analyzer("first", "api", "20min", "error:");
        final FunctionConfigYaml.Analyzer second = analyzer("second", "api", "1h", "rate limit reached");

        assertSame(first, FunctionAnalyzerUtils.firstHit(List.of(first, second), SAMPLE_CONSOLE));
    }

    @Test
    public void test_firstHit_toleratesNothingToLookAt() {
        final FunctionConfigYaml.Analyzer a = analyzer("downtime", "api", "20min", "rate limit reached");

        assertNull(FunctionAnalyzerUtils.firstHit(null, SAMPLE_CONSOLE));
        assertNull(FunctionAnalyzerUtils.firstHit(List.of(), SAMPLE_CONSOLE));
        assertNull(FunctionAnalyzerUtils.firstHit(List.of(a), null));
        assertNull(FunctionAnalyzerUtils.firstHit(List.of(a), ""));
    }

    @Test
    public void test_firstHit_skipsAnUncompilableRegexRatherThanThrowing() {
        // this runs while handling a failure that already happened; one bad rule must not stop the rest
        final FunctionConfigYaml.Analyzer broken = analyzer("broken", "api", "20min", "[unclosed");
        final FunctionConfigYaml.Analyzer good = analyzer("good", "api", "20min", "rate limit reached");

        assertSame(good, FunctionAnalyzerUtils.firstHit(List.of(broken, good), SAMPLE_CONSOLE));
    }

    // ---- merge -------------------------------------------------------------------------------

    @Test
    public void test_merge_bothSitesApply() {
        final List<FunctionConfigYaml.Analyzer> merged = FunctionAnalyzerUtils.merge(
                List.of(analyzer("from-dispatcher", "api", "1h", "a")),
                List.of(analyzer("from-function", "function", "30s", "b")));

        assertEquals(2, merged.size());
        assertEquals(List.of("from-dispatcher", "from-function"), merged.stream().map(a -> a.name).toList());
    }

    @Test
    public void test_merge_sameNameKeepsTheLongerTimeout_functionSideLonger() {
        final FunctionConfigYaml.Analyzer longer = analyzer("downtime", "api", "1h", "b");

        final List<FunctionConfigYaml.Analyzer> merged = FunctionAnalyzerUtils.merge(
                List.of(analyzer("downtime", "api", "30s", "a")), List.of(longer));

        assertEquals(1, merged.size());
        assertSame(longer, merged.get(0));
    }

    @Test
    public void test_merge_sameNameKeepsTheLongerTimeout_dispatcherSideLonger() {
        // the direction that matters: a Function author must not be able to shorten an installation's limit
        final FunctionConfigYaml.Analyzer longer = analyzer("downtime", "api", "1d", "a");

        final List<FunctionConfigYaml.Analyzer> merged = FunctionAnalyzerUtils.merge(
                List.of(longer), List.of(analyzer("downtime", "api", "30s", "b")));

        assertEquals(1, merged.size());
        assertSame(longer, merged.get(0));
    }

    @Test
    public void test_merge_toleratesEitherSideBeingEmpty() {
        assertEquals(1, FunctionAnalyzerUtils.merge(List.of(), List.of(analyzer("x", "api", "1h", "a"))).size());
        assertEquals(1, FunctionAnalyzerUtils.merge(List.of(analyzer("x", "api", "1h", "a")), List.of()).size());
        assertTrue(FunctionAnalyzerUtils.merge(List.of(), List.of()).isEmpty());
    }

    // ---- checkScopeAllowedInDescriptor -------------------------------------------------------

    @Test
    public void test_checkScope_acceptsWhatAFunctionMayDeclare() {
        // processor IS declarable: in this security model the owner of the installation is the owner of
        // the Functions, so a descriptor setting processor scope is setting policy over its own fleet
        FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("a", "api", "20min", "x"));
        FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("b", "function", "20min", "x"));
        FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("c", "processor", "20min", "x"));
    }

    @Test
    public void test_checkScope_rejectsDispatcherOnlyScopes() {
        assertThrows(IllegalStateException.class,
                () -> FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("a", "global", "20min", "x")));
        assertThrows(IllegalStateException.class,
                () -> FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("b", "company", "20min", "x")));
    }

    @Test
    public void test_checkScope_rejectsAnUnknownOrMissingScope() {
        // an unknown scope throws rather than being ignored: a silent downgrade leaves the author
        // believing a rule is in force when it is not
        assertThrows(IllegalStateException.class,
                () -> FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("a", "core", "20min", "x")));
        assertThrows(IllegalStateException.class,
                () -> FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer("b", "", "20min", "x")));
    }

    private static FunctionConfigYaml.Analyzer analyzer(String name, String scope, String timeout, String... regex) {
        return new FunctionConfigYaml.Analyzer(name, new ArrayList<>(List.of(regex)), timeout, false, scope);
    }
}
