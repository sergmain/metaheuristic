package aiai.ai.yaml;

import aiai.ai.yaml.signature.SignaturesConfig;
import aiai.ai.yaml.signature.SignaturesConfigUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class TestSignatureConfig {

    @Test
    public void testParsingYaml() throws IOException {
        try(InputStream is = TestSignatureConfig.class.getResourceAsStream("/yaml/signature-config.yaml")) {
            SignaturesConfig ssc = SignaturesConfigUtils.to(is);

            assertEquals(2, ssc.configs.size());

            assertEquals("http://localhost:8080", ssc.configs.get(0).url);
            assertNull(ssc.configs.get(0).publicKey);
            assertFalse(ssc.configs.get(0).signatureRequired);

            assertEquals("https://host", ssc.configs.get(1).url);
            assertEquals("some-public-key", ssc.configs.get(1).publicKey);
            assertTrue(ssc.configs.get(1).signatureRequired);
        }
    }

        @Test
    public void test() {
        SignaturesConfig ssc = new SignaturesConfig();

        SignaturesConfig.SignatureConfig config = new SignaturesConfig.SignatureConfig();
        config.url = "http://localhost:8080";
        config.signatureRequired = false;

        ssc.configs.add(config);

        config = new SignaturesConfig.SignatureConfig();
        config.url = "https://host";
        config.signatureRequired = true;
        config.publicKey = "some-public-key";

        ssc.configs.add(config);

        String yaml = SignaturesConfigUtils.toString(ssc);
        System.out.println(yaml);

        SignaturesConfig ssc1 = SignaturesConfigUtils.to(yaml);

        assertEquals(ssc.configs.size(), ssc1.configs.size());

        assertEquals(ssc.configs.get(0).url, ssc1.configs.get(0).url);
        assertEquals(ssc.configs.get(0).publicKey, ssc1.configs.get(0).publicKey);
        assertEquals(ssc.configs.get(0).signatureRequired, ssc1.configs.get(0).signatureRequired);

        assertEquals(ssc.configs.get(1).url, ssc1.configs.get(1).url);
        assertEquals(ssc.configs.get(1).publicKey, ssc1.configs.get(1).publicKey);
        assertEquals(ssc.configs.get(1).signatureRequired, ssc1.configs.get(1).signatureRequired);

    }

}
