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

import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.launchpad.snippet.SnippetsConfig;
import lombok.Data;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class TestYamlParser {

    @Test
    public void loadYmlAsMapFromString() {

        String yamlAsString = "{JYaml: Original Java Implementation, "
                + "JvYaml: Java port of RbYaml, SnakeYAML: Java 5 / YAML 1.1, "
                + "YamlBeans: To/from JavaBeans}";

        Yaml yaml = new Yaml();

        Map<String, String> yamlParsers = yaml.load(yamlAsString);

        Assert.assertThat(yamlParsers.keySet(), CoreMatchers.hasItems("JYaml", "JvYaml", "YamlBeans", "SnakeYAML"));
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

    @Test
    public void loadYmlAsMapFromStream() throws IOException {

        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/simple.yaml")) {

            Yaml yaml = new Yaml(new Constructor(SampleYamlConfig.class));

            SampleYamlConfig config = yaml.load(is);
            Assert.assertNotNull(config.dataset);
            Assert.assertNotNull(config.dataset.input);
            Assert.assertNotNull(config.dataset.labels);
            Assert.assertNotNull(config.dataset.output);

            Assert.assertEquals(3, config.dataset.input.size());
            Assert.assertEquals(2, config.dataset.labels.size());
        }
    }

    @Test
    public void loadSnippetsFromYaml() throws IOException {

        try(InputStream is = TestYamlParser.class.getResourceAsStream("/snippets/snippet-01/snippets.yaml")) {

            SnippetsConfig config = SnippetsConfig.loadSnippetYaml(is);

            Assert.assertNotNull(config);
            Assert.assertNotNull(config.snippets);
            Assert.assertEquals(2, config.snippets.size());
/*
        ns: aiai.fit.default.snippet
        type: fit
        file: fit-model.py
        version: 1.0
*/
            SnippetsConfig.SnippetConfig sc;
            sc = config.snippets.get(0);
            Assert.assertEquals("aiai.fit.default.snippet", sc.name);
            Assert.assertEquals(SnippetType.fit, sc.type);
            Assert.assertEquals("fit-model.py", sc.file);
            Assert.assertEquals("1.0", sc.version);

/*
    - name: aiai.predict.default.snippet
      version: 1.0
      type: predict
      file: predict-model.py
*/
            sc = config.snippets.get(1);
            Assert.assertEquals("aiai.predict.default.snippet", sc.name);
            Assert.assertEquals(SnippetType.predict, sc.type);
            Assert.assertEquals("predict-model.py", sc.file);
            Assert.assertEquals("1.0", sc.version);
        }
    }



}
