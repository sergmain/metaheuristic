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
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Round-trip, tamper, and wrong-key tests for {@link AsymmetricEncryptor}.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class AsymmetricEncryptorTest {

    @Test
    void test_encryptDecrypt_roundTrip() throws Exception {
        CreateKeys ck = new CreateKeys(2048);
        byte[] plaintext = "sk-test-secret-12345".getBytes(StandardCharsets.UTF_8);

        SealedSecret sealed = AsymmetricEncryptor.encrypt(plaintext, ck.getPublicKey());
        byte[] recovered = AsymmetricEncryptor.decrypt(sealed, ck.getPrivateKey());

        assertArrayEquals(plaintext, recovered);
    }

    @Test
    void test_encryptDecrypt_longPayload() throws Exception {
        CreateKeys ck = new CreateKeys(2048);
        byte[] plaintext = new byte[4096];
        new SecureRandom().nextBytes(plaintext);

        SealedSecret sealed = AsymmetricEncryptor.encrypt(plaintext, ck.getPublicKey());
        byte[] recovered = AsymmetricEncryptor.decrypt(sealed, ck.getPrivateKey());

        assertArrayEquals(plaintext, recovered);
    }

    @Test
    void test_decrypt_tamperedCiphertext_throws() throws Exception {
        CreateKeys ck = new CreateKeys(2048);
        byte[] plaintext = "abc".getBytes(StandardCharsets.UTF_8);

        SealedSecret sealed = AsymmetricEncryptor.encrypt(plaintext, ck.getPublicKey());
        sealed.ciphertextWithTag()[0] ^= 0x01;

        assertThrows(GeneralSecurityException.class,
            () -> AsymmetricEncryptor.decrypt(sealed, ck.getPrivateKey()));
    }

    @Test
    void test_decrypt_wrongKey_throws() throws Exception {
        CreateKeys alice = new CreateKeys(2048);
        CreateKeys bob = new CreateKeys(2048);

        byte[] plaintext = "abc".getBytes(StandardCharsets.UTF_8);
        SealedSecret sealed = AsymmetricEncryptor.encrypt(plaintext, alice.getPublicKey());

        assertThrows(GeneralSecurityException.class,
            () -> AsymmetricEncryptor.decrypt(sealed, bob.getPrivateKey()));
    }
}
