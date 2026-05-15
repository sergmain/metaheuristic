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

import ai.metaheuristic.ai.dispatcher.data.VaultData;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Plain unit tests for VaultService — no Spring context. Uses an in-memory
 * fake {@link VaultTxService} so the encryption + KDF + AAD paths are
 * exercised end-to-end against an in-process company-keyed blob store
 * without test containers / H2.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class VaultServiceTest {

    /**
     * In-memory stand-in for {@link VaultTxService} backed by a
     * {@code ConcurrentHashMap<companyUniqueId, VaultEntries>}. Persists
     * across {@link VaultService} instances inside one test so a fresh
     * {@link VaultService} over the same fake store reproduces the
     * dispatcher-restart scenario.
     */
    static final class FakeVaultTxService extends VaultTxService {
        final ConcurrentHashMap<Long, CompanyParamsYaml.VaultEntries> store = new ConcurrentHashMap<>();
        final List<String> events = new ArrayList<>();
        /** If non-null, saveVaultBlob returns false to simulate company-not-found / write failure. */
        Long failOnCompanyId = null;

        FakeVaultTxService() {
            super(null, null, null);
        }

        @Override
        public boolean saveVaultBlob(long companyUniqueId, CompanyParamsYaml.VaultEntries blob,
                                     String keyCode, String action) {
            if (failOnCompanyId != null && failOnCompanyId == companyUniqueId) {
                return false;
            }
            store.put(companyUniqueId, blob);
            events.add(companyUniqueId + ":" + keyCode + ":" + action);
            return true;
        }

        @Override
        public CompanyParamsYaml.VaultEntries loadVaultBlob(long companyUniqueId) {
            return store.get(companyUniqueId);
        }
    }

    private static VaultService newVaultService(FakeVaultTxService fake) {
        return new VaultService(fake);
    }

    @Test
    void initialState_isClosed() {
        VaultService service = newVaultService(new FakeVaultTxService());
        assertFalse(service.isOpened(7L), "Newly created service must be locked");
        assertEquals(Optional.empty(), service.getApiKey(7L, "any"));
    }

    @Test
    void unlock_firstTime_createsEmptyVault_butDoesNotPersistYet() {
        FakeVaultTxService fake = new FakeVaultTxService();
        VaultService service = newVaultService(fake);
        VaultData.UnlockResult r = service.unlock(42L, "master-pass-1");
        assertTrue(r.errorMessages == null || r.errorMessages.isEmpty(),
            "Expected no errors, got: " + r.errorMessages);
        assertTrue(r.opened);
        assertTrue(r.created, "Company had no vault; service must report created=true");
        assertTrue(service.isOpened(42L));
        // First-time unlock alone does NOT persist — only put/delete persist.
        assertNull(fake.store.get(42L));
    }

    @Test
    void unlock_existingVault_correctPassphrase_opensWithoutCreate() {
        FakeVaultTxService fake = new FakeVaultTxService();
        VaultService service = newVaultService(fake);
        assertTrue(service.unlock(42L, "master-pass-2").opened);
        service.putApiKey(42L, "k", "v");

        VaultService fresh = newVaultService(fake);
        VaultData.UnlockResult r = fresh.unlock(42L, "master-pass-2");
        assertTrue(r.opened);
        assertFalse(r.created, "Existing vault must report created=false");
        assertTrue(fresh.isOpened(42L));
    }

    @Test
    void unlock_existingVault_wrongPassphrase_returnsError() {
        FakeVaultTxService fake = new FakeVaultTxService();
        VaultService service = newVaultService(fake);
        assertTrue(service.unlock(42L, "correct-pass").opened);
        service.putApiKey(42L, "k", "v");

        VaultService fresh = newVaultService(fake);
        VaultData.UnlockResult r = fresh.unlock(42L, "wrong-pass");
        assertFalse(r.opened);
        assertFalse(fresh.isOpened(42L));
    }

    @Test
    void unlock_blankPassphrase_rejected() {
        VaultService service = newVaultService(new FakeVaultTxService());
        VaultData.UnlockResult r = service.unlock(42L, "");
        assertFalse(r.opened);
        assertFalse(service.isOpened(42L));
        assertNotNull(r.errorMessages);
    }

    @Test
    void getApiKey_afterPut_returnsValue() {
        VaultService service = newVaultService(new FakeVaultTxService());
        service.unlock(42L, "pass");
        assertTrue(service.putApiKey(42L, "openai", "sk-test-value-1"));
        assertEquals(Optional.of("sk-test-value-1"), service.getApiKey(42L, "openai"));
    }

    @Test
    void getApiKey_unknownCode_returnsEmpty() {
        VaultService service = newVaultService(new FakeVaultTxService());
        service.unlock(42L, "pass");
        assertEquals(Optional.empty(), service.getApiKey(42L, "nonexistent"));
    }

    @Test
    void getApiKey_isolatesByCompanyId() {
        FakeVaultTxService fake = new FakeVaultTxService();
        VaultService service = newVaultService(fake);
        service.unlock(2L, "pass-2");
        service.unlock(7L, "pass-7");
        service.putApiKey(2L, "openai", "tenant-2-secret");
        service.putApiKey(7L, "openai", "tenant-7-secret");

        assertEquals(Optional.of("tenant-2-secret"), service.getApiKey(2L, "openai"));
        assertEquals(Optional.of("tenant-7-secret"), service.getApiKey(7L, "openai"));
        // Different vault, no entry there
        assertEquals(Optional.empty(), service.getApiKey(42L, "openai"));
    }

    @Test
    void putApiKey_overwritesExistingEntry() {
        VaultService service = newVaultService(new FakeVaultTxService());
        service.unlock(2L, "pass");
        service.putApiKey(2L, "openai", "old-value");
        service.putApiKey(2L, "openai", "new-value");

        assertEquals(Optional.of("new-value"), service.getApiKey(2L, "openai"));
    }

    @Test
    void getApiKey_whenLocked_returnsEmpty() {
        VaultService service = newVaultService(new FakeVaultTxService());
        // service was never unlocked for company 2
        assertEquals(Optional.empty(), service.getApiKey(2L, "openai"));
    }

    @Test
    void putApiKey_whenLocked_returnsFalse() {
        VaultService service = newVaultService(new FakeVaultTxService());
        assertFalse(service.putApiKey(2L, "openai", "value"));
    }

    @Test
    void persistedEntry_visibleAfterReopen() {
        FakeVaultTxService fake = new FakeVaultTxService();
        VaultService service = newVaultService(fake);
        assertTrue(service.unlock(7L, "master").opened);
        assertTrue(service.putApiKey(7L, "openai", "persisted-value"));

        // Simulate restart with a fresh service over the same persistent fake.
        VaultService fresh = newVaultService(fake);
        assertTrue(fresh.unlock(7L, "master").opened);

        assertEquals(Optional.of("persisted-value"), fresh.getApiKey(7L, "openai"));
    }

    @Test
    void tamperedSalt_unlockFails() {
        // Salt is bound as GCM AAD via buildAad. Flipping the salt in the
        // persisted blob must fail authentication on decrypt.
        FakeVaultTxService fake = new FakeVaultTxService();
        VaultService service = newVaultService(fake);
        assertTrue(service.unlock(7L, "master").opened);
        assertTrue(service.putApiKey(7L, "openai", "v"));

        CompanyParamsYaml.VaultEntries blob = fake.store.get(7L);
        assertNotNull(blob);
        // Replace salt with a different one — same length so Base64 stays valid.
        byte[] saltBytes = java.util.Base64.getDecoder().decode(blob.salt);
        saltBytes[0] ^= 0x01;
        fake.store.put(7L, new CompanyParamsYaml.VaultEntries(
            java.util.Base64.getEncoder().encodeToString(saltBytes),
            blob.iterations,
            blob.encryptedEntries));

        VaultService fresh = newVaultService(fake);
        VaultData.UnlockResult r = fresh.unlock(7L, "master");
        assertFalse(r.opened, "Tampering with salt must fail GCM authentication");
        assertFalse(fresh.isOpened(7L));
    }

    @Test
    void tamperedIterations_unlockFails() {
        // Iterations is bound as GCM AAD via buildAad. Flipping the value in the
        // persisted blob must fail authentication on decrypt.
        FakeVaultTxService fake = new FakeVaultTxService();
        VaultService service = newVaultService(fake);
        assertTrue(service.unlock(7L, "master").opened);
        assertTrue(service.putApiKey(7L, "openai", "v"));

        CompanyParamsYaml.VaultEntries blob = fake.store.get(7L);
        fake.store.put(7L, new CompanyParamsYaml.VaultEntries(
            blob.salt,
            blob.iterations + 1, // tamper iteration count
            blob.encryptedEntries));

        VaultService fresh = newVaultService(fake);
        VaultData.UnlockResult r = fresh.unlock(7L, "master");
        assertFalse(r.opened, "Tampering with iterations must fail GCM authentication");
    }

    // ---- listEntries -----------------------------------------------------------------

    @Test
    void listEntries_whenLocked_returnsEmptyList() {
        VaultService service = newVaultService(new FakeVaultTxService());
        assertTrue(service.listEntries(7L).isEmpty());
    }

    @Test
    void listEntries_emptyVault_returnsEmptyList() {
        VaultService service = newVaultService(new FakeVaultTxService());
        service.unlock(7L, "pass");
        assertTrue(service.listEntries(7L).isEmpty());
    }

    @Test
    void listEntries_returnsCodesAndSecrets_inInsertionOrder() {
        VaultService service = newVaultService(new FakeVaultTxService());
        service.unlock(7L, "pass");
        service.putApiKey(7L, "openai", "secret-1");
        service.putApiKey(7L, "anthropic", "secret-3");

        var entries = service.listEntries(7L);
        assertEquals(2, entries.size());
        assertEquals(7L, entries.get(0).companyId());
        assertEquals("openai", entries.get(0).code());
        assertEquals("secret-1", entries.get(0).secret());
        assertEquals(7L, entries.get(1).companyId());
        assertEquals("anthropic", entries.get(1).code());
        assertEquals("secret-3", entries.get(1).secret());
    }

    @Test
    void listEntries_isolatedPerCompany() {
        FakeVaultTxService fake = new FakeVaultTxService();
        VaultService service = newVaultService(fake);
        service.unlock(7L, "pass-7");
        service.unlock(2L, "pass-2");
        service.putApiKey(7L, "openai", "tenant-7-secret");
        service.putApiKey(2L, "openai", "tenant-2-secret");

        var entriesFor2 = service.listEntries(2L);
        assertEquals(1, entriesFor2.size());
        assertEquals(2L, entriesFor2.get(0).companyId());
        assertEquals("tenant-2-secret", entriesFor2.get(0).secret());

        var entriesFor7 = service.listEntries(7L);
        assertEquals(1, entriesFor7.size());
        assertEquals(7L, entriesFor7.get(0).companyId());
    }

    // ---- deleteApiKey ----------------------------------------------------------------

    @Test
    void deleteApiKey_existingEntry_removesAndPersists() {
        FakeVaultTxService fake = new FakeVaultTxService();
        VaultService service = newVaultService(fake);
        service.unlock(7L, "pass");
        service.putApiKey(7L, "openai", "v");
        assertTrue(service.deleteApiKey(7L, "openai"));
        assertEquals(Optional.empty(), service.getApiKey(7L, "openai"));
        assertTrue(service.listEntries(7L).isEmpty());

        // Deletion must be persisted — fresh instance over the same store sees nothing.
        VaultService fresh = newVaultService(fake);
        fresh.unlock(7L, "pass");
        assertEquals(Optional.empty(), fresh.getApiKey(7L, "openai"));
    }

    @Test
    void deleteApiKey_unknownEntry_returnsFalse() {
        VaultService service = newVaultService(new FakeVaultTxService());
        service.unlock(7L, "pass");
        assertFalse(service.deleteApiKey(7L, "nonexistent"));
    }

    @Test
    void deleteApiKey_whenLocked_returnsFalse() {
        VaultService service = newVaultService(new FakeVaultTxService());
        assertFalse(service.deleteApiKey(7L, "any"));
    }

    // ---- verifyPassphrase ------------------------------------------------------------

    @Test
    void verifyPassphrase_correctPass_returnsTrue() {
        VaultService service = newVaultService(new FakeVaultTxService());
        service.unlock(7L, "master-pass");
        assertTrue(service.verifyPassphrase(7L, "master-pass"));
    }

    @Test
    void verifyPassphrase_wrongPass_returnsFalse() {
        VaultService service = newVaultService(new FakeVaultTxService());
        service.unlock(7L, "master-pass");
        assertFalse(service.verifyPassphrase(7L, "wrong"));
    }

    @Test
    void verifyPassphrase_whenLocked_returnsFalse() {
        VaultService service = newVaultService(new FakeVaultTxService());
        assertFalse(service.verifyPassphrase(7L, "anything"));
    }

    @Test
    void verifyPassphrase_blankOrNull_returnsFalse() {
        VaultService service = newVaultService(new FakeVaultTxService());
        service.unlock(7L, "master-pass");
        assertFalse(service.verifyPassphrase(7L, ""));
        assertFalse(service.verifyPassphrase(7L, null));
    }

    // ---- getKeyBytes -----------------------------------------------------------------

    @Test
    void test_getKeyBytes_matchesStringUtf8() {
        VaultService service = newVaultService(new FakeVaultTxService());
        long companyId = 42L;            // never 1L (reserved for MH management company)
        service.unlock(companyId, "pass");
        String code = "openai_api_key";
        String expected = "sk-test-1234567890";
        assertTrue(service.putApiKey(companyId, code, expected));

        Optional<byte[]> opt = service.getKeyBytes(companyId, code);

        assertTrue(opt.isPresent());
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), opt.get());
    }

    @Test
    void test_getKeyBytes_missing() {
        VaultService service = newVaultService(new FakeVaultTxService());
        long companyId = 42L;
        service.unlock(companyId, "pass");
        Optional<byte[]> opt = service.getKeyBytes(companyId, "nonexistent");
        assertTrue(opt.isEmpty());
    }

    @Test
    void test_getKeyBytes_callerZero_noSideEffect() {
        VaultService service = newVaultService(new FakeVaultTxService());
        long companyId = 42L;
        service.unlock(companyId, "pass");
        String code = "k";
        String expected = "secret123";
        assertTrue(service.putApiKey(companyId, code, expected));

        byte[] first = service.getKeyBytes(companyId, code).orElseThrow();
        Arrays.fill(first, (byte) 0);

        byte[] second = service.getKeyBytes(companyId, code).orElseThrow();
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), second);

        // first and second must be different instances
        assertNotSame(first, second);
    }

    @Test
    void test_getKeyBytes_locked() {
        VaultService service = newVaultService(new FakeVaultTxService());
        // do NOT unlock the vault
        Optional<byte[]> opt = service.getKeyBytes(42L, "anything");
        assertTrue(opt.isEmpty());
    }

    // ---- persistence-failure rollback ----------------------------------------------

    @Test
    void putApiKey_persistenceFails_inMemoryStateRolledBack() {
        FakeVaultTxService fake = new FakeVaultTxService();
        fake.failOnCompanyId = 42L;
        VaultService service = newVaultService(fake);
        service.unlock(42L, "pass");

        assertFalse(service.putApiKey(42L, "openai", "v"));
        // After a failed save, the in-memory entries must NOT contain the new value.
        assertEquals(Optional.empty(), service.getApiKey(42L, "openai"));
    }

    @Test
    void deleteApiKey_persistenceFails_inMemoryStateRolledBack() {
        FakeVaultTxService fake = new FakeVaultTxService();
        VaultService service = newVaultService(fake);
        service.unlock(42L, "pass");
        service.putApiKey(42L, "openai", "v");

        fake.failOnCompanyId = 42L;
        assertFalse(service.deleteApiKey(42L, "openai"));
        // After a failed delete persist, the value must still be present in memory.
        assertEquals(Optional.of("v"), service.getApiKey(42L, "openai"));
    }
}
