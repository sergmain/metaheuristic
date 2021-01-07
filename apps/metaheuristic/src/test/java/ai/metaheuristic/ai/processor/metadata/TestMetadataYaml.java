/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYamlUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestMetadataYaml {

    @Test
    public void testParsingFile() throws IOException {
        String yaml = IOUtils.resourceToString("/metadata/metadata.yaml", StandardCharsets.UTF_8);
        assertNotNull(yaml);
        MetadataParamsYaml m = MetadataParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        assertNotNull(m);
        assertNotNull(m.getMetadata());
        assertNotNull(m.getProcessors());
        assertEquals(1, m.getMetadata().size());
        assertEquals(2, m.getProcessors().size());
        Set<Map.Entry<String, MetadataParamsYaml.Processor>> entry = m.getProcessors().entrySet();
        Iterator<Map.Entry<String, MetadataParamsYaml.Processor>> iterator = entry.iterator();
        Map.Entry<String, MetadataParamsYaml.Processor> map = iterator.next();
        Map.Entry<String, MetadataParamsYaml.Processor> map1 = iterator.next();

        assertEquals(Consts.DEFAULT_PROCESSOR_CODE, map.getKey());
        MetadataParamsYaml.Processor p = map.getValue();
        assertTrue(p.states.containsKey("http://localhost:8080"));
        assertEquals("localhost-8080", p.states.get("http://localhost:8080").dispatcherCode);
        assertEquals("15", p.states.get("http://localhost:8080").processorId);


        MetadataParamsYaml.Processor p1 = map1.getValue();
        assertTrue(p1.states.containsKey("http://host"));
        assertEquals("host", p.states.get("http://host").dispatcherCode);
        assertNull(p.states.get("http://host").processorId);
    }

    @Test
    public void testParsingEmptyFile() throws IOException {
        String yaml = IOUtils.resourceToString("/metadata/metadata-empty.yaml", StandardCharsets.UTF_8);
        assertNotNull(yaml);
        MetadataParamsYaml m = MetadataParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        assertNotNull(m);
        assertNotNull(m.metadata);
        assertNotNull(m.processors);
        assertEquals(0, m.metadata.size());
        assertEquals(0, m.processors.size());
    }

    @Test
    public void testParsingZeroFile() throws IOException {
        String yaml = IOUtils.resourceToString("/metadata/metadata-zero.yaml", StandardCharsets.UTF_8);
        assertNotNull(yaml);
        MetadataParamsYaml m = MetadataParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        assertNotNull(m);
        assertNotNull(m.metadata);
        assertNotNull(m.processors);
        assertEquals(0, m.metadata.size());
        assertEquals(0, m.processors.size());
    }


}
