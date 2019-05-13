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

import ai.metaheuristic.api.v1.data.SnippetApiData;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigList;
import ai.metaheuristic.commons.yaml.snippet.SnippetConfigListUtils;
import ai.metaheuristic.commons.yaml.snippet.SnippetUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

public class TestYamlParser {

    public static Yaml init(Class<?> clazz) {
        return initWithTags(clazz, new Class[]{clazz}, null);
    }

    public static Yaml initWithTags(Class<?> clazz, Class<?>[] clazzMap, TypeDescription customTypeDescription) {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Representer representer = new Representer();
        if (clazzMap!=null) {
            for (Class<?> clazzTag : clazzMap) {
                representer.addClassTag(clazzTag, Tag.MAP);
            }
        }

        Constructor constructor = new Constructor(clazz);
        if (customTypeDescription!=null) {
            constructor.addTypeDescription(customTypeDescription);
        }

        //noinspection UnnecessaryLocalVariable
        Yaml yaml = new Yaml(constructor, representer, options);
        return yaml;
    }

    @Test
    public void testUrlAsKey() {
        Map<String, String> mirror = new ConcurrentHashMap<>();
        final String key = "http://localhost:8080";
        final String value = "C:\\repo";
        mirror.put(key, value);

        Yaml yaml = init(Map.class);

        String s = yaml.dump(mirror);
        System.out.println("s = " + s);

        Map<String, String> map = yaml.load(s);

        assertFalse(map.isEmpty());
        assertTrue(map.containsKey(key));
        assertEquals(value, map.get(key));
    }

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

            SnippetConfigList config = SnippetConfigListUtils.to(is);
            assertNotNull(config);
            assertNotNull(config.getSnippets());
            assertEquals(1, config.getSnippets().size());
            SnippetApiData.SnippetConfig snippet = config.getSnippets().get(0);
            SnippetApiData.SnippetConfigStatus status = SnippetUtils.validate(snippet);
            assertFalse(status.isOk);
        }
    }

    @Test
    public void loadSnippetsFromYaml() throws IOException {

        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/snippets-test.yaml")) {

            SnippetConfigList config = SnippetConfigListUtils.to(is);

            assertNotNull(config);
            assertNotNull(config.snippets);
            assertEquals(3, config.snippets.size());

            SnippetApiData.SnippetConfig sc;
            sc = config.snippets.get(0);
            assertEquals("aiai.fit.default.snippet:1.0-SNAPSHOT", sc.code);
            assertEquals(CommonConsts.FIT_TYPE, sc.type);
            assertEquals("fit-model.py", sc.file);
            assertEquals("python-3", sc.env);
            assertEquals("abc1", sc.params);
            assertFalse(sc.metrics);

            sc = config.snippets.get(1);
            assertEquals("aiai.predict.default.snippet:1.0-SNAPSHOT", sc.code);
            assertEquals(CommonConsts.PREDICT_TYPE, sc.type);
            assertEquals("predict-model.py", sc.file);
            assertEquals("python-3", sc.env);
            assertEquals("abc2", sc.params);
            assertTrue(sc.metrics);

            sc = config.snippets.get(2);
            assertEquals("aiai.predict-model-for-test-only.snippet:1.0-SNAPSHOT", sc.code);
            assertEquals(CommonConsts.PREDICT_TYPE, sc.type);
            assertEquals("predict-model-for-test-only.py", sc.file);
            assertEquals("python-3", sc.env);
            assertEquals("abc3", sc.params);
            assertFalse(sc.metrics);
        }
    }
}
