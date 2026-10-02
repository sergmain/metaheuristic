/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.LongPredicate;

/**
 * Crypto and decision logic for auto-unlocking a company's Key Vault at Dispatcher start.
 *
 * <p>The Vault passphrase is stored in application.properties encrypted with a KEK (key-encryption key)
 * that lives in an OS environment variable. Encryption is AES-256-GCM via {@link JsonCrypto}; the AAD
 * binds the ciphertext to one company, so a value produced for one company never opens another.
 *
 * <p>Pure: no Spring, no environment reads, no Vault access — every input is a parameter.
 *
 * <p>Error code prefix: {@code 01.671.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 10/1/2026
 */
public final class VaultBootUnlockUtils {

    /** KEK length in bytes: AES-256. */
    public static final int KEK_LEN = 32;

    private static final String AAD_PREFIX = "mh-vault-boot-unlock:";

    /** Outcome of a boot-unlock attempt, in the order the checks run. */
    public enum Status {
        /** no encrypted passphrase configured - the Vault stays locked, as before */
        NOT_CONFIGURED,
        /** encrypted passphrase configured, but the KEK environment variable is not set */
        KEK_MISSING,
        /** the company has no persisted Vault - boot-unlock never creates one */
        // no longer produced: a missing Vault is created empty, see READY_NO_VAULT and CREATED
        NO_VAULT,
        /** wrong KEK, wrong company, tampered or malformed value */
        DECRYPT_FAILED,
        /** passphrase recovered, ready for {@code VaultService.unlock} */
        READY,
        /** passphrase recovered, the company has no stored Vault - {@code VaultService.unlock} creates it empty */
        READY_NO_VAULT,
        /** the recovered passphrase did not open the Vault */
        UNLOCK_FAILED,
        /** the Vault is open */
        UNLOCKED,
        /** no Vault was stored; an empty one was created and is open, stored when its first entry is added */
        CREATED
    }

    /** {@code passphrase} is non-null only for {@link Status#READY} and {@link Status#READY_NO_VAULT}. */
    public record BootPassphrase(Status status, @Nullable String passphrase) {}

    private VaultBootUnlockUtils() {
        // utility class
    }

    /**
     * Decide whether a boot-unlock can be attempted and, if so, recover the passphrase.
     *
     * @param encryptedB64     value of {@code mh.dispatcher.vault.boot-unlock.encrypted-passphrase}
     * @param kekB64           value of the KEK environment variable
     * @param companyUniqueId  company whose Vault is to be unlocked
     * @param hasPersistedVault does this company have a Vault persisted in {@code Company.params}
     */
    public static BootPassphrase resolveBootPassphrase(
            @Nullable String encryptedB64, @Nullable String kekB64, long companyUniqueId, LongPredicate hasPersistedVault) {

        if (encryptedB64 == null || encryptedB64.isBlank()) {
            return new BootPassphrase(Status.NOT_CONFIGURED, null);
        }
        if (kekB64 == null || kekB64.isBlank()) {
            return new BootPassphrase(Status.KEK_MISSING, null);
        }
        final String passphrase;
        try {
            passphrase = decryptPassphrase(kekB64, encryptedB64, companyUniqueId);
        }
        catch (Exception e) {
            return new BootPassphrase(Status.DECRYPT_FAILED, null);
        }
        // A missing Vault is created empty with this passphrase, so that a key that isn't there reads as
        // "no such entry" (410) instead of "Vault locked" (423) - two different errors.
        return new BootPassphrase(hasPersistedVault.test(companyUniqueId) ? Status.READY : Status.READY_NO_VAULT, passphrase);
    }

    /** @return Base64 of [IV || ciphertext || tag] */
    public static String encryptPassphrase(String kekB64, String passphrase, long companyUniqueId) throws GeneralSecurityException {
        final byte[] kek = decodeKek(kekB64);
        try {
            return Base64.getEncoder().encodeToString(JsonCrypto.encrypt(kek, passphrase, aad(companyUniqueId)));
        }
        finally {
            Arrays.fill(kek, (byte) 0);
        }
    }

    public static String decryptPassphrase(String kekB64, String encryptedB64, long companyUniqueId) throws GeneralSecurityException {
        final byte[] kek = decodeKek(kekB64);
        try {
            return JsonCrypto.decrypt(kek, Base64.getDecoder().decode(encryptedB64.strip()), aad(companyUniqueId));
        }
        finally {
            Arrays.fill(kek, (byte) 0);
        }
    }

    /** The caller owns the returned buffer and must zero it. */
    public static byte[] decodeKek(String kekB64) {
        final byte[] kek = Base64.getDecoder().decode(kekB64.strip());
        if (kek.length != KEK_LEN) {
            final int len = kek.length;
            Arrays.fill(kek, (byte) 0);
            throw new IllegalArgumentException("01.671.010 KEK must be Base64 of " + KEK_LEN + " bytes (AES-256), actual length: " + len);
        }
        return kek;
    }

    static byte[] aad(long companyUniqueId) {
        return (AAD_PREFIX + companyUniqueId).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * State of the KEK environment variable for the startup log - never the value itself.
     *
     * @param kekB64 value of the KEK environment variable, as read
     */
    public static String describeKek(@Nullable String kekB64) {
        if (kekB64 == null || kekB64.isBlank()) {
            return "NOT set";
        }
        try {
            Arrays.fill(decodeKek(kekB64), (byte) 0);
            return "set, Base64 of " + KEK_LEN + " bytes";
        }
        catch (IllegalArgumentException e) {
            return "set, but NOT Base64 of " + KEK_LEN + " bytes";
        }
    }
}
