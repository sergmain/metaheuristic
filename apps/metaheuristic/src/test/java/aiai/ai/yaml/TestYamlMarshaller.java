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

import org.junit.Assert;
import org.junit.Test;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class TestYamlMarshaller {


    @Test
    public void testMarshallerWithComplexValues() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("key 2", "value 2");
        map.put("key 1", 1.0);
        map.put("key 4", "value 4");
        map.put("key 3", new BigDecimal("12345678987634763924672346987326498.0"));

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Yaml yaml = new Yaml(options);

        String mapYaml;
        Scanner scanner;

        mapYaml = yaml.dump(map);
        System.out.println(mapYaml);

        scanner = new Scanner(mapYaml);
        scanner.useDelimiter("[\n]");

        Assert.assertEquals("key 2: value 2", scanner.next());
        Assert.assertEquals("key 1: 1.0", scanner.next());
        Assert.assertEquals("key 4: value 4", scanner.next());
        Assert.assertEquals("key 3: 12345678987634763924672346987326498.0", scanner.next());


    }


    @Test
    public void testMarshaller() {

        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("key 2", "value 2");
        map.put("key 1", "value 1");
        map.put("key 4", "value 4");
        map.put("key 3", "value 3");

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Yaml yaml = new Yaml(options);

        String mapYaml;
        Scanner scanner;

        mapYaml = yaml.dump(map);
        System.out.println(mapYaml);

        scanner = new Scanner(mapYaml);
        scanner.useDelimiter("[\n]");

        Assert.assertEquals("key 2: value 2", scanner.next());
        Assert.assertEquals("key 1: value 1", scanner.next());
        Assert.assertEquals("key 4: value 4", scanner.next());
        Assert.assertEquals("key 3: value 3", scanner.next());

        mapYaml = yaml.dump(map.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,(oldValue, newValue) -> oldValue, LinkedHashMap::new))
        );
        System.out.println(mapYaml);

        scanner = new Scanner(mapYaml);
        scanner.useDelimiter("[\n]");

        Assert.assertEquals("key 1: value 1", scanner.next());
        Assert.assertEquals("key 2: value 2", scanner.next());
        Assert.assertEquals("key 3: value 3", scanner.next());
        Assert.assertEquals("key 4: value 4", scanner.next());

    }
}
