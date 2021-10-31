/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml;

import ai.metaheuristic.commons.yaml.env.EnvParamsYaml;
import ai.metaheuristic.commons.yaml.env.EnvParamsYamlUtils;
import ai.metaheuristic.commons.yaml.env.EnvParamsYamlUtilsV2;
import ai.metaheuristic.commons.yaml.env.EnvParamsYamlV2;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 10/16/2021
 * Time: 2:28 AM
 */
public class TestEnvYaml {

    @Test
    public void testVersion() {
        assertEquals( new EnvParamsYaml().version, EnvParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion() );
    }

    @Test
    public void testV2() throws IOException {
        String s = IOUtils.resourceToString("/yaml/env-v2.yaml", StandardCharsets.UTF_8);
        final EnvParamsYamlUtilsV2 forVersion = (EnvParamsYamlUtilsV2)EnvParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
        assertNotNull(forVersion);
        EnvParamsYamlV2 env = forVersion.to(s);

        assertEquals(3, env.processors.size());
        assertEquals("proc-01", env.processors.get(0).code);
        assertEquals("stat, ai, ai-common, pc9", env.processors.get(0).tags);
        assertEquals("proc-02", env.processors.get(1).code);
        assertEquals("stat, ai-common", env.processors.get(1).tags);
        assertEquals("proc-03", env.processors.get(2).code);
        assertEquals("ai-common", env.processors.get(2).tags);

        assertEquals(1, env.disk.size());
        assertEquals("test-files", env.disk.get(0).code);
        assertEquals("/test-files-edition", env.disk.get(0).path);

        assertEquals(1, env.envs.size());
        assertEquals("python", env.envs.get("python-3"));

        assertEquals(1, env.mirrors.size());
        assertEquals("/git-probe/mh-01/mh.git", env.mirrors.get("https://github.com/sergmain/metaheuristic.git"));
    }
    @Test
    public void testV3() throws IOException {
        String s = IOUtils.resourceToString("/yaml/env-v3.yaml", StandardCharsets.UTF_8);
        EnvParamsYaml env = EnvParamsYamlUtils.BASE_YAML_UTILS.to(s);

        assertEquals(777, env.quotas.limit);
        assertEquals(11, env.quotas.defaultValue);
        assertEquals(4, env.quotas.values.size());
        assertEquals("stat", env.quotas.values.get(0).tag);
        assertEquals(111, env.quotas.values.get(0).amount);
        assertEquals("ai", env.quotas.values.get(1).tag);
        assertEquals(222, env.quotas.values.get(1).amount);
        assertEquals("ai-common", env.quotas.values.get(2).tag);
        assertEquals(333, env.quotas.values.get(2).amount);
        assertEquals("pc9", env.quotas.values.get(3).tag);
        assertEquals(444, env.quotas.values.get(3).amount);

        assertEquals(3, env.processors.size());
        assertEquals("proc-01", env.processors.get(0).code);
        assertEquals("stat, ai, ai-common, pc9", env.processors.get(0).tags);
        assertEquals("proc-02", env.processors.get(1).code);
        assertEquals("stat, ai-common", env.processors.get(1).tags);
        assertEquals("proc-03", env.processors.get(2).code);
        assertEquals("ai-common", env.processors.get(2).tags);

        assertEquals(1, env.disk.size());
        assertEquals("test-files", env.disk.get(0).code);
        assertEquals("/test-files-edition", env.disk.get(0).path);

        assertEquals(1, env.envs.size());
        assertEquals("python", env.envs.get("python-3"));

        assertEquals(1, env.mirrors.size());
        assertEquals("/git-probe/mh-01/mh.git", env.mirrors.get("https://github.com/sergmain/metaheuristic.git"));
    }
}
