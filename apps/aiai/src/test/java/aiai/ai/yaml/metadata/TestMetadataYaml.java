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
package aiai.ai.yaml.metadata;

import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.metadata.MetadataUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class TestMetadataYaml {

    @Test
    public void testParsingFile() throws IOException {

        try(InputStream is = TestMetadataYaml.class.getResourceAsStream("/yaml/metadata/metadata.yaml")) {
            Metadata m = MetadataUtils.to(is);
            assertNotNull(m);
            assertNotNull(m.getMetadata());
            assertNotNull(m.getLaunchpad());
            assertEquals(1, m.getMetadata().size());
            assertEquals(2, m.getLaunchpad().size());
            Set<Map.Entry<String, Metadata.LaunchpadInfo>> entry = m.getLaunchpad().entrySet();
            Iterator<Map.Entry<String, Metadata.LaunchpadInfo>> iterator = entry.iterator();
            Map.Entry<String, Metadata.LaunchpadInfo> map = iterator.next();
            Map.Entry<String, Metadata.LaunchpadInfo> map1 = iterator.next();

            assertEquals("http://localhost:8080", map.getKey());
            assertEquals("localhost-8080", map.getValue().code);
            assertEquals("15", map.getValue().stationId);

            assertEquals("http://host", map1.getKey());
            assertEquals("host", map1.getValue().code);
            assertNull(map1.getValue().stationId);
        }
    }
}
