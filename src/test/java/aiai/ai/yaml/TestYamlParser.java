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

import aiai.ai.yaml.snippet.SnippetType;
import aiai.ai.yaml.snippet.SnippetsConfig;
import aiai.ai.yaml.snippet.SnippetsConfigUtils;
import lombok.Data;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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

    @Data
    public static class DatasetConfig {
        List<String> input;
        List<String> labels;
        String output;
    }

    @Data
    public static class SampleYamlConfig {
        DatasetConfig dataset;
/*
dataset:
  input:
    - file_01.txt
    - file_02.txt
    - file_03.txt
  labels:
    - file_04.txt
    - file_05.txt
  output: dataset_06.txt
*/

    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void loadSnippetYamlWithError_01() throws IOException {

        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/snippets-test-error.yaml")) {

            SnippetsConfig config = SnippetsConfigUtils.loadSnippetYaml(is);
            assertNotNull(config);
            assertNotNull(config.getSnippets());
            assertEquals(1, config.getSnippets().size());
            SnippetsConfig.SnippetConfig snippet = config.getSnippets().get(0);
            SnippetsConfig.SnippetConfigStatus status = snippet.verify();
            assertFalse(status.isOk);
        }
    }

    @Test
    public void loadYmlAsMapFromStream() throws IOException {

        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/simple.yaml")) {

            Yaml yaml = new Yaml(new Constructor(SampleYamlConfig.class));

            SampleYamlConfig config = yaml.load(is);
            assertNotNull(config.dataset);
            assertNotNull(config.dataset.input);
            assertNotNull(config.dataset.labels);
            assertNotNull(config.dataset.output);

            assertEquals(3, config.dataset.input.size());
            assertEquals(2, config.dataset.labels.size());
        }
    }

    @Test
    public void loadSnippetsFromYaml() throws IOException {

        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/snippets-test.yaml")) {

            SnippetsConfig config = SnippetsConfigUtils.loadSnippetYaml(is);

            assertNotNull(config);
            assertNotNull(config.snippets);
            assertEquals(3, config.snippets.size());

            SnippetsConfig.SnippetConfig sc;
            sc = config.snippets.get(0);
            assertEquals("aiai.fit.default.snippet", sc.name);
            assertEquals(SnippetType.fit, sc.type);
            assertEquals("fit-model.py", sc.file);
            assertEquals("1.0-SNAPSHOT", sc.version);
            assertEquals("python-3", sc.env);

            sc = config.snippets.get(1);
            assertEquals("aiai.predict.default.snippet", sc.name);
            assertEquals(SnippetType.predict, sc.type);
            assertEquals("predict-model.py", sc.file);
            assertEquals("1.0-SNAPSHOT", sc.version);
            assertEquals("python-3", sc.env);
        }
    }



}
