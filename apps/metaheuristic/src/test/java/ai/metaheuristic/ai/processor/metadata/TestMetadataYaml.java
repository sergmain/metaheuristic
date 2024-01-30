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
package ai.metaheuristic.ai.processor.metadata;

import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYaml;
import ai.metaheuristic.ai.yaml.metadata.MetadataParamsYamlUtils;
import ai.metaheuristic.commons.exceptions.UpgradeNotSupportedException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class TestMetadataYaml {

    @Test
    public void testParsingFile() throws IOException {
        String yaml = IOUtils.resourceToString("/metadata/metadata.yaml", StandardCharsets.UTF_8);
        assertNotNull(yaml);
        MetadataParamsYaml m = MetadataParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertNotNull(m);
        assertNotNull(m.getProcessorSessions());
        assertTrue(m.processorSessions.containsKey("http://localhost:8080"));
        assertTrue(m.processorSessions.containsKey("http://host"));

        final MetadataParamsYaml.ProcessorSession ps1 = m.processorSessions.get("http://localhost:8080");
        assertNotNull(ps1);

        assertEquals("localhost-8080", ps1.dispatcherCode);
        assertEquals(15L, ps1.processorId);
        assertEquals("aaa123", ps1.sessionId);

        MetadataParamsYaml.ProcessorSession ps2 = m.processorSessions.get("http://host");
        assertNotNull(ps2);

        assertEquals("host", ps2.dispatcherCode);
        assertNull(ps2.processorId);
        assertNull(ps2.sessionId);
    }

    @Test
    public void testParsingEmptyFile() throws IOException {
        String yaml = IOUtils.resourceToString("/metadata/metadata-empty.yaml", StandardCharsets.UTF_8);
        assertNotNull(yaml);
        MetadataParamsYaml m = MetadataParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        assertNotNull(m);
        assertNotNull(m.processorSessions);
    }

    @Test
    public void testParsingZeroFile() throws IOException {
        String yaml = IOUtils.resourceToString("/metadata/metadata-zero.yaml", StandardCharsets.UTF_8);
        assertNotNull(yaml);
        assertThrows(UpgradeNotSupportedException.class, ()->MetadataParamsYamlUtils.BASE_YAML_UTILS.to(yaml));
    }


}
