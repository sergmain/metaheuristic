/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.yaml;

import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

public class TestEnvYaml {

    @Test
    public void testEnvYaml() throws IOException {

        EnvYaml envYaml;
        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/env/env.yaml")) {
            envYaml = EnvYamlUtils.to(is);
        }
        assertNotNull(envYaml);
        assertNotNull(envYaml.getEnvs());
        assertEquals(2, envYaml.getEnvs().size());
        assertEquals("python.exe", envYaml.getEnvs().get("python"));
        assertEquals("E:\\Anaconda3\\envs\\python-36\\python.exe", envYaml.getEnvs().get("python-3"));

        assertNotNull(envYaml.getDisk());
        assertEquals(2, envYaml.getDisk().size());
        assertEquals("code-1", envYaml.getDisk().get(0).code);
        assertEquals("C:\\storage\\code-1", envYaml.getDisk().get(0).path);
        assertEquals("code-2", envYaml.getDisk().get(1).code);
        assertEquals("C:\\storage\\code-2", envYaml.getDisk().get(1).path);



        String s = EnvYamlUtils.toString(envYaml);

        EnvYaml envYaml1 = EnvYamlUtils.to(s);
        assertEquals(envYaml, envYaml1);
    }

    @Test
    public void testEnvYaml_1() throws IOException {

        EnvYaml envYaml;
        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/env/env-2-lines.yaml")) {
            envYaml = EnvYamlUtils.to(is);
        }
        assertNotNull(envYaml);
        assertNotNull(envYaml.getEnvs());
        assertEquals(2, envYaml.getEnvs().size());
        assertEquals("simple-app.jar", envYaml.getEnvs().get("simple-app"));
        assertEquals("1st-line 2nd-line", envYaml.getEnvs().get("lines"));

        assertNotNull(envYaml.getDisk());
        assertTrue(envYaml.getDisk().isEmpty());

        String s = EnvYamlUtils.toString(envYaml);

        EnvYaml envYaml1 = EnvYamlUtils.to(s);
        assertEquals(envYaml, envYaml1);
    }
}
