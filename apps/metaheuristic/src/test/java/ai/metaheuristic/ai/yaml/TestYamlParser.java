/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.commons.utils.FunctionCoreUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYamlUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@SuppressWarnings("SameParameterValue")
@Execution(CONCURRENT)
public class TestYamlParser {

    private static Yaml init(Class<?> clazz) {
        return initWithTags(clazz, new Class[]{clazz}, null);
    }

    private static Yaml initWithTags(Class<?> clazz, @Nullable Class<?>[] clazzMap, @Nullable TypeDescription customTypeDescription) {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Representer representer = new Representer(new DumperOptions());
        if (clazzMap!=null) {
            for (Class<?> clazzTag : clazzMap) {
                representer.addClassTag(clazzTag, Tag.MAP);
            }
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(true);
        loaderOptions.setMaxAliasesForCollections(10);
        loaderOptions.setAllowRecursiveKeys(false);

        Constructor constructor = new Constructor(clazz, loaderOptions);
        if (customTypeDescription!=null) {
            constructor.addTypeDescription(customTypeDescription);
        }

        Yaml yaml = new Yaml(constructor, representer, options);
        return yaml;
    }

    @Test
    public void testUrlAsKey() {
        ConcurrentHashMap<String, String> mirror = new ConcurrentHashMap<>();
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
    public void loadFunctionYamlWithError_01() throws IOException {
        assertThrows(CheckIntegrityFailedException.class, ()-> FunctionConfigYamlUtils.UTILS.to(IOUtils.resourceToString("/yaml/functions-test-error.yaml", StandardCharsets.UTF_8)));
    }

    @Disabled("Need to re-write this test")
    @Test
    public void loadFunctionsFromYaml() throws IOException {

        FunctionConfigYaml config = FunctionConfigYamlUtils.UTILS.to(IOUtils.resourceToString("/yaml/functions-test.yaml", StandardCharsets.UTF_8));
        FunctionCoreUtils.validate(config.function);

/*
        assertNotNull(config);
        assertNotNull(config.function);
        assertEquals(3, config.functions.size());

        FunctionConfigYaml.FunctionConfig sc;
        sc = config.functions.get(0);
        assertEquals("aiai.fit.default.function:1.0-SNAPSHOT", sc.code);
        assertEquals(CommonConsts.FIT_TYPE, sc.type);
        assertEquals("fit-model.py", sc.file);
        assertEquals("python-3", sc.env);
        assertEquals("abc1", sc.params);

        sc = config.functions.get(1);
        assertEquals("aiai.predict.default.function:1.0-SNAPSHOT", sc.code);
        assertEquals(CommonConsts.PREDICT_TYPE, sc.type);
        assertEquals("predict-model.py", sc.file);
        assertEquals("python-3", sc.env);
        assertEquals("abc2", sc.params);

        sc = config.functions.get(2);
        assertEquals("aiai.predict-model-for-test-only.function:1.0-SNAPSHOT", sc.code);
        assertEquals(CommonConsts.PREDICT_TYPE, sc.type);
        assertEquals("predict-model-for-test-only.py", sc.file);
        assertEquals("python-3", sc.env);
        assertEquals("abc3", sc.params);
*/
    }
}
