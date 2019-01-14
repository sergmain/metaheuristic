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
package aiai.ai.core;

import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.yaml.hyper_params.HyperParams;
import aiai.ai.yaml.hyper_params.HyperParamsUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

import static org.junit.Assert.*;

public class TextExperimentUtils {

    private Yaml yaml;
    @Before
    public void init() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        yaml = new Yaml(options);
    }

    @Test
    public void testCreatePath() {
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 0), ExperimentUtils.getNumberOfVariants("") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 0), ExperimentUtils.getNumberOfVariants(" ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 0), ExperimentUtils.getNumberOfVariants(null) );

        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 1, "10"), ExperimentUtils.getNumberOfVariants("10") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 1, "10"), ExperimentUtils.getNumberOfVariants(" 10 ") );

        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 3, new String[]{"10","15","37"}), ExperimentUtils.getNumberOfVariants(" [ 10, 15, 37] ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 2, new String[]{"10","15"}), ExperimentUtils.getNumberOfVariants(" [ 10, 15, ] ") );

        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 2, new String[]{"10","15"}), ExperimentUtils.getNumberOfVariants(" Range ( 10, 20, 5) ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 3, new String[]{"10","15", "20"}), ExperimentUtils.getNumberOfVariants(" Range ( 10, 21, 5) ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 1, new String[]{"10"}), ExperimentUtils.getNumberOfVariants(" Range ( 10, 15, 5) ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 2, new String[]{"10","15"}), ExperimentUtils.getNumberOfVariants(" Range ( 10, 16, 5) ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 1, new String[]{"10"}), ExperimentUtils.getNumberOfVariants(" Range ( 10, 14, 5) ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 0), ExperimentUtils.getNumberOfVariants(" Range ( 10, 10, 5) ") );

        assertFalse(ExperimentUtils.getNumberOfVariants(" Range ( 10, 14, ) ").isStatus());
        assertFalse(ExperimentUtils.getNumberOfVariants(" Range ( , 14, 10) ").isStatus());
        assertFalse(ExperimentUtils.getNumberOfVariants(" Range ( abc, 15, 3) ").isStatus());
        assertFalse(ExperimentUtils.getNumberOfVariants(" Range ( 10, abc, 3) ").isStatus());
        assertFalse(ExperimentUtils.getNumberOfVariants(" Range ( 10, 15, abc) ").isStatus());

        assertTrue(ExperimentUtils.getNumberOfVariants(" [ 10, abc, 37] ").isStatus());
        assertTrue(ExperimentUtils.getNumberOfVariants(" abc ").isStatus());

    }

    @Test
    public void testVariantsWithSpaces() {
        ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(" ['#in_top_draw_digit, accuracy', accuracy] ");
        assertEquals( ExperimentUtils.NumberOfVariants
                .instanceOf(true, null, 2, Arrays.asList("#in_top_draw_digit, accuracy", "accuracy")), variants);
        assertTrue(variants.isStatus());
        variants = ExperimentUtils.getNumberOfVariants(" ['#in_top_draw_digit, accuracy', 'accuracy'] ");
        assertEquals( ExperimentUtils.NumberOfVariants
                .instanceOf(true, null, 2, Arrays.asList("#in_top_draw_digit, accuracy", "accuracy")), variants);
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

        List<HyperParams> params = ExperimentUtils.getAllHyperParams(map);
        assertEquals(4, params.size());
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testMetaProducer() {
        List<HyperParams> allPaths;
        Map<String, String> map = new LinkedHashMap<>();

        map.put("key1", null);
        allPaths = ExperimentUtils.getAllHyperParams(map);
        assertTrue(allPaths.isEmpty());

        map.clear();
        map.put("key1", "");
        allPaths = ExperimentUtils.getAllHyperParams(map);
        assertTrue(allPaths.isEmpty());


        map.clear();
        map.put("key1", "10");
        allPaths = ExperimentUtils.getAllHyperParams(map);

        assertEquals(1, allPaths.size());
        HyperParams path = allPaths.get(0);
        assertEquals(1, path.params.size());
        assertEquals("10", path.params.get("key1"));

        map.clear();
        map.put("key3", "30");
        map.put("key4", "40");
        map.put("key2", "20");
        map.put("key1", "10");

        allPaths = ExperimentUtils.getAllHyperParams(map);

        assertEquals(1, allPaths.size());
        HyperParams path1 = allPaths.get(0);
        assertEquals(4, path1.params.size());

        String mapYaml;
        Scanner scanner;

        mapYaml = HyperParamsUtils.toYaml(path1);
        System.out.println(mapYaml);

        scanner = new Scanner(mapYaml);
        scanner.useDelimiter("[\n]");

        Assert.assertEquals("key1: '10'", scanner.next());
        Assert.assertEquals("key2: '20'", scanner.next());
        Assert.assertEquals("key3: '30'", scanner.next());
        Assert.assertEquals("key4: '40'", scanner.next());

    }

    @Test
    public void testHyperParams() {
        HyperParams hp = new HyperParams(new LinkedHashMap<>(), "abc");

        HyperParams hp1 = hp.asClone();

        hp1.path += "123";

        assertEquals("abc", hp.path);
        assertEquals("abc123", hp1.path);

    }

    private String toSampleYaml(String[][] values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String[] v : values) {
            map.put(v[0], v[1]);
        }
        List<HyperParams> allPaths = ExperimentUtils.getAllHyperParams(map);

        return HyperParamsUtils.toYaml(allPaths.get(0));
    }

    @Test
    public void testGetAllHyperParams_1() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key3", "30");
        map.put("key4", "40");
        map.put("key2", "[2,4]");
        map.put("key1", "[11, 13]");

        List<HyperParams> allPaths = ExperimentUtils.getAllHyperParams(map);

        List<String> allYamls = new ArrayList<>();
        String currYaml;
        for (HyperParams hyperParams : allPaths) {
            currYaml = HyperParamsUtils.toYaml(hyperParams);
            allYamls.add(currYaml);
        }
        assertEquals(4, allYamls.size());

        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","2"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","4"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","13"}, {"key2","2"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","13"}, {"key2","4"}, {"key3","30"}, {"key4","40"}, }) ) );
    }

    @Test
    public void testGetAllHyperParams_2() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key3", "30");
        map.put("key4", "40");
        map.put("key2", "[2,4]");
        map.put("key1", "[11]");

        List<HyperParams> allPaths = ExperimentUtils.getAllHyperParams(map);

        List<String> allYamls = new ArrayList<>();
        String currYaml;
        for (HyperParams hyperParams : allPaths) {
            currYaml = HyperParamsUtils.toYaml(hyperParams);
            allYamls.add(currYaml);
        }
        assertEquals(2, allYamls.size());

        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","2"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","4"}, {"key3","30"}, {"key4","40"}, }) ) );
    }

    @Test
    public void testGetAllHyperParams_3() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key1", "[11]");
        map.put("key2", "[LSTM, GRU]");

        List<HyperParams> allPaths = ExperimentUtils.getAllHyperParams(map);

        List<String> allYamls = new ArrayList<>();
        String currYaml;
        for (HyperParams hyperParams : allPaths) {
            currYaml = HyperParamsUtils.toYaml(hyperParams);
            allYamls.add(currYaml);
        }
        assertEquals(2, allYamls.size());

        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","LSTM"} }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","GRU"} }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","GRU"} }) ) );

        assertTrue(getYaml(allYamls, toSampleYaml(new String[][]{{"key1","11"}, {"key2","GRU"} }) ).contains("GRU") );
        assertTrue(getYaml(allYamls, toSampleYaml(new String[][]{{"key1","11"}, {"key2","LSTM"} }) ).contains("LSTM") );

    }

    private static String getYaml( List<String> allYamls, String yaml) {
        for (String allYaml : allYamls) {
            if (allYaml.equals(yaml)) {
                return yaml;
            }
        }
        throw new IllegalStateException("not found " + yaml);
    }

    @Test
    public void testEmptyList() {
        Map<String, String> map = ExperimentService.toMap(new ArrayList<>(), 1337, "10");
        List<HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);
        assertEquals(1, allHyperParams.size());

        List<String> allYamls = new ArrayList<>();
        String currYaml;
        for (HyperParams hyperParams : allHyperParams) {
            currYaml = HyperParamsUtils.toYaml(hyperParams);
            allYamls.add(currYaml);
        }
        assertEquals(1, allYamls.size());

        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"seed","1337"}, {"epoch","10"}}) ) );
    }

    @Test
    public void testEmptyList_1() {
        Map<String, String> map = ExperimentService.toMap(new ArrayList<>(), 1337, "[7, 11, 13]");
        List<HyperParams> allHyperParams = ExperimentUtils.getAllHyperParams(map);
        assertEquals(3, allHyperParams.size());
        List<String> allYamls = new ArrayList<>();

        String currYaml;
        for (HyperParams hyperParams : allHyperParams) {
            currYaml = HyperParamsUtils.toYaml(hyperParams);
            allYamls.add(currYaml);
        }
        assertEquals(3, allYamls.size());

        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"seed","1337"}, {"epoch","7"}}) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"seed","1337"}, {"epoch","11"}}) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"seed","1337"}, {"epoch","13"}}) ) );
    }

    @Data
    @AllArgsConstructor
    private static class TextToYaml {
        public String aaa;

        public String bbb;
    }

    @Test
    public void testExcluedeField() {
        // Right now there isn't simple way to exclude field from marshaling.
        // rewrite this test when excluding field will be implemented
        String s = yaml.dump( new TextToYaml("AAA-valuse", "BBB-value") );
        assertTrue(s.contains("BBB-value"));
    }

}
