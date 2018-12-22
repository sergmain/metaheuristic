package aiai.ai.yaml;

import aiai.ai.yaml.launchpad_lookup.LaunchpadLookupConfig;
import aiai.ai.yaml.launchpad_lookup.LaunchpadLookupConfigUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

public class TestLaunchpadLookup {

    @Test
    public void testParsingYaml() throws IOException {
        try (InputStream is = TestLaunchpadLookup.class.getResourceAsStream("/yaml/launchpads.yaml")) {
            LaunchpadLookupConfig ssc = LaunchpadLookupConfigUtils.to(is);

            assertEquals(2, ssc.launchpads.size());

            assertEquals("http://localhost:8080", ssc.launchpads.get(0).url);
            assertEquals(LaunchpadLookupConfig.LaunchpadLookupType.direct, ssc.launchpads.get(0).lookupType);
            assertNull(ssc.launchpads.get(0).publicKey);
            assertFalse(ssc.launchpads.get(0).signatureRequired);
            assertFalse(ssc.launchpads.get(0).disabled);

            assertEquals("https://host", ssc.launchpads.get(1).url);
            assertEquals(LaunchpadLookupConfig.LaunchpadLookupType.registry, ssc.launchpads.get(1).lookupType);
            assertEquals("some-public-key", ssc.launchpads.get(1).publicKey);
            assertTrue(ssc.launchpads.get(1).signatureRequired);
            assertTrue(ssc.launchpads.get(1).disabled);
        }
    }

    @Test
    public void test() {
        LaunchpadLookupConfig ssc = new LaunchpadLookupConfig();

        LaunchpadLookupConfig.LaunchpadLookup config = new LaunchpadLookupConfig.LaunchpadLookup();
        config.url = "http://localhost:8080";
        config.signatureRequired = false;
        config.lookupType = LaunchpadLookupConfig.LaunchpadLookupType.direct;

        ssc.launchpads.add(config);

        config = new LaunchpadLookupConfig.LaunchpadLookup();
        config.url = "https://host";
        config.signatureRequired = true;
        config.publicKey = "some-public-key";
        config.lookupType = LaunchpadLookupConfig.LaunchpadLookupType.registry;

        ssc.launchpads.add(config);

        String yaml = LaunchpadLookupConfigUtils.toString(ssc);
        System.out.println(yaml);

        LaunchpadLookupConfig ssc1 = LaunchpadLookupConfigUtils.to(yaml);

        assertEquals(ssc.launchpads.size(), ssc1.launchpads.size());

        assertEquals(ssc.launchpads.get(0).url, ssc1.launchpads.get(0).url);
        assertEquals(ssc.launchpads.get(0).publicKey, ssc1.launchpads.get(0).publicKey);
        assertEquals(ssc.launchpads.get(0).signatureRequired, ssc1.launchpads.get(0).signatureRequired);
        assertEquals(ssc.launchpads.get(0).lookupType, ssc1.launchpads.get(0).lookupType);

        assertEquals(ssc.launchpads.get(1).url, ssc1.launchpads.get(1).url);
        assertEquals(ssc.launchpads.get(1).publicKey, ssc1.launchpads.get(1).publicKey);
        assertEquals(ssc.launchpads.get(1).signatureRequired, ssc1.launchpads.get(1).signatureRequired);
        assertEquals(ssc.launchpads.get(1).lookupType, ssc1.launchpads.get(1).lookupType);

    }

}
