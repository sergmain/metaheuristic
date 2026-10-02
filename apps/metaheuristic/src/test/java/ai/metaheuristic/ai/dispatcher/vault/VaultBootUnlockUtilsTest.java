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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.function.LongPredicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link VaultBootUnlockUtils}. No Spring context; real AES-GCM, real random KEKs.
 *
 * @author Sergio Lissner
 * Date: 10/1/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class VaultBootUnlockUtilsTest {

    private static final long MGMT = 1L;
    private static final String PASSPHRASE = "correct horse battery staple";

    private static String newKek() {
        byte[] b = new byte[VaultBootUnlockUtils.KEK_LEN];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private static LongPredicate vaultsOf(Long... ids) {
        final Set<Long> vaults = Set.of(ids);
        return vaults::contains;
    }

    @Test
    public void test_roundTrip() throws Exception {
        final String kek = newKek();
        final String enc = VaultBootUnlockUtils.encryptPassphrase(kek, PASSPHRASE, MGMT);
        assertEquals(PASSPHRASE, VaultBootUnlockUtils.decryptPassphrase(kek, enc, MGMT));
    }

    @Test
    public void test_roundTrip_toleratesSurroundingWhitespaceFromAPropertiesFile() throws Exception {
        final String kek = newKek();
        final String enc = VaultBootUnlockUtils.encryptPassphrase(kek, PASSPHRASE, MGMT);
        assertEquals(PASSPHRASE, VaultBootUnlockUtils.decryptPassphrase(" " + kek + "\n", " " + enc + " ", MGMT));
    }

    @Test
    public void test_encrypt_isRandomizedPerCall() throws Exception {
        final String kek = newKek();
        assertNotEquals(VaultBootUnlockUtils.encryptPassphrase(kek, PASSPHRASE, MGMT),
                VaultBootUnlockUtils.encryptPassphrase(kek, PASSPHRASE, MGMT),
                "a fresh IV per call must give a different ciphertext for the same passphrase");
    }

    @Test
    public void test_decrypt_withAnotherKek_fails() throws Exception {
        final String enc = VaultBootUnlockUtils.encryptPassphrase(newKek(), PASSPHRASE, MGMT);
        assertThrows(GeneralSecurityException.class, () -> VaultBootUnlockUtils.decryptPassphrase(newKek(), enc, MGMT));
    }

    @Test
    public void test_decrypt_forAnotherCompany_fails() throws Exception {
        final String kek = newKek();
        final String enc = VaultBootUnlockUtils.encryptPassphrase(kek, PASSPHRASE, MGMT);
        assertThrows(GeneralSecurityException.class, () -> VaultBootUnlockUtils.decryptPassphrase(kek, enc, 7L),
                "the AAD binds the value to its company; it must not open another company's Vault");
    }

    @Test
    public void test_decrypt_ofTamperedValue_fails() throws Exception {
        final String kek = newKek();
        final byte[] raw = Base64.getDecoder().decode(VaultBootUnlockUtils.encryptPassphrase(kek, PASSPHRASE, MGMT));
        raw[raw.length - 1] ^= 0x01;
        final String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(GeneralSecurityException.class, () -> VaultBootUnlockUtils.decryptPassphrase(kek, tampered, MGMT));
    }

    @Test
    public void test_decodeKek_rejectsWrongLength() {
        final String shortKek = Base64.getEncoder().encodeToString(new byte[16]);
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> VaultBootUnlockUtils.decodeKek(shortKek));
        assertTrue(e.getMessage().startsWith("01.671.010 "), e.getMessage());
    }

    @Test
    public void test_resolve_notConfigured_whenValueIsNullOrBlank() {
        assertEquals(VaultBootUnlockUtils.Status.NOT_CONFIGURED,
                VaultBootUnlockUtils.resolveBootPassphrase(null, newKek(), MGMT, vaultsOf(MGMT)).status());
        assertEquals(VaultBootUnlockUtils.Status.NOT_CONFIGURED,
                VaultBootUnlockUtils.resolveBootPassphrase("  ", newKek(), MGMT, vaultsOf(MGMT)).status());
    }

    @Test
    public void test_resolve_kekMissing() throws Exception {
        final String enc = VaultBootUnlockUtils.encryptPassphrase(newKek(), PASSPHRASE, MGMT);
        assertEquals(VaultBootUnlockUtils.Status.KEK_MISSING,
                VaultBootUnlockUtils.resolveBootPassphrase(enc, null, MGMT, vaultsOf(MGMT)).status());
        assertEquals(VaultBootUnlockUtils.Status.KEK_MISSING,
                VaultBootUnlockUtils.resolveBootPassphrase(enc, "", MGMT, vaultsOf(MGMT)).status());
    }

    @Test
    public void test_resolve_noVault_handsOutThePassphraseToCreateItEmpty() throws Exception {
        final String kek = newKek();
        final String enc = VaultBootUnlockUtils.encryptPassphrase(kek, PASSPHRASE, MGMT);
        final VaultBootUnlockUtils.BootPassphrase bp = VaultBootUnlockUtils.resolveBootPassphrase(enc, kek, MGMT, vaultsOf(7L));
        assertNotEquals(VaultBootUnlockUtils.Status.NO_VAULT, bp.status(), "a missing Vault no longer stops boot-unlock - it is created empty");
        assertEquals(VaultBootUnlockUtils.Status.READY_NO_VAULT, bp.status());
        assertEquals(PASSPHRASE, bp.passphrase(), "the passphrase is what the empty Vault is created with");
    }

    @Test
    public void test_resolve_decryptFailed_forAValueOfAnotherCompany() throws Exception {
        final String kek = newKek();
        final String encForOther = VaultBootUnlockUtils.encryptPassphrase(kek, PASSPHRASE, 7L);
        final VaultBootUnlockUtils.BootPassphrase bp = VaultBootUnlockUtils.resolveBootPassphrase(encForOther, kek, MGMT, vaultsOf(MGMT));
        assertEquals(VaultBootUnlockUtils.Status.DECRYPT_FAILED, bp.status());
        assertNull(bp.passphrase());
    }

    @Test
    public void test_resolve_decryptFailed_forAMalformedKek() throws Exception {
        final String enc = VaultBootUnlockUtils.encryptPassphrase(newKek(), PASSPHRASE, MGMT);
        assertEquals(VaultBootUnlockUtils.Status.DECRYPT_FAILED,
                VaultBootUnlockUtils.resolveBootPassphrase(enc, "not-base64-!!", MGMT, vaultsOf(MGMT)).status());
    }

    @Test
    public void test_resolve_ready_recoversThePassphrase() throws Exception {
        final String kek = newKek();
        final String enc = VaultBootUnlockUtils.encryptPassphrase(kek, PASSPHRASE, MGMT);
        final VaultBootUnlockUtils.BootPassphrase bp = VaultBootUnlockUtils.resolveBootPassphrase(enc, kek, MGMT, vaultsOf(MGMT));
        assertEquals(VaultBootUnlockUtils.Status.READY, bp.status());
        assertEquals(PASSPHRASE, bp.passphrase());
    }

    @Test
    public void test_describeKek_notSet() {
        assertEquals("NOT set", VaultBootUnlockUtils.describeKek(null));
        assertEquals("NOT set", VaultBootUnlockUtils.describeKek("  "));
    }

    @Test
    public void test_describeKek_valid_neverContainsTheValue() {
        final String kek = newKek();
        final String d = VaultBootUnlockUtils.describeKek(kek);
        assertEquals("set, Base64 of 32 bytes", d);
        assertFalse(d.contains(kek));
    }

    @Test
    public void test_describeKek_wrongLengthOrNotBase64() {
        assertEquals("set, but NOT Base64 of 32 bytes", VaultBootUnlockUtils.describeKek(Base64.getEncoder().encodeToString(new byte[16])));
        assertEquals("set, but NOT Base64 of 32 bytes", VaultBootUnlockUtils.describeKek("not-base64-!!"));
    }
}
