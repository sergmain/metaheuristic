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

package ai.metaheuristic.ai.dispatcher.vault;

import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the AES/GCM JSON encryption helper.
 *
 * @author Sergio Lissner
 */
class JsonCryptoTest {

    private static byte[] aes256Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Test
    void roundTrip_recoversPlaintext() throws Exception {
        byte[] key = aes256Key();
        String plaintext = "{\"42:openai\":\"sk-test-value\"}";

        byte[] payload = JsonCrypto.encrypt(key, plaintext, null);
        String decrypted = JsonCrypto.decrypt(key, payload, null);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void roundTrip_withAad_recoversPlaintext() throws Exception {
        byte[] key = aes256Key();
        byte[] aad = "header-bytes".getBytes();
        String plaintext = "{\"k\":\"v\"}";

        byte[] payload = JsonCrypto.encrypt(key, plaintext, aad);
        assertEquals(plaintext, JsonCrypto.decrypt(key, payload, aad));
    }

    @Test
    void encrypt_producesDistinctOutputsAcrossCalls() throws Exception {
        // Same key, same plaintext, but a fresh random IV per call must yield
        // different ciphertexts. This is a key safety property of GCM.
        byte[] key = aes256Key();
        String plaintext = "{}";

        byte[] a = JsonCrypto.encrypt(key, plaintext, null);
        byte[] b = JsonCrypto.encrypt(key, plaintext, null);

        assertNotEquals(java.util.Arrays.toString(a), java.util.Arrays.toString(b));
    }

    @Test
    void decrypt_withWrongKey_throws() throws Exception {
        byte[] key1 = aes256Key();
        byte[] key2 = aes256Key();
        byte[] payload = JsonCrypto.encrypt(key1, "{\"k\":\"v\"}", null);

        assertThrows(AEADBadTagException.class, () -> JsonCrypto.decrypt(key2, payload, null));
    }

    @Test
    void decrypt_withTamperedCiphertext_throws() throws Exception {
        byte[] key = aes256Key();
        byte[] payload = JsonCrypto.encrypt(key, "{\"k\":\"v\"}", null);

        // Flip a bit somewhere in the ciphertext (past the 12-byte IV).
        payload[payload.length - 1] ^= 0x01;

        assertThrows(AEADBadTagException.class, () -> JsonCrypto.decrypt(key, payload, null));
    }

    @Test
    void decrypt_withWrongAad_throws() throws Exception {
        byte[] key = aes256Key();
        byte[] payload = JsonCrypto.encrypt(key, "{\"k\":\"v\"}", "good-aad".getBytes());

        // Same key, same ciphertext, but the AAD differs — must fail.
        assertThrows(AEADBadTagException.class,
                () -> JsonCrypto.decrypt(key, payload, "bad-aad".getBytes()));
    }

    @Test
    void decrypt_missingAad_whenEncryptedWithAad_throws() throws Exception {
        byte[] key = aes256Key();
        byte[] payload = JsonCrypto.encrypt(key, "{\"k\":\"v\"}", "header".getBytes());

        // Encrypted with AAD, decrypt without — must fail.
        assertThrows(AEADBadTagException.class, () -> JsonCrypto.decrypt(key, payload, null));
    }

    @Test
    void decrypt_withTooShortPayload_throwsIllegalArgument() {
        byte[] key = aes256Key();
        byte[] tiny = new byte[4];

        assertThrows(IllegalArgumentException.class, () -> JsonCrypto.decrypt(key, tiny, null));
    }
}
