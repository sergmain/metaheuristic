/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.env.EnvParamsYamlUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
public class TestEnvYaml {

    @Test
    public void testEnvYaml() throws IOException {

        String yaml = IOUtils.resourceToString("/yaml/env/env.yaml", StandardCharsets.UTF_8);
        EnvParamsYaml envYaml = EnvParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertNotNull(envYaml);
        assertNotNull(envYaml.getEnvs());
        assertEquals(2, envYaml.getEnvs().size());
        assertEquals("python", envYaml.getEnvs().get(0).code);
        assertEquals("python.exe", envYaml.getEnvs().get(0).exec);
        assertEquals("python-3", envYaml.getEnvs().get(1).code);
        assertEquals("E:\\Anaconda3\\envs\\python-36\\python.exe", envYaml.getEnvs().get(1).exec);

        assertNotNull(envYaml.getDisk());
        assertEquals(2, envYaml.getDisk().size());
        assertEquals("code-1", envYaml.getDisk().get(0).code);
        assertEquals("C:\\storage\\code-1", envYaml.getDisk().get(0).path);
        assertEquals("code-2", envYaml.getDisk().get(1).code);
        assertEquals("C:\\storage\\code-2", envYaml.getDisk().get(1).path);



        String s = EnvParamsYamlUtils.BASE_YAML_UTILS.toString(envYaml);

        EnvParamsYaml envYaml1 = EnvParamsYamlUtils.BASE_YAML_UTILS.to(s);
        assertEquals(envYaml, envYaml1);
    }

    @Test
    public void testEnvYaml_1() throws IOException {

        String yaml = IOUtils.resourceToString("/yaml/env/env-2-lines.yaml", StandardCharsets.UTF_8);
        EnvParamsYaml envYaml = EnvParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertNotNull(envYaml);
        assertNotNull(envYaml.getEnvs());
        assertEquals(2, envYaml.getEnvs().size());
        EnvParamsYaml.Env env;
        env = envYaml.getEnvs().stream().filter(o->o.code.equals("simple-app")).findFirst().orElseThrow();
        assertEquals("simple-app.jar", env.exec);
        env = envYaml.getEnvs().stream().filter(o->o.code.equals("lines")).findFirst().orElseThrow();
        assertEquals("1st-line 2nd-line", env.exec);

        assertNotNull(envYaml.getDisk());
        assertTrue(envYaml.getDisk().isEmpty());

        String s = EnvParamsYamlUtils.BASE_YAML_UTILS.toString(envYaml);

        EnvParamsYaml envYaml1 = EnvParamsYamlUtils.BASE_YAML_UTILS.to(s);
        assertEquals(envYaml, envYaml1);
    }
}
