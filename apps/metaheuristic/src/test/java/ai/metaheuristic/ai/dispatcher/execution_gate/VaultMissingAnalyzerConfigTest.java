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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.utils.FunctionAnalyzerUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The "Vault has no entry" rule is configured at the dispatcher level, not in a Function descriptor: it is
 * Processor-emitted infrastructure ({@code DownloadSealedSecretService}, 01.812.040/01.812.041) common to every
 * Function that fetches a sealed secret, and {@code ExecutionGateService.loadAnalyzers} concatenates the
 * dispatcher list ({@code mh.dispatcher.execution-gate.analyzers}) ahead of any descriptor list.
 *
 * <p>A wrong property name binds to nothing silently - the analyzers list stays empty and the rule simply never
 * fires, which looks exactly like "the rule did not match". This test binds the EXACT property keys shipped in
 * {@code application.properties} into {@code Globals.Dispatcher.ExecutionGate} with Spring's own {@link Binder}
 * (no context, no doubles) and asserts one fully-populated {@link FunctionConfigYaml.Analyzer} comes out, then
 * that it behaves as the rule requires against the real Vault-missing console.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
public class VaultMissingAnalyzerConfigTest {

    /** The keys and values exactly as application.properties ships them (relaxed names bind to the camelCase fields). */
    private static Map<String, Object> shippedProperties() {
        final Map<String, Object> p = new LinkedHashMap<>();
        p.put("mh.dispatcher.execution-gate.analyzers[0].name", "vault locked or key missing");
        p.put("mh.dispatcher.execution-gate.analyzers[0].regex[0]", "(01\\.)?812\\.04[01] Vault has no entry");
        p.put("mh.dispatcher.execution-gate.analyzers[0].timeout", "2min");
        p.put("mh.dispatcher.execution-gate.analyzers[0].increment-tries", "false");
        p.put("mh.dispatcher.execution-gate.analyzers[0].scope", "api");
        return p;
    }

    private static Globals.ExecutionGate bind(Map<String, Object> properties) {
        final ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        return new Binder(source)
                .bind("mh.dispatcher.execution-gate", Globals.ExecutionGate.class)
                .orElseGet(Globals.ExecutionGate::new);
    }

    @Test
    public void test_theShippedPropertiesBindToOneFullyPopulatedAnalyzer() {
        final Globals.ExecutionGate gate = bind(shippedProperties());

        assertEquals(1, gate.analyzers.size(), "the shipped keys must bind to exactly one analyzer; 0 means a key is misnamed");
        final FunctionConfigYaml.Analyzer a = gate.analyzers.get(0);
        assertEquals("vault locked or key missing", a.name);
        assertNotNull(a.regex);
        assertEquals(1, a.regex.size());
        assertEquals("(01\\.)?812\\.04[01] Vault has no entry", a.regex.get(0));
        assertEquals("2min", a.timeout);
        assertFalse(a.incrementTries, "increment-tries must bind to false - a locked Vault is never the Task's fault");
        assertEquals(EnumsApi.GateScope.api, a.scope);
    }

    @Test
    public void test_theBoundRuleMatchesTheRealVaultMissingConsoleAndNotAnUnrelatedOne() {
        final FunctionConfigYaml.Analyzer a = bind(shippedProperties()).analyzers.get(0);

        assertSame(a, FunctionAnalyzerUtils.firstHit(java.util.List.of(a),
                "01.812.040 Vault has no entry for companyId=2, keyCode=RG_API_AUTH. Task #429 is finished with error."));
        assertSame(a, FunctionAnalyzerUtils.firstHit(java.util.List.of(a),
                "01.812.041 Vault has no entry for companyId=2, keyCode=RG_API_AUTH. Task #430 is finished with error."));
        assertNull(FunctionAnalyzerUtils.firstHit(java.util.List.of(a),
                "01.812.050 BAD_GATEWAY fetching sealed secret for companyId=2, keyCode=RG_API_AUTH; retry next cycle"));
    }

    @Test
    public void test_theBoundTimeoutIsTheShortReprobeInterval() {
        final FunctionConfigYaml.Analyzer a = bind(shippedProperties()).analyzers.get(0);

        // short on purpose: a locked Vault clears only on a human unlock, so the free retry should re-probe soon
        assertEquals(Duration.ofMinutes(2), FunctionAnalyzerUtils.parseTimeout(a.timeout));
    }
}
