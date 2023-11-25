/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.function;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static ai.metaheuristic.ai.dispatcher.function.FunctionService.FUNCTION_CONFIG_YAML_SCHEME_VALIDATOR;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Serge
 * Date: 9/10/2020
 * Time: 12:02 AM
 */
@Execution(CONCURRENT)
public class TestFunctionConfigSchemeValidation {

    @Test
    public void testOk() {
        String yaml = createYaml();
        String result = FUNCTION_CONFIG_YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(yaml);
        assertNull(result, "result: " + result + "\nyaml\n"+ yaml );
    }

    @Test
    public void testError_01() throws IOException {
        String cfg = IOUtils.resourceToString("/yaml/bundle/bundle-verification-test-error-v3-01.yaml", StandardCharsets.UTF_8);
        String result = FUNCTION_CONFIG_YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        assertNotNull(result, result);
    }

    @Test
    public void testError_02() throws IOException {
        String cfg = IOUtils.resourceToString("/yaml/bundle/bundle-verification-test-error-v3-02.yaml", StandardCharsets.UTF_8);
        String result = FUNCTION_CONFIG_YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        assertNotNull(result, result);
    }

    @Test
    public void test_v1_01() throws IOException {
        String cfg = IOUtils.resourceToString("/yaml/bundle/bundle-verification-test-v1-01.yaml", StandardCharsets.UTF_8);
        String result = FUNCTION_CONFIG_YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        assertNull(result, result);
    }

    @Test
    public void test_v2_01() throws IOException {
        String cfg = IOUtils.resourceToString("/yaml/bundle/bundle-verification-test-v2-01.yaml", StandardCharsets.UTF_8);

        String result = FUNCTION_CONFIG_YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        assertNull(result, result);
    }

    @Test
    public void test_v3_01() throws IOException {
        String cfg = IOUtils.resourceToString("/yaml/bundle/bundle-verification-test-v3-01.yaml", StandardCharsets.UTF_8);

        String result = FUNCTION_CONFIG_YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        assertNull(result, result);
    }

    @Test
    public void test_v3_02() throws IOException {
        String cfg = IOUtils.resourceToString("/yaml/bundle/bundle-verification-test-v3-02.yaml", StandardCharsets.UTF_8);

        String result = FUNCTION_CONFIG_YAML_SCHEME_VALIDATOR.validateStructureOfDispatcherYaml(cfg);
        assertNull(result, result);
    }

    private static String createYaml() {
        FunctionConfigYaml cfgList = new FunctionConfigYaml();
        FunctionConfigYaml.FunctionConfig cfg = cfgList.function;

        cfgList.system.checksumMap.put(EnumsApi.HashAlgo.SHA256, "123");
        cfg.code = "code";
        cfg.type = "type";
        cfg.file = "file";
        cfg.params = "params";
        cfg.env = "env";
        cfg.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        cfg.git = new GitInfo("repo", "branch", "commit", null);
        cfg.metas.add(Map.of("meta-key", "meta-value"));
        cfg.metas.add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        return FunctionConfigYamlUtils.UTILS.toString(cfgList);
    }
}
