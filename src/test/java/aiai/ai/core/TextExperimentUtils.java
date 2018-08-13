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

import aiai.ai.launchpad.experiment.ExperimentUtils;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;

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

        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 1, 10L), ExperimentUtils.getNumberOfVariants("10") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 1, 10L), ExperimentUtils.getNumberOfVariants(" 10 ") );

        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 3, new Long[]{10L,15L,37L}), ExperimentUtils.getNumberOfVariants(" [ 10, 15, 37] ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 2, new Long[]{10L,15L}), ExperimentUtils.getNumberOfVariants(" [ 10, 15, ] ") );

        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 2, new Long[]{10L,15L}), ExperimentUtils.getNumberOfVariants(" Range ( 10, 20, 5) ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 3, new Long[]{10L,15L, 20L}), ExperimentUtils.getNumberOfVariants(" Range ( 10, 21, 5) ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 1, new Long[]{10L}), ExperimentUtils.getNumberOfVariants(" Range ( 10, 15, 5) ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 2, new Long[]{10L,15L}), ExperimentUtils.getNumberOfVariants(" Range ( 10, 16, 5) ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 1, new Long[]{10L}), ExperimentUtils.getNumberOfVariants(" Range ( 10, 14, 5) ") );
        assertEquals( ExperimentUtils.NumberOfVariants.instanceOf(true, null, 0), ExperimentUtils.getNumberOfVariants(" Range ( 10, 10, 5) ") );

        assertFalse(ExperimentUtils.getNumberOfVariants(" Range ( 10, 14, ) ").isStatus());
        assertFalse(ExperimentUtils.getNumberOfVariants(" Range ( , 14, 10) ").isStatus());
        assertFalse(ExperimentUtils.getNumberOfVariants(" Range ( abc, 15, 3) ").isStatus());
        assertFalse(ExperimentUtils.getNumberOfVariants(" Range ( 10, abc, 3) ").isStatus());
        assertFalse(ExperimentUtils.getNumberOfVariants(" Range ( 10, 15, abc) ").isStatus());
        assertFalse(ExperimentUtils.getNumberOfVariants(" abc ").isStatus());
        assertFalse(ExperimentUtils.getNumberOfVariants(" [ 10, abc, 37] ").isStatus());

    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testMetaProducer() {
        List<Map<String, Long>> allPaths;
        Map<String, String> map = new LinkedHashMap<>();

        map.put("key1", null);
        thrown.expect(IllegalStateException.class);
        allPaths = ExperimentUtils.getAllPaths(map);

        map.clear();
        map.put("key1", "");
        thrown.expect(IllegalStateException.class);
        allPaths = ExperimentUtils.getAllPaths(map);


        map.clear();
        map.put("key1", "10");
        allPaths = ExperimentUtils.getAllPaths(map);

        assertEquals(1, allPaths.size());
        Map<String, Long> path = allPaths.get(0);
        assertEquals(1, path.size());
        assertEquals(10L, (long)path.get("key1"));

        map.clear();
        map.put("key3", "30");
        map.put("key4", "40");
        map.put("key2", "20");
        map.put("key1", "10");

        allPaths = ExperimentUtils.getAllPaths(map);

        assertEquals(1, allPaths.size());
        Map<String, Long> path1 = allPaths.get(0);
        assertEquals(4, path1.size());

        String mapYaml;
        Scanner scanner;

        mapYaml = toYaml(path1);
        System.out.println(mapYaml);

        scanner = new Scanner(mapYaml);
        scanner.useDelimiter("[\n]");

        Assert.assertEquals("key1: 10", scanner.next());
        Assert.assertEquals("key2: 20", scanner.next());
        Assert.assertEquals("key3: 30", scanner.next());
        Assert.assertEquals("key4: 40", scanner.next());

    }


    private String toSampleYaml(String[][] values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String[] v : values) {
            map.put(v[0], v[1]);
        }
        List<Map<String, Long>> allPaths = ExperimentUtils.getAllPaths(map);

        return toYaml(allPaths.get(0));
    }

    @Test
    public void testMetaProducer_1() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key3", "30");
        map.put("key4", "40");
        map.put("key2", "[2,4]");
        map.put("key1", "[11, 13]");

        List<Map<String, Long>> allPaths = ExperimentUtils.getAllPaths(map);

        List<String> allYamls = new ArrayList<>();
        String currYaml;
        for (Map<String, Long> allPath : allPaths) {
            currYaml = toYaml(allPath);
            allYamls.add(currYaml);
        }
        assertEquals(4, allYamls.size());

        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","2"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","4"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","13"}, {"key2","2"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","13"}, {"key2","4"}, {"key3","30"}, {"key4","40"}, }) ) );
    }

    @Test
    public void testMetaProducer_2() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key3", "30");
        map.put("key4", "40");
        map.put("key2", "[2,4]");
        map.put("key1", "[11]");

        List<Map<String, Long>> allPaths = ExperimentUtils.getAllPaths(map);

        List<String> allYamls = new ArrayList<>();
        String currYaml;
        for (Map<String, Long> allPath : allPaths) {
            currYaml = toYaml(allPath);
            allYamls.add(currYaml);
        }
        assertEquals(2, allYamls.size());

        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","2"}, {"key3","30"}, {"key4","40"}, }) ) );
        assertTrue(allYamls.contains(toSampleYaml(new String[][]{{"key1","11"}, {"key2","4"}, {"key3","30"}, {"key4","40"}, }) ) );
    }

    private String toYaml(Map<String, Long> producedMap) {
        if (producedMap==null) {
            return null;
        }
        String mapYaml;
        mapYaml = yaml.dump(producedMap.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new))
        );
        return mapYaml;
    }

}
