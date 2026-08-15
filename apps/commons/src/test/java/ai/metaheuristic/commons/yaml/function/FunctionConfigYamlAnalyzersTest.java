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
