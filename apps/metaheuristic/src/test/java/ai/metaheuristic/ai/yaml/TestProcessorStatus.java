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

package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Serge
 * Date: 5/23/2019
 * Time: 3:54 PM
 */
public class TestProcessorStatus {

    @Test
    public void test() throws IOException {
        String yaml = IOUtils.resourceToString("/yaml/processor/processor-status-01.yaml", StandardCharsets.UTF_8);
        ProcessorStatusYaml ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(yaml);

/*
env:
  disk: [
  ]
  envs:
    python-3: C:\Anaconda3\envs\python-3.7\python.exe
    java-11: C:\jdk-11.0.2\bin\java.exe -jar
  mirrors:
    https://bitbucket.org/buzovskaya/jcons.git: \\192.168.10.9\disk-s\jcons-git-mirror
    https://bitbucket.org/buzovskaya/ric411.git: \\192.168.10.9\disk-s\ric411-git-mirror
gitStatusInfo:
  status: installed
  version: 2.21.0.windows.1
schedule: |
  workingDay: 0:00-23:59
  weekend: 0:00-23:59
sessionCreatedOn: 1558588131596
sessionId: e7af5f04-7123-487d-a7af-39224ee9c6fa-b42407fd-b953-454e-82e9-7384a0c34669
*/

        assertNotNull(ss.gitStatusInfo);
        assertEquals(Enums.GitStatus.installed, ss.gitStatusInfo.status);
        assertEquals("2.21.0.windows.1", ss.gitStatusInfo.version);
        assertEquals("workingDay: 0:00-23:59\nweekend: 0:00-23:59\n", ss.schedule);
        assertEquals(1558588131596L, ss.sessionCreatedOn);
        assertEquals("e7af5f04-7123-487d-a7af-39224ee9c6fa-b42407fd-b953-454e-82e9-7384a0c34669", ss.sessionId);

        assertNotNull(ss.env);
        assertEquals(2, ss.env.envs.size());
        assertNotNull(ss.env.envs.get("python-3"));
        assertNotNull(ss.env.envs.get("java-11"));
        assertEquals("C:\\Anaconda3\\envs\\python-3.7\\python.exe", ss.env.envs.get("python-3"));
        assertEquals("C:\\jdk-11.0.2\\bin\\java.exe -jar", ss.env.envs.get("java-11"));

        assertEquals(2, ss.env.mirrors.size());
        assertNotNull(ss.env.mirrors.get("https://bitbucket.org/buzovskaya/ric411.git"));
        assertNotNull(ss.env.mirrors.get("https://bitbucket.org/buzovskaya/jcons.git"));
        assertEquals("\\\\192.168.10.9\\disk-s\\jcons-git-mirror",
                ss.env.mirrors.get("https://bitbucket.org/buzovskaya/jcons.git"));
        assertEquals("\\\\192.168.10.9\\disk-s\\ric411-git-mirror",
                ss.env.mirrors.get("https://bitbucket.org/buzovskaya/ric411.git"));
    }
}
