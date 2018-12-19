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
package aiai.ai.yaml.metadata;

import aiai.ai.station.StationConsts;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestMetadataYaml {

    @Test
    public void testParsingFile() throws IOException {

        try(InputStream is = TestMetadataYaml.class.getResourceAsStream("/yaml/metadata/metadata.yaml")) {
            Metadata m = MetadataUtils.to(is);
            assertNotNull(m);
            assertNotNull(m.getMetadata());
            assertNotNull(m.getLaunchpad());
            assertEquals(1, m.getMetadata().size());
            assertEquals(1, m.getLaunchpad().size());
            assertEquals("11", m.metadata.get(StationConsts.STATION_ID));
            Set<Map.Entry<String, Metadata.LaunchpadCode>> entry = m.getLaunchpad().entrySet();
            Map.Entry<String, Metadata.LaunchpadCode> map = entry.iterator().next();

            assertEquals("http://localhost:8080", map.getKey());
            assertEquals("localhost-8080", map.getValue().value);
        }
    }
}
