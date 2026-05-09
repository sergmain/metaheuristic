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

import org.jspecify.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES/GCM encryption helper for the dispatcher vault.
 *
 * <p>Output layout: {@code [12-byte IV || ciphertext || 16-byte auth tag]}.
 * IV is random per call — never reuse an IV with the same key.
 *
 * <p>Optional AAD (Associated Authenticated Data) binds the ciphertext to
 * external context (e.g. file header bytes). AAD is authenticated but not
 * encrypted; encrypting with one AAD and decrypting with another fails the
 * tag check. The vault uses this to bind ciphertext to its on-disk frame
 * header — flipping the iteration count, swapping the salt, or splicing in
 * a different ciphertext all fail authentication.
 *
 * <p>Caller is responsible for KDF (passphrase to AES key) and any
 * higher-level framing (magic bytes, salt, iteration count).
 *
 * @author Sergio Lissner
 */
public final class JsonCrypto {

    private static final String ALG = "AES/GCM/NoPadding";
    private static final int GCM_IV_LEN = 12;       // 96 bits - recommended for GCM
    private static final int GCM_TAG_LEN = 128;     // 128 bits - full strength
    private static final SecureRandom RNG = new SecureRandom();

    private JsonCrypto() {
        // utility class
    }

    /**
     * Encrypt plaintext JSON with the given AES key.
     *
     * Returns a single byte[] of [12-byte IV || ciphertext || 16-byte auth tag].
     * IV is random per call — never reuse an IV with the same key.
     *
     * @param key  AES key, 16/24/32 bytes long (AES-128/192/256)
     * @param json plaintext JSON string
     * @param aad  optional associated data; may be null
     */
    public static byte[] encrypt(byte[] key, String json, @Nullable byte[] aad) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_LEN];
        RNG.nextBytes(iv);
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        try {
            Cipher cipher = Cipher.getInstance(ALG);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            if (aad != null) {
                cipher.updateAAD(aad);
            }
            byte[] plaintext = json.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext;
            try {
                ciphertext = cipher.doFinal(plaintext);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
            // Prepend IV so decrypt() can recover it
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } finally {
            // Best-effort wipe of the SecretKeySpec's internal copy is not possible
            // (Java caches it). Caller should also wipe the source key bytes.
        }
    }

    /**
     * Decrypt the format produced by {@link #encrypt}.
     * Throws AEADBadTagException if data was tampered with, AAD doesn't match,
     * or wrong key used.
     *
     * @param aad must equal the AAD passed to encrypt(); null if none was used
     */
    public static String decrypt(byte[] key, byte[] payload, @Nullable byte[] aad) throws GeneralSecurityException {
        if (payload.length < GCM_IV_LEN + GCM_TAG_LEN / 8) {
            throw new IllegalArgumentException("Payload too short");
        }
        byte[] iv = new byte[GCM_IV_LEN];
        System.arraycopy(payload, 0, iv, 0, GCM_IV_LEN);
        byte[] ciphertext = new byte[payload.length - GCM_IV_LEN];
        System.arraycopy(payload, GCM_IV_LEN, ciphertext, 0, ciphertext.length);
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance(ALG);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
        if (aad != null) {
            cipher.updateAAD(aad);
        }
        byte[] plaintext = cipher.doFinal(ciphertext);
        try {
            return new String(plaintext, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }
}
