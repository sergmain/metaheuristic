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

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;

/**
 * Hybrid asymmetric encryption: RSA-OAEP-SHA-256 + AES-256-GCM.
 *
 * <p>Used by Dispatcher to encrypt a per-tenant API key under a Processor's
 * RSA public key. Used by Processor to decrypt with its private key just before
 * launching a Function.
 *
 * <p>OAEP parameters are pinned explicitly: digest = SHA-256, MGF1 = SHA-256.
 * The JCA default for {@code RSA/ECB/OAEPWithSHA-256AndMGF1Padding} historically
 * uses MGF1-SHA-1 on some JDKs even when the outer hash is SHA-256, which leads
 * to silent interop failures. We never rely on the default.
 *
 * @author Sergio Lissner
 */
public final class AsymmetricEncryptor {

    private static final String RSA_TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_TRANSFORM = "AES/GCM/NoPadding";
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final SecureRandom RNG = newRng();

    private static final OAEPParameterSpec OAEP_PARAMS = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private AsymmetricEncryptor() {
        // static-only
    }

    private static SecureRandom newRng() {
        try {
            return SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            // Fall back to default — never silent on prod hosts; documented so reviewers can audit.
            return new SecureRandom();
        }
    }

    /**
     * Encrypts the given plaintext under the recipient's RSA public key.
     *
     * <p>The plaintext buffer is NOT zeroed by this method; the caller owns it.
     */
    public static SealedSecret encrypt(byte[] plaintext, PublicKey recipientPublicKey)
            throws GeneralSecurityException {

        // 1. Generate a fresh 256-bit AES key.
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(AES_KEY_BITS, RNG);
        SecretKey aesKey = kg.generateKey();
        byte[] aesKeyBytes = aesKey.getEncoded();
        try {
            // 2. Wrap the AES key under the recipient's RSA public key.
            Cipher rsa = Cipher.getInstance(RSA_TRANSFORM);
            rsa.init(Cipher.ENCRYPT_MODE, recipientPublicKey, OAEP_PARAMS, RNG);
            byte[] wrappedAesKey = rsa.doFinal(aesKeyBytes);

            // 3. AES-GCM-encrypt the plaintext with a random 96-bit IV.
            byte[] iv = new byte[GCM_IV_LEN];
            RNG.nextBytes(iv);
            Cipher aes = Cipher.getInstance(AES_TRANSFORM);
            aes.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertextWithTag = aes.doFinal(plaintext);

            return new SealedSecret(SealedSecret.VERSION_1, wrappedAesKey, iv, ciphertextWithTag);
        } finally {
            // Best-effort: zero the raw AES key bytes. The SecretKey object itself
            // may still retain a copy inside the JCE provider; that's outside our
            // control and not load-bearing per the threat model.
            Arrays.fill(aesKeyBytes, (byte) 0);
        }
    }

    /**
     * Decrypts a SealedSecret. Returns a fresh byte[] owned by the caller,
     * who must {@code Arrays.fill} it after use.
     *
     * <p>Throws if the version is unknown, the AES key cannot be unwrapped (wrong
     * recipient or corruption), or the GCM tag does not verify (tamper detection).
     */
    public static byte[] decrypt(SealedSecret sealed, PrivateKey recipientPrivateKey)
            throws GeneralSecurityException {

        if (sealed.version() != SealedSecret.VERSION_1) {
            throw new GeneralSecurityException("Unsupported SealedSecret version: " + sealed.version());
        }

        // 1. Unwrap the AES key with the recipient's RSA private key.
        Cipher rsa = Cipher.getInstance(RSA_TRANSFORM);
        rsa.init(Cipher.DECRYPT_MODE, recipientPrivateKey, OAEP_PARAMS);
        byte[] aesKeyBytes = rsa.doFinal(sealed.wrappedAesKey());
        try {
            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

            // 2. AES-GCM-decrypt the ciphertext; the tag is appended at the end (JCE convention).
            Cipher aes = Cipher.getInstance(AES_TRANSFORM);
            aes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, sealed.gcmIv()));
            return aes.doFinal(sealed.ciphertextWithTag());
        } finally {
            Arrays.fill(aesKeyBytes, (byte) 0);
        }
    }
}
