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

import ai.metaheuristic.commons.yaml.YamlUtils;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirms that SnakeYaml - configured exactly as the project configures it via
 * YamlUtils.init - accepts an underscore-bearing map key like "linux_amd64" as a
 * plain String key, round-tripping it without quoting or implicit-resolver coercion.
 * This is the proposed key shape for per-OS Function 'targets'.
 */
@Execution(ExecutionMode.CONCURRENT)
public class FunctionTargetsMapKeyYamlTest {

    @Data
    public static class Holder {
        public Map<String, String> targets = new LinkedHashMap<>();
    }

    @Test
    public void test_underscoreMapKey_roundTripsViaProjectYaml() {
        Holder h = new Holder();
        h.targets.put("linux_amd64", "bin/linux-amd64");
        h.targets.put("darwin_arm64", "bin/darwin-arm64");
        h.targets.put("windows_amd64", "call-cc.exe");

        Yaml yaml = YamlUtils.init(Holder.class);
        String dumped = yaml.dump(h);

        // key must be emitted as a plain (unquoted) scalar
        assertTrue(dumped.contains("linux_amd64:"), dumped);

        Holder loaded = (Holder) yaml.load(dumped);
        assertEquals(3, loaded.targets.size());
        assertEquals("bin/linux-amd64", loaded.targets.get("linux_amd64"));
        assertEquals("bin/darwin-arm64", loaded.targets.get("darwin_arm64"));
        assertEquals("call-cc.exe", loaded.targets.get("windows_amd64"));

        // each key stays a String (no int/bool/timestamp resolver coercion)
        for (Object k : loaded.targets.keySet()) {
            assertInstanceOf(String.class, k);
        }
    }
}
