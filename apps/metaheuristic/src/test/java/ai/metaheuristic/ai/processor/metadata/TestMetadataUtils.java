/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.metadata;

import ai.metaheuristic.ai.processor.utils.MetadataUtils;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Serge
 * Date: 5/1/2022
 * Time: 9:17 PM
 */
public class TestMetadataUtils {

    @Test
    public void testFixProcessorCodes_1() {
        final List<String> codes = List.of("aaa1", "aaa2");
        final LinkedHashMap<String, MetadataParamsYaml.ProcessorSession> map = new LinkedHashMap<>();
        final MetadataParamsYaml.ProcessorSession ps1 = new MetadataParamsYaml.ProcessorSession();
        ps1.cores.putAll(Map.of("aaa1", "qqq", "aaa2", "wwww"));
        map.put("123", ps1);

        MetadataUtils.fixProcessorCodes(codes, map);

        assertEquals(2, ps1.cores.size());
        assertEquals("qqq", ps1.cores.get("aaa1"));
        assertEquals("www", ps1.cores.get("aaa2"));

    }

    @Test
    public void testFixProcessorCodes_2() {
        final List<String> codes = List.of("aaa1");
        final LinkedHashMap<String, MetadataParamsYaml.ProcessorSession> map = new LinkedHashMap<>();
        final MetadataParamsYaml.ProcessorSession ps1 = new MetadataParamsYaml.ProcessorSession();
        ps1.cores.putAll(Map.of("aaa1", "qqq", "aaa2", "wwww"));
        map.put("123", ps1);

        MetadataUtils.fixProcessorCodes(codes, map);

        assertEquals(1, ps1.cores.size());
        assertEquals("qqq", ps1.cores.get("aaa1"));
    }

    @Test
    public void testFixProcessorCodes_3() {
        final List<String> codes = List.of("aaa1", "aaa2", "aaa3");
        final LinkedHashMap<String, MetadataParamsYaml.ProcessorSession> map = new LinkedHashMap<>();
        final MetadataParamsYaml.ProcessorSession ps1 = new MetadataParamsYaml.ProcessorSession();
        ps1.cores.putAll(Map.of("aaa1", "qqq", "aaa2", "wwww"));
        map.put("123", ps1);

        MetadataUtils.fixProcessorCodes(codes, map);

        assertEquals(3, ps1.cores.size());
        assertEquals("qqq", ps1.cores.get("aaa1"));
        assertEquals("www", ps1.cores.get("aaa2"));
        assertEquals(null, ps1.cores.get("aaa3"));
    }

    @Test
    public void testFixProcessorCodes_4() {
        final List<String> codes = List.of("aaa3", "aaa4");
        final LinkedHashMap<String, MetadataParamsYaml.ProcessorSession> map = new LinkedHashMap<>();
        final MetadataParamsYaml.ProcessorSession ps1 = new MetadataParamsYaml.ProcessorSession();
        ps1.cores.putAll(Map.of("aaa1", "qqq", "aaa2", "wwww"));
        map.put("123", ps1);

        MetadataUtils.fixProcessorCodes(codes, map);

        assertEquals(2, ps1.cores.size());
        assertEquals(null, ps1.cores.get("aaa3"));
        assertEquals(null, ps1.cores.get("aaa4"));
    }
}
