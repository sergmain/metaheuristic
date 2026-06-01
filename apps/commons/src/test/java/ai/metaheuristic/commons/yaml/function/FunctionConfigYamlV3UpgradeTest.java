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

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Phase 1 of 016: confirms the V2 (single src/file) -> V3 (per-OS targets) migration and
 * that a multi-OS version-less config round-trips through YAML intact.
 */
@Execution(CONCURRENT)
public class FunctionConfigYamlV3UpgradeTest {

    @Test
    public void test_legacyV2Yaml_wrapsIntoDefaultTarget() {
        FunctionConfigYamlV2 v2 = new FunctionConfigYamlV2();
        v2.function.code = "fn:1.0";
        v2.function.type = "type";
        v2.function.file = "call-cc.jar";
        v2.function.src = "src";
        v2.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        Objects.requireNonNull(v2.function.metas).add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        // dump as a version-2 document, then load via the registry (version detection -> full chain)
        String yamlV2 = new FunctionConfigYamlUtilsV2().toString(v2);
        FunctionConfigYaml result = FunctionConfigYamlUtils.UTILS.to(yamlV2);

        assertEquals(3, result.version);
        assertEquals(1, result.function.targets.size());
        FunctionConfigYaml.Target def = result.function.targets.get(CommonConsts.MH_DEFAULT_OS_KEY);
        assertNotNull(def, "legacy file/src must be wrapped under MH_DEFAULT_OS_KEY");
        assertEquals("call-cc.jar", def.file);
        assertEquals("src", def.src);
    }

    @Test
    public void test_multiOsTargets_roundTrip() {
        FunctionConfigYaml fc = new FunctionConfigYaml();
        fc.function.code = "fn:1.0";
        fc.function.type = "type";
        fc.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        fc.function.targets.put("linux_amd64", new FunctionConfigYaml.Target("bin/linux-amd64", "call-cc"));
        fc.function.targets.put("windows_amd64", new FunctionConfigYaml.Target("bin/windows-amd64", "call-cc.exe"));
        Objects.requireNonNull(fc.function.metas).add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        String yaml = FunctionConfigYamlUtils.UTILS.toString(fc);
        FunctionConfigYaml loaded = FunctionConfigYamlUtils.UTILS.to(yaml);

        assertEquals(3, loaded.version);
        assertEquals(2, loaded.function.targets.size());
        assertEquals("call-cc", loaded.function.targets.get("linux_amd64").file);
        assertEquals("bin/linux-amd64", loaded.function.targets.get("linux_amd64").src);
        assertEquals("call-cc.exe", loaded.function.targets.get("windows_amd64").file);
        assertEquals("bin/windows-amd64", loaded.function.targets.get("windows_amd64").src);
    }

    @Test
    public void test_dispatcherSourcing_emptyTargets_failsIntegrity() {
        FunctionConfigYaml fc = new FunctionConfigYaml();
        fc.function.code = "fn:1.0";
        fc.function.type = "type";
        fc.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        // no targets added
        assertThrows(Exception.class, fc::checkIntegrity);
    }
}
