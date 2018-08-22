/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.yaml;

import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestEnvYaml {

    @Test
    public void testEnvYaml() throws IOException {

        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/env.yaml")) {
            EnvYaml envYaml = EnvYamlUtils.toEnvYaml(is);
            assertNotNull(envYaml);
            assertNotNull(envYaml.getEnvs());
            assertEquals(2, envYaml.getEnvs().size());
            assertEquals("python.exe", envYaml.getEnvs().get("python"));
            assertEquals("E:\\Anaconda3\\envs\\python-36\\python.exe", envYaml.getEnvs().get("python-3"));

            String s = EnvYamlUtils.toString(envYaml);

            EnvYaml envYaml1 = EnvYamlUtils.toEnvYaml(s);
            assertEquals(envYaml, envYaml1);
        }
    }
}
