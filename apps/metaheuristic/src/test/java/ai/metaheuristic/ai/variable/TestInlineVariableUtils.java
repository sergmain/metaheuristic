/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
package ai.metaheuristic.ai.variable;

import ai.metaheuristic.ai.dispatcher.variable.InlineVariable;
import ai.metaheuristic.ai.dispatcher.variable.InlineVariableUtils;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestInlineVariableUtils {

    @Test
    public void testCreatePath() {
        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 0), InlineVariableUtils.getNumberOfVariants("") );
        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 0), InlineVariableUtils.getNumberOfVariants(" ") );
        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 0), InlineVariableUtils.getNumberOfVariants(null) );

        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 1, "10"), InlineVariableUtils.getNumberOfVariants("10") );
        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 1, "10"), InlineVariableUtils.getNumberOfVariants(" 10 ") );

        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 3, new String[]{"10","15","37"}), InlineVariableUtils.getNumberOfVariants(" [ 10, 15, 37] ") );
        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 2, new String[]{"10","15"}), InlineVariableUtils.getNumberOfVariants(" [ 10, 15, ] ") );

        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 2, new String[]{"10","15"}), InlineVariableUtils.getNumberOfVariants(" Range ( 10, 20, 5) ") );
        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 3, new String[]{"10","15", "20"}), InlineVariableUtils.getNumberOfVariants(" Range ( 10, 21, 5) ") );
        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 1, new String[]{"10"}), InlineVariableUtils.getNumberOfVariants(" Range ( 10, 15, 5) ") );
        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 2, new String[]{"10","15"}), InlineVariableUtils.getNumberOfVariants(" Range ( 10, 16, 5) ") );
        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 1, new String[]{"10"}), InlineVariableUtils.getNumberOfVariants(" Range ( 10, 14, 5) ") );
        assertEquals( InlineVariableUtils.NumberOfVariants.instanceOf(true, null, 0), InlineVariableUtils.getNumberOfVariants(" Range ( 10, 10, 5) ") );

        assertFalse(InlineVariableUtils.getNumberOfVariants(" Range ( 10, 14, ) ").isStatus());
        assertFalse(InlineVariableUtils.getNumberOfVariants(" Range ( , 14, 10) ").isStatus());
        assertFalse(InlineVariableUtils.getNumberOfVariants(" Range ( abc, 15, 3) ").isStatus());
        assertFalse(InlineVariableUtils.getNumberOfVariants(" Range ( 10, abc, 3) ").isStatus());
        assertFalse(InlineVariableUtils.getNumberOfVariants(" Range ( 10, 15, abc) ").isStatus());

        assertTrue(InlineVariableUtils.getNumberOfVariants(" [ 10, abc, 37] ").isStatus());
        assertTrue(InlineVariableUtils.getNumberOfVariants(" abc ").isStatus());

    }

    @Test
    public void testVariantsWithSpaces() {
        InlineVariableUtils.NumberOfVariants variants =
                InlineVariableUtils.getNumberOfVariants(" ['#in_top_draw_digit, accuracy', accuracy] ");
        assertEquals( InlineVariableUtils.NumberOfVariants
                .instanceOf(true, null, 2, Arrays.asList("#in_top_draw_digit, accuracy", "accuracy")), variants);
        assertTrue(variants.isStatus());

        variants = InlineVariableUtils.getNumberOfVariants(" ['#in_top_draw_digit, accuracy', 'accuracy'] ");
        assertEquals( InlineVariableUtils.NumberOfVariants
                .instanceOf(true, null, 2, Arrays.asList("#in_top_draw_digit, accuracy", "accuracy")), variants);
        assertTrue(variants.isStatus());

        variants = InlineVariableUtils.getNumberOfVariants(" '#in_top_draw_digit' ");
        assertEquals( InlineVariableUtils.NumberOfVariants
                .instanceOf(true, null, 1, Collections.singletonList("#in_top_draw_digit")), variants);
        assertTrue(variants.isStatus());

        variants = InlineVariableUtils.getNumberOfVariants(" #in_top_draw_digit ");
        assertEquals( InlineVariableUtils.NumberOfVariants
                .instanceOf(true, null, 1, Collections.singletonList("#in_top_draw_digit")), variants);
        assertTrue(variants.isStatus());
    }

    @Test
    public void testGetAllHyperParams() {

        Map<String, String> map = new HashMap<>();
        map.put("aaa", "[7, 13]");
        map.put("batches", "[20, 40]");
        map.put("RNN", "[LSTM]");
        map.put("seed", "1337");
        map.put("epoch", "10");

        List<InlineVariable> params = InlineVariableUtils.getAllInlineVariants(map);
        assertEquals(4, params.size());
    }

    @Test
    public void testMetaProducer() {
        List<InlineVariable> allPaths;
        Map<String, String> map = new LinkedHashMap<>();

        map.put("key1", null);
        allPaths = InlineVariableUtils.getAllInlineVariants(map);
        assertTrue(allPaths.isEmpty());

        map.clear();
        map.put("key1", "");
        allPaths = InlineVariableUtils.getAllInlineVariants(map);
        assertTrue(allPaths.isEmpty());


        map.clear();
        map.put("key1", "10");
        allPaths = InlineVariableUtils.getAllInlineVariants(map);

        assertEquals(1, allPaths.size());
        InlineVariable path = allPaths.get(0);
        assertEquals(1, path.params.size());
        assertEquals("10", path.params.get("key1"));

        map.clear();
        map.put("key3", "30");
        map.put("key4", "40");
        map.put("key2", "20");
        map.put("key1", "10");

        allPaths = InlineVariableUtils.getAllInlineVariants(map);

        assertEquals(1, allPaths.size());
        InlineVariable path1 = allPaths.get(0);
        assertEquals(4, path1.params.size());
    }

    @Test
    public void testHyperParams() {
        InlineVariable hp = new InlineVariable(new LinkedHashMap<>(), "abc");

        InlineVariable hp1 = hp.asClone();

        hp1.path += "123";

        assertEquals("abc", hp.path);
        assertEquals("abc123", hp1.path);

    }

/*
    @Nullable
    private String toSampleYaml(String[][] values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String[] v : values) {
            map.put(v[0], v[1]);
        }
        List<InlineVariable> allPaths = InlineVariableUtils.getAllHyperParams(map);

        return HyperParamsUtils.toYaml(allPaths.get(0));
    }
*/

    @Test
    public void testGetAllHyperParams_1() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key3", "30");
        map.put("key4", "40");
        map.put("key2", "[2,4]");
        map.put("key1", "[11, 13]");

        List<InlineVariable> inlines = InlineVariableUtils.getAllInlineVariants(map);
        assertEquals(4, inlines.size());

/*

        List<String> allYamls = new ArrayList<>();
        String currYaml;
        for (InlineVariable inlineVariable : inlines) {
            currYaml = HyperParamsUtils.toYaml(inlineVariable);
            allYamls.add(currYaml);
        }
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","2"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","4"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","13"}, {"key2","2"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","13"}, {"key2","4"}, {"key3","30"}, {"key4","40"}, }) ) );
*/
    }

    @Test
    public void testGetAllHyperParams_2() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key3", "30");
        map.put("key4", "40");
        map.put("key2", "[2,4]");
        map.put("key1", "[11]");

        List<InlineVariable> inlines = InlineVariableUtils.getAllInlineVariants(map);
        assertEquals(2, inlines.size());

/*
        List<String> allYamls = new ArrayList<>();
        String currYaml;
        for (InlineVariable hyperParams : inlines) {
            currYaml = HyperParamsUtils.toYaml(hyperParams);
            allYamls.add(currYaml);
        }

        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","2"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","4"}, {"key3","30"}, {"key4","40"}, }) ) );
*/
    }

    @Test
    public void testGetAllHyperParams_3() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key1", "[11]");
        map.put("key2", "[LSTM, GRU]");

        List<InlineVariable> inlines = InlineVariableUtils.getAllInlineVariants(map);
        assertEquals(2, inlines.size());

/*
        List<String> allYamls = new ArrayList<>();
        String currYaml;
        for (InlineVariable hyperParams : inlines) {
            currYaml = HyperParamsUtils.toYaml(hyperParams);
            allYamls.add(currYaml);
        }

        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","LSTM"} }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","GRU"} }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","GRU"} }) ) );

        assertTrue(getYaml(allYamls, toSampleYaml(new String[][]{{"key1","11"}, {"key2","GRU"} }) ).contains("GRU") );
        assertTrue(getYaml(allYamls, toSampleYaml(new String[][]{{"key1","11"}, {"key2","LSTM"} }) ).contains("LSTM") );
*/

    }

    private static String getYaml( List<String> allYamls, String yaml) {
        for (String allYaml : allYamls) {
            if (allYaml.equals(yaml)) {
                return yaml;
            }
        }
        throw new IllegalStateException("not found " + yaml);
    }

}
