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

package ai.metaheuristic.ai.yaml.series;

import ai.metaheuristic.ai.utils.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 4/3/2021
 * Time: 8:36 PM
 */
public class TestSeriesParamsYamlKey {

/*
    @Test
    public void test() {
        SeriesParamsYaml.Key key1;
        {
            key1 = getKey(Map.of(
                    "key10", "v10",
                    "key1", "v1",
                    "key4", "v4",
                    "key7", "v7",
                    "key5", "v5"
            ), List.of("var10", "var1", "var6", "var3"));

            List<String> list11 = new ArrayList<>(key1.hyperParams.keySet());

            assertEquals(5, list11.size());
            assertEquals("key1", list11.get(0));
            assertEquals("key10", list11.get(1));
            assertEquals("key4", list11.get(2));
            assertEquals("key5", list11.get(3));
            assertEquals("key7", list11.get(4));

            List<String> list12 = new ArrayList<>(key1.variables);

            assertEquals(4, list12.size());
            assertEquals("var1", list12.get(0));
            assertEquals("var10", list12.get(1));
            assertEquals("var3", list12.get(2));
            assertEquals("var6", list12.get(3));
        }

        SeriesParamsYaml.Key key2;
        {
            key2 = getKey(Map.of(
                    "key10", "v10",
                    "key5", "v5",
                    "key4", "v4",
                    "key1", "v1",
                    "key7", "v7"
            ), List.of("var3", "var6", "var1", "var10"));

            List<String> list21 = new ArrayList<>(key2.hyperParams.keySet());

            assertEquals(5, list21.size());
            assertEquals("key1", list21.get(0));
            assertEquals("key10", list21.get(1));
            assertEquals("key4", list21.get(2));
            assertEquals("key5", list21.get(3));
            assertEquals("key7", list21.get(4));

            List<String> list22 = new ArrayList<>(key2.variables);

            assertEquals(4, list22.size());
            assertEquals("var1", list22.get(0));
            assertEquals("var10", list22.get(1));
            assertEquals("var3", list22.get(2));
            assertEquals("var6", list22.get(3));

        }
        assertEquals(key1, key2);

        SeriesParamsYaml.Key key3;
        {
            key3 = getKey(Map.of(
                    "key10", "v10",
                    "key5", "v5",
                    "key4", "v4",
                    "key1", "v1",
                    "key7", "v7"
            ), List.of("var3", "var6", "var10"));
        }
        SeriesParamsYaml.Key key4;
        {
            final Map<String, String> map = Map.of(
                    "key10", "v10",
                    "key5", "v5",
                    "key1", "v1",
                    "key7", "v7"
            );
            final List<String> list = List.of("var3", "var6", "var1", "var10");
            key4 = getKey(map, list);
        }

        assertNotEquals(key1, key3);
        assertNotEquals(key1, key4);
        assertNotEquals(key2, key3);
        assertNotEquals(key2, key4);
        assertNotEquals(key3, key4);
    }
*/

/*
    private static SeriesParamsYaml.Key getKey(Map<String, String> map, List<String> list) {
        SeriesParamsYaml.Key key4;
        key4 = new SeriesParamsYaml.Key();
        key4.hyperParams.putAll(map);
        key4.variables.addAll(list);
        return key4;
    }
*/

    @Test
    public void test1() throws JsonProcessingException {
        SeriesParamsYaml params = new SeriesParamsYaml();

/*
        SeriesParamsYaml.Key key1 = getKey(Map.of(
                "key10", "v10",
                "key1", "v1",
                "key4", "v4",
                "key7", "v7",
                "key5", "v5"
        ), List.of("var10", "var1", "var6", "var3"));

*/
        SeriesParamsYaml.ExperimentPart part = new SeriesParamsYaml.ExperimentPart();
        part.taskContextId = "1#1";
        part.hyperParams.putAll(Map.of("k1","v1","k2","v2","k3","v3","k4","v4"));
        part.variables.addAll(List.of("var10", "var1", "var6", "var3"));

        params.parts.add(part);

/*
        SeriesParamsYaml.Key key2 = getKey(Map.of(
                "key210", "v210",
                "key21", "v21",
                "key24", "v24",
                "key27", "v27",
                "key25", "v25"
        ), List.of("var210", "var21", "var26", "var23"));
*/

        SeriesParamsYaml.ExperimentPart part2 = new SeriesParamsYaml.ExperimentPart();
        part2.taskContextId = "1#2";
        part2.hyperParams.putAll(Map.of("k21","v21","k22","v22","k23","v23","k24","v24"));
        part.variables.addAll(List.of("var210", "var21", "var26", "var23"));

        params.parts.add(part2);

        String yaml = SeriesParamsYamlUtils.BASE_YAML_UTILS.toString(params);
        System.out.println(yaml);
        System.out.println("\n\n\n");

        String json = JsonUtils.getMapper().writeValueAsString(params);
        System.out.println(json);

        SeriesParamsYaml params21 = JsonUtils.getMapper().readValue(json, SeriesParamsYaml.class);
//        assertTrue(params21.parts.containsKey(key1));
//        assertTrue(params21.parts.containsKey(key2));




        SeriesParamsYaml params1 = SeriesParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
//        assertTrue(params1.parts.containsKey(key1));
//        assertTrue(params1.parts.containsKey(key2));

        System.out.println(yaml);


    }
}
