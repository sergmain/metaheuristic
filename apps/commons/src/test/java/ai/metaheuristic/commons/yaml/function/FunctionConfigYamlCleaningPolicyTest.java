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
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * cleaningPolicy is a Processor-side only option of a Function. It was added as a @Nullable field,
 * i.e. without a version bump, so an old config without that field must still be readable.
 */
@Execution(CONCURRENT)
public class FunctionConfigYamlCleaningPolicyTest {

    private static FunctionConfigYaml createFunctionConfig(@org.jspecify.annotations.Nullable String cleaningPolicy) {
        FunctionConfigYaml fc = new FunctionConfigYaml();
        fc.function.code = "fn:1.0";
        fc.function.type = "type";
        fc.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        fc.function.assetDir = "assets";
        fc.function.cleaningPolicy = cleaningPolicy;
        fc.function.targets.put(CommonConsts.MH_DEFAULT_OS_KEY, new FunctionConfigYaml.Target("src", "call-cc.jar"));
        return fc;
    }

    @Test
    public void test_cleaningPolicyAssets_roundTrip() {
        String yaml = FunctionConfigYamlUtils.UTILS.toString(createFunctionConfig("ASSETS"));
        FunctionConfigYaml loaded = FunctionConfigYamlUtils.UTILS.to(yaml);

        assertEquals(3, loaded.version);
        assertEquals("ASSETS", loaded.function.cleaningPolicy);
    }

    @Test
    public void test_cleaningPolicyAll_roundTrip() {
        String yaml = FunctionConfigYamlUtils.UTILS.toString(createFunctionConfig("ALL"));
        FunctionConfigYaml loaded = FunctionConfigYamlUtils.UTILS.to(yaml);

        assertEquals("ALL", loaded.function.cleaningPolicy);
    }

    @Test
    public void test_cleaningPolicyNotSpecified_staysNull() {
        String yaml = FunctionConfigYamlUtils.UTILS.toString(createFunctionConfig(null));
        FunctionConfigYaml loaded = FunctionConfigYamlUtils.UTILS.to(yaml);

        assertNull(loaded.function.cleaningPolicy);
    }

    @Test
    public void test_v3DocWithCleaningPolicy_upgradesToVersionLess() {
        FunctionConfigYamlV3 v3 = new FunctionConfigYamlV3();
        v3.function.code = "fn:1.0";
        v3.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        v3.function.assetDir = "assets";
        v3.function.cleaningPolicy = "ASSETS";
        v3.function.targets.put(CommonConsts.MH_DEFAULT_OS_KEY, new FunctionConfigYamlV3.TargetV3("src", "call-cc.jar"));

        String yamlV3 = new FunctionConfigYamlUtilsV3().toString(v3);
        assertTrue(yamlV3.contains("cleaningPolicy"), yamlV3);

        FunctionConfigYaml loaded = FunctionConfigYamlUtils.UTILS.to(yamlV3);
        assertEquals("ASSETS", loaded.function.cleaningPolicy);
    }

    @Test
    public void test_legacyV2Doc_hasNoCleaningPolicy() {
        FunctionConfigYamlV2 v2 = new FunctionConfigYamlV2();
        v2.function.code = "fn:1.0";
        v2.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        v2.function.file = "call-cc.jar";
        v2.function.src = "src";

        String yamlV2 = new FunctionConfigYamlUtilsV2().toString(v2);
        FunctionConfigYaml loaded = FunctionConfigYamlUtils.UTILS.to(yamlV2);

        assertNull(loaded.function.cleaningPolicy);
    }

    @Test
    public void test_toFunctionConfig_propagatesCleaningPolicy() {
        TaskParamsYaml.FunctionConfig trg = TaskParamsUtils.toFunctionConfig(createFunctionConfig("ASSETS"));
        assertEquals("ASSETS", trg.cleaningPolicy);
    }

    @Test
    public void test_toFunctionConfig_propagatesNull() {
        TaskParamsYaml.FunctionConfig trg = TaskParamsUtils.toFunctionConfig(createFunctionConfig(null));
        assertNull(trg.cleaningPolicy);
    }

    @Test
    public void test_taskParamsYaml_roundTripKeepsCleaningPolicy() {
        TaskParamsYaml t = new TaskParamsYaml();
        t.task.processCode = "p1";
        t.task.execContextId = 1L;
        t.task.context = EnumsApi.FunctionExecContext.external;
        t.task.function = TaskParamsUtils.toFunctionConfig(createFunctionConfig("ASSETS"));

        String yaml = TaskParamsYamlUtils.UTILS.toString(t);
        TaskParamsYaml loaded = TaskParamsYamlUtils.UTILS.to(yaml);

        assertEquals("ASSETS", loaded.task.function.cleaningPolicy);
    }

    @Test
    public void test_cleaningPolicyList_roundTrip() {
        String yaml = FunctionConfigYamlUtils.UTILS.toString(createFunctionConfig("ASSETS, FULL_AFTER_ONE_WEEK"));
        FunctionConfigYaml loaded = FunctionConfigYamlUtils.UTILS.to(yaml);

        assertEquals("ASSETS, FULL_AFTER_ONE_WEEK", loaded.function.cleaningPolicy);
    }

    @Test
    public void test_cleaningPolicyList_asWrittenInDescriptor() {
        // the form of mh-function.yaml - an unquoted scalar with a comma
        String yaml = """
                version: 3
                function:
                  code: fn:1.0
                  type: type
                  sourcing: dispatcher
                  targets:
                    mh-default:
                      src: src
                      file: call-cc.jar
                  cleaningPolicy: ASSETS, FULL_AFTER_ONE_WEEK
                """;
        FunctionConfigYaml loaded = FunctionConfigYamlUtils.UTILS.to(yaml);

        assertEquals("ASSETS, FULL_AFTER_ONE_WEEK", loaded.function.cleaningPolicy);
    }

    @Test
    public void test_cleaningPolicyWithUnknownName_isRefused() {
        FunctionConfigYaml fc = createFunctionConfig("ASSETS, FULL_AFTER_A_WEEK");
        RuntimeException e = assertThrows(RuntimeException.class, fc::checkIntegrity);
        assertTrue(e.getMessage().contains("FULL_AFTER_A_WEEK"), e.getMessage());
    }

    @Test
    public void test_taskParamsYaml_withEnumEraCleaningPolicy_isReadAsString() {
        // task params stored before the field became a list carry one name - it must be read as is
        TaskParamsYaml t = new TaskParamsYaml();
        t.task.processCode = "p1";
        t.task.execContextId = 1L;
        t.task.context = EnumsApi.FunctionExecContext.external;
        t.task.function = TaskParamsUtils.toFunctionConfig(createFunctionConfig("ASSETS, FULL_AFTER_ONE_WEEK"));

        String yaml = TaskParamsYamlUtils.UTILS.toString(t);
        assertTrue(yaml.contains("cleaningPolicy: ASSETS, FULL_AFTER_ONE_WEEK"), yaml);
        TaskParamsYaml loaded = TaskParamsYamlUtils.UTILS.to(yaml.replace("cleaningPolicy: ASSETS, FULL_AFTER_ONE_WEEK", "cleaningPolicy: ASSETS"));

        assertEquals("ASSETS", loaded.task.function.cleaningPolicy);
    }
}
