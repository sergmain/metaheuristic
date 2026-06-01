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

package ai.metaheuristic.commons.yaml.task;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.CommonConsts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Phase 2 of 016: TaskParamsYaml V2 (single src/file) -> V3 (per-OS targets) migration,
 * multi-OS round-trip, and the internal-context (no targets) case.
 */
@Execution(CONCURRENT)
public class TaskParamsYamlV3UpgradeTest {

    @Test
    public void test_legacyV2_externalDispatcher_wrapsIntoDefaultTarget() {
        TaskParamsYamlV2 v2 = new TaskParamsYamlV2();
        v2.task.processCode = "p1";
        v2.task.execContextId = 1L;
        v2.task.context = EnumsApi.FunctionExecContext.external;
        TaskParamsYamlV2.FunctionConfigV2 fc = new TaskParamsYamlV2.FunctionConfigV2();
        fc.code = "fn:1.0";
        fc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        fc.file = "call-cc.jar";
        fc.src = "src";
        v2.task.function = fc;

        String yamlV2 = new TaskParamsYamlUtilsV2().toString(v2);
        TaskParamsYaml result = TaskParamsYamlUtils.UTILS.to(yamlV2);

        assertEquals(3, result.version);
        assertEquals(1, result.task.function.targets.size());
        TaskParamsYaml.Target def = result.task.function.targets.get(CommonConsts.MH_DEFAULT_OS_KEY);
        assertNotNull(def);
        assertEquals("call-cc.jar", def.file);
        assertEquals("src", def.src);
    }

    @Test
    public void test_legacyV2_internalContext_hasNoTargets() {
        TaskParamsYamlV2 v2 = new TaskParamsYamlV2();
        v2.task.processCode = "p1";
        v2.task.execContextId = 1L;
        v2.task.context = EnumsApi.FunctionExecContext.internal;
        TaskParamsYamlV2.FunctionConfigV2 fc = new TaskParamsYamlV2.FunctionConfigV2();
        fc.code = "fn:1.0";
        fc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        // internal context: no file
        v2.task.function = fc;

        String yamlV2 = new TaskParamsYamlUtilsV2().toString(v2);
        TaskParamsYaml result = TaskParamsYamlUtils.UTILS.to(yamlV2);

        assertEquals(3, result.version);
        assertTrue(result.task.function.targets.isEmpty(), "internal context must produce no targets");
    }

    @Test
    public void test_multiOsTargets_roundTrip() {
        TaskParamsYaml t = new TaskParamsYaml();
        t.task.processCode = "p1";
        t.task.execContextId = 1L;
        t.task.context = EnumsApi.FunctionExecContext.external;
        TaskParamsYaml.FunctionConfig fc = new TaskParamsYaml.FunctionConfig();
        fc.code = "fn:1.0";
        fc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        fc.targets.put("linux_amd64", new TaskParamsYaml.Target("bin/linux-amd64", "call-cc"));
        fc.targets.put("windows_amd64", new TaskParamsYaml.Target("bin/windows-amd64", "call-cc.exe"));
        t.task.function = fc;

        String yaml = TaskParamsYamlUtils.UTILS.toString(t);
        TaskParamsYaml loaded = TaskParamsYamlUtils.UTILS.to(yaml);

        assertEquals(3, loaded.version);
        assertEquals(2, loaded.task.function.targets.size());
        assertEquals("call-cc", loaded.task.function.targets.get("linux_amd64").file);
        assertEquals("bin/linux-amd64", loaded.task.function.targets.get("linux_amd64").src);
        assertEquals("call-cc.exe", loaded.task.function.targets.get("windows_amd64").file);
        assertEquals("bin/windows-amd64", loaded.task.function.targets.get("windows_amd64").src);
    }
}
