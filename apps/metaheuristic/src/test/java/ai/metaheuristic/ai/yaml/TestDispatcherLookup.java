/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfigUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

public class TestDispatcherLookup {

    @Test
    public void testParsingYaml() throws IOException {
        try (InputStream is = TestDispatcherLookup.class.getResourceAsStream("/yaml/dispatchers.yaml")) {
            DispatcherLookupConfig ssc = DispatcherLookupConfigUtils.to(is);

            assertEquals(2, ssc.dispatchers.size());

            assertEquals("http://localhost:8080", ssc.dispatchers.get(0).url);
            assertEquals(DispatcherLookupConfig.DispatcherLookupType.direct, ssc.dispatchers.get(0).lookupType);
            assertNull(ssc.dispatchers.get(0).publicKey);
            assertFalse(ssc.dispatchers.get(0).signatureRequired);
            assertFalse(ssc.dispatchers.get(0).disabled);

            assertEquals("https://host", ssc.dispatchers.get(1).url);
            assertEquals(DispatcherLookupConfig.DispatcherLookupType.registry, ssc.dispatchers.get(1).lookupType);
            assertEquals("some-public-key", ssc.dispatchers.get(1).publicKey);
            assertTrue(ssc.dispatchers.get(1).signatureRequired);
            assertTrue(ssc.dispatchers.get(1).disabled);
        }
    }

    @Test
    public void test() {
        DispatcherLookupConfig ssc = new DispatcherLookupConfig();

        DispatcherLookupConfig.DispatcherLookup config = new DispatcherLookupConfig.DispatcherLookup();
        config.url = "http://localhost:8080";
        config.signatureRequired = false;
        config.lookupType = DispatcherLookupConfig.DispatcherLookupType.direct;

        ssc.dispatchers.add(config);

        config = new DispatcherLookupConfig.DispatcherLookup();
        config.url = "https://host";
        config.signatureRequired = true;
        config.publicKey = "some-public-key";
        config.lookupType = DispatcherLookupConfig.DispatcherLookupType.registry;

        ssc.dispatchers.add(config);

        String yaml = DispatcherLookupConfigUtils.toString(ssc);
        System.out.println(yaml);

        DispatcherLookupConfig ssc1 = DispatcherLookupConfigUtils.to(yaml);

        assertEquals(ssc.dispatchers.size(), ssc1.dispatchers.size());

        assertEquals(ssc.dispatchers.get(0).url, ssc1.dispatchers.get(0).url);
        assertEquals(ssc.dispatchers.get(0).publicKey, ssc1.dispatchers.get(0).publicKey);
        assertEquals(ssc.dispatchers.get(0).signatureRequired, ssc1.dispatchers.get(0).signatureRequired);
        assertEquals(ssc.dispatchers.get(0).lookupType, ssc1.dispatchers.get(0).lookupType);

        assertEquals(ssc.dispatchers.get(1).url, ssc1.dispatchers.get(1).url);
        assertEquals(ssc.dispatchers.get(1).publicKey, ssc1.dispatchers.get(1).publicKey);
        assertEquals(ssc.dispatchers.get(1).signatureRequired, ssc1.dispatchers.get(1).signatureRequired);
        assertEquals(ssc.dispatchers.get(1).lookupType, ssc1.dispatchers.get(1).lookupType);

    }

}
