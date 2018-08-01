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

import lombok.Data;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

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

    @Test
    public void loadYmlAsMapFromStream() throws IOException {

        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/simple.yaml")) {

            Yaml yaml = new Yaml();

            Map<String, Map<String, List<String>>> yamlParsers = yaml.load(is);
            Assert.assertThat(yamlParsers.keySet(), CoreMatchers.hasItems("dataset"));
            Assert.assertThat(yamlParsers.get("dataset").keySet(), CoreMatchers.hasItems("input", "labels", "output"));

            List<String> files = yamlParsers.get("dataset").get("input");
            Assert.assertEquals(3, files.size());

            List<String> outputs = yamlParsers.get("dataset").get("output");
            Assert.assertEquals(1, outputs.size());
        }
    }

    @Test
    public void loadSnippetsFromYaml() throws IOException {

        try(InputStream is = TestYamlParser.class.getResourceAsStream("/snippets/snippet-01/snippets.yaml")) {

            Yaml yaml = new Yaml();

            List<Map<String, Map<String, String>>> cfg = yaml.load(is);
            Assert.assertNotNull(cfg);
            Assert.assertEquals(2, cfg.size());
        }
    }

}
