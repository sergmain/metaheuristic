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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BundleData;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYaml;
import ai.metaheuristic.commons.yaml.bundle_cfg.BundleCfgYamlUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bundle-level {@code analyzers} block of mh-bundle.yaml: parsed, validated, and applied at packaging to
 * every Function of the bundle, after the Function's own rules.
 *
 * @author Sergio Lissner
 * Date: 10/23/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class BundleAnalyzersTest {

    private static final String BUNDLE_WITH_ANALYZERS = """
            version: 1
            bundleConfig:
              - path: fn-a
                type: function
              - path: fn-b
                type: function
            analyzers:
              - name: shared-usage-limit
                regex:
                  - 'You.ve hit your usage limit'
                  - 'Claude usage limit reached'
                timeout: 1h
                incrementTries: false
                scope: processor
              - name: shared-quota
                regex:
                  - 'failed with status 429'
                timeout: 20min
                incrementTries: true
                scope: api
            """;

    private static final String BUNDLE_WITHOUT_ANALYZERS = """
            version: 1
            bundleConfig:
              - path: fn-a
                type: function
              - path: fn-b
                type: function
            """;

    private static FunctionConfigYaml.Analyzer analyzer(String name, String regex, String timeout, boolean incrementTries, EnumsApi.GateScope scope) {
        return new FunctionConfigYaml.Analyzer(name, new ArrayList<>(List.of(regex)), timeout, incrementTries, scope);
    }

    private static List<String> names(List<FunctionConfigYaml.Analyzer> analyzers) {
        return analyzers.stream().map(a -> a.name).toList();
    }

    // ---------- parsing ----------

    @Test
    public void test_load_bundleAnalyzers_everyFieldRead() {
        final BundleCfgYaml cfg = BundleCfgYamlUtils.UTILS.to(BUNDLE_WITH_ANALYZERS);

        assertEquals(2, cfg.bundleConfig.size(), "bundleConfig must be read as before");
        assertNotNull(cfg.analyzers, "the analyzers block must be read");
        assertEquals(2, cfg.analyzers.size());

        final FunctionConfigYaml.Analyzer first = cfg.analyzers.get(0);
        assertInstanceOf(FunctionConfigYaml.Analyzer.class, first, "V1 elements must be mapped to the current type");
        assertEquals("shared-usage-limit", first.name);
        assertEquals(List.of("You.ve hit your usage limit", "Claude usage limit reached"), first.regex);
        assertEquals("1h", first.timeout);
        assertFalse(first.incrementTries);
        assertEquals(EnumsApi.GateScope.processor, first.scope);

        final FunctionConfigYaml.Analyzer second = cfg.analyzers.get(1);
        assertEquals("shared-quota", second.name);
        assertEquals(List.of("failed with status 429"), second.regex);
        assertEquals("20min", second.timeout);
        assertTrue(second.incrementTries);
        assertEquals(EnumsApi.GateScope.api, second.scope);
    }

    @Test
    public void test_load_noAnalyzers_staysNull() {
        final BundleCfgYaml cfg = BundleCfgYamlUtils.UTILS.to(BUNDLE_WITHOUT_ANALYZERS);

        assertEquals(2, cfg.bundleConfig.size());
        assertNull(cfg.analyzers, "a bundle that declares no analyzers must not gain an empty list");
    }

    @Test
    public void test_roundTrip_analyzersSurvive() {
        final BundleCfgYaml cfg = BundleCfgYamlUtils.UTILS.to(BUNDLE_WITH_ANALYZERS);

        final String yaml = BundleCfgYamlUtils.UTILS.toString(cfg);
        assertFalse(yaml.contains("!!"), "the dumped bundle must carry no java type tags: " + yaml);

        final BundleCfgYaml reRead = BundleCfgYamlUtils.UTILS.to(yaml);
        assertNotNull(reRead.analyzers);
        assertEquals(cfg.analyzers, reRead.analyzers, "every field of every rule must survive the round trip");
    }

    @Test
    public void test_roundTrip_noAnalyzers_notEmitted() {
        final BundleCfgYaml cfg = BundleCfgYamlUtils.UTILS.to(BUNDLE_WITHOUT_ANALYZERS);

        final String yaml = BundleCfgYamlUtils.UTILS.toString(cfg);
        assertFalse(yaml.contains("analyzers"), "absence must stay absent in the dumped bundle: " + yaml);
    }

    @Test
    public void test_load_dispatcherOnlyScope_rejected() {
        final String yaml = BUNDLE_WITH_ANALYZERS.replace("scope: processor", "scope: company");

        final CheckIntegrityFailedException e = assertThrows(CheckIntegrityFailedException.class, () -> BundleCfgYamlUtils.UTILS.to(yaml));
        assertTrue(e.getMessage().contains("shared-usage-limit"), "the message must name the broken rule: " + e.getMessage());
    }

    @Test
    public void test_load_unreadableTimeout_rejected() {
        final String yaml = BUNDLE_WITH_ANALYZERS.replace("timeout: 20min", "timeout: 20 minutes");

        final CheckIntegrityFailedException e = assertThrows(CheckIntegrityFailedException.class, () -> BundleCfgYamlUtils.UTILS.to(yaml));
        assertTrue(e.getMessage().contains("shared-quota"), "the message must name the broken rule: " + e.getMessage());
    }

    @Test
    public void test_load_noRegex_rejected() {
        final String yaml = BUNDLE_WITH_ANALYZERS.replace("""
                    regex:
                      - 'failed with status 429'
                """, "");

        final CheckIntegrityFailedException e = assertThrows(CheckIntegrityFailedException.class, () -> BundleCfgYamlUtils.UTILS.to(yaml));
        assertTrue(e.getMessage().contains("can never match"), e.getMessage());
    }

    @Test
    public void test_load_regexNotCompiling_rejected() {
        final String yaml = BUNDLE_WITH_ANALYZERS.replace("'failed with status 429'", "'failed with (status 429'");

        final CheckIntegrityFailedException e = assertThrows(CheckIntegrityFailedException.class, () -> BundleCfgYamlUtils.UTILS.to(yaml));
        assertTrue(e.getMessage().contains("doesn't compile"), e.getMessage());
    }

    @Test
    public void test_load_everyBrokenRuleReported() {
        final String yaml = BUNDLE_WITH_ANALYZERS
                .replace("scope: processor", "scope: global")
                .replace("timeout: 20min", "timeout: soon");

        final CheckIntegrityFailedException e = assertThrows(CheckIntegrityFailedException.class, () -> BundleCfgYamlUtils.UTILS.to(yaml));
        assertTrue(e.getMessage().contains("shared-usage-limit"), e.getMessage());
        assertTrue(e.getMessage().contains("shared-quota"), e.getMessage());
    }

    // ---------- merge ----------

    @Test
    public void test_merge_bundleNull_ownReturnedAsIs() {
        final List<FunctionConfigYaml.Analyzer> own = new ArrayList<>(List.of(analyzer("own", "x", "1h", false, EnumsApi.GateScope.processor)));

        assertSame(own, FunctionAnalyzerUtils.withBundleAnalyzers(own, null));
        assertSame(own, FunctionAnalyzerUtils.withBundleAnalyzers(own, List.of()));
    }

    @Test
    public void test_merge_bothAbsent_staysNull() {
        assertNull(FunctionAnalyzerUtils.withBundleAnalyzers(null, null));
        assertNull(FunctionAnalyzerUtils.withBundleAnalyzers(null, List.of()));
    }

    @Test
    public void test_merge_ownAbsent_bundleRulesCopied() {
        final List<FunctionConfigYaml.Analyzer> bundle = List.of(analyzer("b1", "x", "1h", false, EnumsApi.GateScope.processor));

        final List<FunctionConfigYaml.Analyzer> actual = FunctionAnalyzerUtils.withBundleAnalyzers(null, bundle);

        assertNotNull(actual);
        assertEquals(bundle, actual);
        assertNotSame(bundle.get(0), actual.get(0), "a Function must get its own copy of a bundle rule");

        actual.get(0).name = "changed";
        actual.get(0).regex.add("y");
        assertEquals("b1", bundle.get(0).name, "the bundle's rule must not change through one Function's copy");
        assertEquals(List.of("x"), bundle.get(0).regex);
    }

    @Test
    public void test_merge_ownFirst_thenBundle() {
        final List<FunctionConfigYaml.Analyzer> own = List.of(
                analyzer("own1", "a", "1h", false, EnumsApi.GateScope.processor),
                analyzer("own2", "b", "1h", false, EnumsApi.GateScope.processor));
        final List<FunctionConfigYaml.Analyzer> bundle = List.of(
                analyzer("b1", "c", "5min", false, EnumsApi.GateScope.api),
                analyzer("b2", "d", "5min", true, EnumsApi.GateScope.function));

        final List<FunctionConfigYaml.Analyzer> actual = FunctionAnalyzerUtils.withBundleAnalyzers(own, bundle);

        assertNotNull(actual);
        assertEquals(List.of("own1", "own2", "b1", "b2"), names(actual));
    }

    @Test
    public void test_merge_ownRuleWinsFirstHit_onSameConsole() {
        // the Function narrows the meaning of the same console text: its rule must be consulted first
        final List<FunctionConfigYaml.Analyzer> own = List.of(analyzer("own-short", "usage limit", "5min", false, EnumsApi.GateScope.processor));
        final List<FunctionConfigYaml.Analyzer> bundle = List.of(analyzer("bundle-long", "usage limit", "1h", false, EnumsApi.GateScope.processor));

        final FunctionConfigYaml.Analyzer hit = FunctionAnalyzerUtils.firstHit(
                FunctionAnalyzerUtils.withBundleAnalyzers(own, bundle), "... Claude usage limit reached ...");

        assertNotNull(hit);
        assertEquals("own-short", hit.name);
    }

    @Test
    public void test_merge_identicalRuleSkipped_sameNameDifferentContentKept() {
        final List<FunctionConfigYaml.Analyzer> own = List.of(
                analyzer("quota", "429", "20min", false, EnumsApi.GateScope.api));
        final List<FunctionConfigYaml.Analyzer> bundle = List.of(
                analyzer("quota", "429", "20min", false, EnumsApi.GateScope.api),
                analyzer("quota", "429", "1h", false, EnumsApi.GateScope.api));

        final List<FunctionConfigYaml.Analyzer> actual = FunctionAnalyzerUtils.withBundleAnalyzers(own, bundle);

        assertNotNull(actual);
        assertEquals(2, actual.size(), "an identical rule is dropped, a rule sharing only the label is not");
        assertEquals("20min", actual.get(0).timeout);
        assertEquals("1h", actual.get(1).timeout);
    }

    @Test
    public void test_merge_idempotent() {
        final List<FunctionConfigYaml.Analyzer> own = List.of(analyzer("own", "a", "1h", false, EnumsApi.GateScope.processor));
        final List<FunctionConfigYaml.Analyzer> bundle = List.of(analyzer("b", "c", "5min", false, EnumsApi.GateScope.api));

        final List<FunctionConfigYaml.Analyzer> once = FunctionAnalyzerUtils.withBundleAnalyzers(own, bundle);
        final List<FunctionConfigYaml.Analyzer> twice = FunctionAnalyzerUtils.withBundleAnalyzers(once, bundle);

        assertEquals(once, twice);
    }

    // ---------- packaging ----------

    private static void writeFunction(Path baseDir, String dir, String code, String ownAnalyzers) throws Exception {
        final Path fnDir = baseDir.resolve(dir);
        Files.createDirectories(fnDir.resolve("src"));
        Files.writeString(fnDir.resolve("src").resolve("main.py"), "print('ok')\n");
        Files.writeString(fnDir.resolve(CommonConsts.MH_FUNCTION_YAML), """
                version: 3
                function:
                  code: %s
                  type: test.bundle-analyzers
                  env: python-3
                  sourcing: dispatcher
                  targets:
                    mh-default:
                      src: src
                      file: main.py
                %s""".formatted(code, ownAnalyzers));
    }

    private static final String OWN_ANALYZERS = """
              analyzers:
                - name: own-rule
                  regex:
                    - 'own console text'
                  timeout: 30s
                  incrementTries: true
                  scope: function
            """;

    private static FunctionConfigYaml packagedFunction(BundleData.Cfg cfg, String dir) throws Exception {
        final Path f = cfg.workingDir.resolve(dir).resolve(CommonConsts.MH_FUNCTION_YAML);
        assertTrue(Files.exists(f), "packaging must write the descriptor of " + dir);
        return FunctionConfigYamlUtils.UTILS.to(Files.readString(f));
    }

    private static BundleData.Cfg packageBundle(Path baseDir, String bundleYaml) throws Exception {
        writeFunction(baseDir, "fn-a", "test.fn-a-1.0", OWN_ANALYZERS);
        writeFunction(baseDir, "fn-b", "test.fn-b-1.0", "");
        Files.writeString(baseDir.resolve(CommonConsts.MH_BUNDLE_YAML), bundleYaml);

        final BundleData.Cfg cfg = new BundleData.Cfg(null, baseDir, null);
        cfg.initOtherPaths(null);
        final BundleCfgYaml bundleCfgYaml = BundleUtils.readBundleCfgYaml(cfg, CommonConsts.MH_BUNDLE_YAML);
        BundleUtils.createBundle(cfg, bundleCfgYaml);
        return cfg;
    }

    @Test
    public void test_package_bundleAnalyzersAppliedToEveryFunction(@TempDir Path baseDir) throws Exception {
        // ❗ A bundle never rewrites a Function's descriptor: a Function may be signed, and the rules its
        // bundle declares are bundle data, joined only at runtime. The descriptors come out of packaging
        // exactly as their authors wrote them, whatever the bundle declares.
        final BundleData.Cfg cfg = packageBundle(baseDir, BUNDLE_WITH_ANALYZERS);

        final FunctionConfigYaml a = packagedFunction(cfg, "fn-a");
        assertNotNull(a.function.analyzers);
        assertEquals(List.of("own-rule"), names(a.function.analyzers),
                "fn-a: only its own rule, the bundle's must not be copied in");

        final FunctionConfigYaml b = packagedFunction(cfg, "fn-b");
        assertNull(b.function.analyzers, "fn-b declares none itself and must stay without");

        // the bundle's rules travel with the bundle itself
        final BundleCfgYaml packagedBundle = BundleCfgYamlUtils.UTILS.to(
                Files.readString(cfg.workingDir.resolve(CommonConsts.MH_BUNDLE_YAML)));
        assertNotNull(packagedBundle.analyzers);
        assertEquals(List.of("shared-usage-limit", "shared-quota"), names(packagedBundle.analyzers));

        final FunctionConfigYaml.Analyzer quota = packagedBundle.analyzers.get(1);
        assertEquals(List.of("failed with status 429"), quota.regex);
        assertEquals("20min", quota.timeout);
        assertTrue(quota.incrementTries);
        assertEquals(EnumsApi.GateScope.api, quota.scope);
    }

    @Test
    public void test_package_noBundleAnalyzers_descriptorsUnchanged(@TempDir Path baseDir) throws Exception {
        final BundleData.Cfg cfg = packageBundle(baseDir, BUNDLE_WITHOUT_ANALYZERS);

        final FunctionConfigYaml a = packagedFunction(cfg, "fn-a");
        assertNotNull(a.function.analyzers);
        assertEquals(List.of("own-rule"), names(a.function.analyzers));

        final FunctionConfigYaml b = packagedFunction(cfg, "fn-b");
        assertNull(b.function.analyzers, "a Function without rules in a bundle without rules must stay without");
    }
}
