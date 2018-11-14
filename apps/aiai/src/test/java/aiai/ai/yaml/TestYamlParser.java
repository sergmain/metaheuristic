/*
 * AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov
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

import aiai.apps.commons.yaml.snippet.SnippetsConfig;
import aiai.apps.commons.yaml.snippet.SnippetsConfigUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.*;

public class TestYamlParser {

    @Test
    public void loadYmlAsMapFromString() {

        String yamlAsString = "{JYaml: Original Java Implementation, "
                + "JvYaml: Java port of RbYaml, SnakeYAML: Java 5 / YAML 1.1, "
                + "YamlBeans: To/from JavaBeans}";

        Yaml yaml = new Yaml();

        Map<String, String> yamlParsers = yaml.load(yamlAsString);

        assertThat(yamlParsers.keySet(), CoreMatchers.hasItems("JYaml", "JvYaml", "YamlBeans", "SnakeYAML"));
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void loadSnippetYamlWithError_01() throws IOException {

        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/snippets-test-error.yaml")) {

            SnippetsConfig config = SnippetsConfigUtils.to(is);
            assertNotNull(config);
            assertNotNull(config.getSnippets());
            assertEquals(1, config.getSnippets().size());
            SnippetsConfig.SnippetConfig snippet = config.getSnippets().get(0);
            SnippetsConfig.SnippetConfigStatus status = snippet.verify();
            assertFalse(status.isOk);
        }
    }

    @Test
    public void loadSnippetsFromYaml() throws IOException {

        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/snippets-test.yaml")) {

            SnippetsConfig config = SnippetsConfigUtils.to(is);

            assertNotNull(config);
            assertNotNull(config.snippets);
            assertEquals(3, config.snippets.size());

            SnippetsConfig.SnippetConfig sc;
            sc = config.snippets.get(0);
            assertEquals("aiai.fit.default.snippet", sc.name);
            assertEquals("fit", sc.type);
            assertEquals("fit-model.py", sc.file);
            assertEquals("1.0-SNAPSHOT", sc.version);
            assertEquals("python-3", sc.env);
            assertEquals("abc1", sc.params);
            assertFalse(sc.metrics);

            sc = config.snippets.get(1);
            assertEquals("aiai.predict.default.snippet", sc.name);
            assertEquals("predict", sc.type);
            assertEquals("predict-model.py", sc.file);
            assertEquals("1.0-SNAPSHOT", sc.version);
            assertEquals("python-3", sc.env);
            assertEquals("abc2", sc.params);
            assertTrue(sc.metrics);

            sc = config.snippets.get(2);
            assertEquals("aiai.predict-model-for-test-only.snippet", sc.name);
            assertEquals("predict", sc.type);
            assertEquals("predict-model-for-test-only.py", sc.file);
            assertEquals("1.0-SNAPSHOT", sc.version);
            assertEquals("python-3", sc.env);
            assertEquals("abc3", sc.params);
            assertFalse(sc.metrics);
        }
    }



}
