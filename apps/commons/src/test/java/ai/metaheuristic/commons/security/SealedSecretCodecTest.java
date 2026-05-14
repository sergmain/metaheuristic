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

package ai.metaheuristic.commons.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Round-trip and rejection tests for {@link SealedSecretCodec}.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class SealedSecretCodecTest {

    @Test
    void test_codec_roundTrip() throws Exception {
        CreateKeys ck = new CreateKeys(2048);
        SealedSecret a = AsymmetricEncryptor.encrypt("xyz".getBytes(StandardCharsets.UTF_8), ck.getPublicKey());
        byte[] wire = SealedSecretCodec.toBytes(a);
        SealedSecret b = SealedSecretCodec.fromBytes(wire);

        assertEquals(a.version(), b.version());
        assertArrayEquals(a.wrappedAesKey(), b.wrappedAesKey());
        assertArrayEquals(a.gcmIv(), b.gcmIv());
        assertArrayEquals(a.ciphertextWithTag(), b.ciphertextWithTag());
    }

    @Test
    void test_codec_unknownVersion_throws() {
        byte[] wire = new byte[]{(byte) 0xFF, 0, 0};
        assertThrows(IllegalArgumentException.class, () -> SealedSecretCodec.fromBytes(wire));
    }
}
