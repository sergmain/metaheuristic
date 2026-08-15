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

package ai.metaheuristic.commons.yaml.function;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.FunctionAnalyzerUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The {@code analyzers} block survives a round trip, and its absence stays absent.
 *
 * <p>The second half matters more than the first. The field was added WITHOUT a version bump, on the
 * grounds that a nullable addition is backward compatible — so every descriptor already signed and
 * deployed omits the key entirely, and must keep loading.
 *
 * @author Sergio Lissner
 * Date: 8/14/2026
 */
@Execution(CONCURRENT)
public class FunctionConfigYamlAnalyzersTest {

    @Test
    public void test_twoAnalyzersSurviveARoundTrip() {
        final FunctionConfigYaml cfg = baseConfig();
        cfg.function.analyzers = new ArrayList<>();
        cfg.function.analyzers.add(new FunctionConfigYaml.Analyzer(
                "downtime", new ArrayList<>(List.of("(?i)rate.?limit", "quota exhausted")), "20min", false, EnumsApi.GateScope.api));
        cfg.function.analyzers.add(new FunctionConfigYaml.Analyzer(
                "host-broken", new ArrayList<>(List.of("no space left on device")), "1h", true, EnumsApi.GateScope.processor));

        final FunctionConfigYaml reRead = roundTrip(cfg);

        assertNotNull(reRead.function.analyzers);
        assertEquals(2, reRead.function.analyzers.size());

        final FunctionConfigYaml.Analyzer first = reRead.function.analyzers.get(0);
        assertEquals("downtime", first.name);
        assertEquals(List.of("(?i)rate.?limit", "quota exhausted"), first.regex);
        assertEquals("20min", first.timeout);
        assertFalse(first.incrementTries);
        assertEquals(EnumsApi.GateScope.api, first.scope);

        final FunctionConfigYaml.Analyzer second = reRead.function.analyzers.get(1);
        assertEquals("host-broken", second.name);
        assertEquals(List.of("no space left on device"), second.regex);
        assertEquals("1h", second.timeout);
        assertTrue(second.incrementTries);
        assertEquals(EnumsApi.GateScope.processor, second.scope);
    }

    @Test
    public void test_aDescriptorWithNoAnalyzersKeyStillLoadsAndYieldsNull() {
        // this is what every already-signed bundle in the field looks like
        final FunctionConfigYaml reRead = roundTrip(baseConfig());

        assertNull(reRead.function.analyzers);
        assertEquals("some-function:1.1", reRead.function.code);
    }

    @Test
    public void test_anEmptyAnalyzersListIsNotTheSameAsAbsent() {
        final FunctionConfigYaml cfg = baseConfig();
        cfg.function.analyzers = new ArrayList<>();

        final FunctionConfigYaml reRead = roundTrip(cfg);

        assertNotNull(reRead.function.analyzers);
        assertTrue(reRead.function.analyzers.isEmpty());
    }

    @Test
    public void test_cloneCopiesAnalyzersDeeply() {
        // FunctionConfig.clone() is used when a Function config is handed around; a shallow copy would
        // let one caller's edit reach another's list
        final FunctionConfigYaml cfg = baseConfig();
        cfg.function.analyzers = new ArrayList<>();
        cfg.function.analyzers.add(new FunctionConfigYaml.Analyzer(
                "downtime", new ArrayList<>(List.of("a")), "30s", false, EnumsApi.GateScope.function));

        final FunctionConfigYaml.FunctionConfig clone = cfg.function.clone();
        assertNotNull(clone.analyzers);

        clone.analyzers.get(0).name = "changed";
        clone.analyzers.get(0).regex.add("b");

        assertEquals("downtime", cfg.function.analyzers.get(0).name, "the original's analyzer must not change");
        assertEquals(List.of("a"), cfg.function.analyzers.get(0).regex, "the original's regex list must not change");
    }

    @Test
    public void test_cloneToleratesAbsentAnalyzers() {
        final FunctionConfigYaml cfg = baseConfig();
        final FunctionConfigYaml.FunctionConfig clone = cfg.function.clone();
        assertNull(clone.analyzers);
    }

    @Test
    public void test_aHandWrittenDescriptorLoadsItsAnalyzers() {
        // NB Round-tripping through toString/to does NOT prove this. A descriptor is hand-written YAML,
        // and hand-written YAML can fail where a serialised document succeeds: the block sitting at the
        // wrong level, or an enum that only deserialises from the exact constant spelling. This is the
        // shape a real Function descriptor uses.
        final String descriptor = """
            version: 3
            function:
              code: some-function:1.1
              env: java-25
              targets:
                mh-default:
                  src: src
                  file: some-function.jar
              sourcing: dispatcher
              api:
                keyCode: SOME_API_KEY
              analyzers:
                - name: api-quota-exhausted
                  regex:
                    - 'call failed with status 429'
                    - '"type":"rate_limit_error"'
                  timeout: 20min
                  incrementTries: false
                  scope: api
                - name: api-credentials-rejected
                  regex:
                    - 'call failed with status 401'
                  timeout: 30min
                  incrementTries: true
                  scope: api
            """;

        final FunctionConfigYaml cfg = FunctionConfigYamlUtils.UTILS.to(descriptor);
        assertNotNull(cfg);
        assertNotNull(cfg.function.analyzers);
        assertEquals(2, cfg.function.analyzers.size());

        final FunctionConfigYaml.Analyzer quota = cfg.function.analyzers.get(0);
        assertEquals("api-quota-exhausted", quota.name);
        assertEquals(EnumsApi.GateScope.api, quota.scope, "lower-case 'api' must deserialise to the enum constant");
        assertEquals("20min", quota.timeout);
        assertFalse(quota.incrementTries);
        assertEquals(2, quota.regex.size());

        final FunctionConfigYaml.Analyzer creds = cfg.function.analyzers.get(1);
        assertTrue(creds.incrementTries, "a non-transient failure must be able to charge the retry");

        // every declared timeout must be readable and every pattern must compile - a descriptor that
        // parses but whose rules cannot be applied is worse than one that fails to parse
        for (FunctionConfigYaml.Analyzer analyzer : cfg.function.analyzers) {
            assertNotNull(FunctionAnalyzerUtils.parseTimeout(analyzer.timeout));
            FunctionAnalyzerUtils.checkScopeAllowedInDescriptor(analyzer);
            for (String regex : analyzer.regex) {
                java.util.regex.Pattern.compile(regex);
            }
        }
    }

    @Test
    public void test_aHandWrittenDescriptorWithNoAnalyzersBlockStillLoads() {
        final String descriptor = """
            version: 3
            function:
              code: some-function:1.1
              targets:
                mh-default:
                  src: src
                  file: some-function.jar
              sourcing: dispatcher
            """;

        final FunctionConfigYaml cfg = FunctionConfigYamlUtils.UTILS.to(descriptor);
        assertNotNull(cfg);
        assertNull(cfg.function.analyzers);
    }

    private static FunctionConfigYaml roundTrip(FunctionConfigYaml cfg) {
        final String yaml = FunctionConfigYamlUtils.UTILS.toString(cfg);
        final FunctionConfigYaml reRead = FunctionConfigYamlUtils.UTILS.to(yaml);
        assertNotNull(reRead);
        return reRead;
    }

    private static FunctionConfigYaml baseConfig() {
        final FunctionConfigYaml cfg = new FunctionConfigYaml();
        cfg.function.code = "some-function:1.1";
        cfg.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        cfg.function.targets.put(CommonConsts.MH_DEFAULT_OS_KEY, new FunctionConfigYaml.Target("src", "some-function.jar"));
        return cfg;
    }
}
