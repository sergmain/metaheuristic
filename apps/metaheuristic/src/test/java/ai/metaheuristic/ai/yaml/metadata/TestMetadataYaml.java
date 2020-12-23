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
package ai.metaheuristic.ai.yaml.metadata;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestMetadataYaml {

    @Test
    public void testParsingFile() throws IOException {

        try(InputStream is = TestMetadataYaml.class.getResourceAsStream("/yaml/metadata/metadata.yaml")) {
            Metadata m = MetadataUtils.to(is);
            assertNotNull(m);
            assertNotNull(m.getMetadata());
            assertNotNull(m.getDispatcher());
            assertEquals(1, m.getMetadata().size());
            assertEquals(2, m.getDispatcher().size());
            Set<Map.Entry<String, Metadata.DispatcherInfo>> entry = m.getDispatcher().entrySet();
            Iterator<Map.Entry<String, Metadata.DispatcherInfo>> iterator = entry.iterator();
            Map.Entry<String, Metadata.DispatcherInfo> map = iterator.next();
            Map.Entry<String, Metadata.DispatcherInfo> map1 = iterator.next();

            assertEquals("http://localhost:8080", map.getKey());
            assertEquals("localhost-8080", map.getValue().code);
            assertEquals("15", map.getValue().processorId);

            assertEquals("http://host", map1.getKey());
            assertEquals("host", map1.getValue().code);
            assertNull(map1.getValue().processorId);
        }
    }

    @Test
    public void testParsingEmptyFile() throws IOException {
        try(InputStream is = TestMetadataYaml.class.getResourceAsStream("/yaml/metadata/metadata-empty.yaml")) {
            Metadata m = MetadataUtils.to(is);
            assertNotNull(m);
            assertNotNull(m.metadata);
            assertNotNull(m.dispatcher);
            assertEquals(0, m.getMetadata().size());
            assertEquals(0, m.getDispatcher().size());
        }
    }

    @Test
    public void testParsingZeroFile() throws IOException {
        try(InputStream is = TestMetadataYaml.class.getResourceAsStream("/yaml/metadata/metadata-zero.yaml")) {
            Metadata m = MetadataUtils.to(is);
            assertNotNull(m);
            assertNotNull(m.metadata);
            assertNotNull(m.dispatcher);
            assertEquals(0, m.getMetadata().size());
            assertEquals(0, m.getDispatcher().size());
        }
    }


}
