/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.ai.processor.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * In-memory keypair generation for the Processor.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class ProcessorKeyPairTest {

    @Test
    void test_init_populatesKeys_andSpkiRoundTrips() throws Exception {
        ProcessorKeyPair kp = new ProcessorKeyPair();
        kp.init();

        assertNotNull(kp.getPrivateKey());
        assertNotNull(kp.getPublicKey());

        byte[] spki = kp.getPublicKeySpki();
        assertNotNull(spki);
        assertTrue(spki.length > 0);

        // SPKI bytes must round-trip through KeyFactory("RSA")
        PublicKey reparsed = KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(spki));
        assertArrayEquals(spki, reparsed.getEncoded());

        // Two separate ProcessorKeyPair instances produce two different keys
        ProcessorKeyPair other = new ProcessorKeyPair();
        other.init();
        assertFalse(java.util.Arrays.equals(kp.getPublicKeySpki(), other.getPublicKeySpki()),
            "Two fresh ProcessorKeyPair instances must produce different public keys");
    }
}
