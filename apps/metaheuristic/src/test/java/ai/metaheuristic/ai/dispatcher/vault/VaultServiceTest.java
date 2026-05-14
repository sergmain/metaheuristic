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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.data.VaultData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Plain unit tests for VaultService — no Spring context. Drives a real
 * AES/GCM-encrypted vault file on a temp directory to keep tests
 * integration-style honest without test containers / H2 / etc.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class VaultServiceTest {

    /**
     * Build a VaultService rooted at the given mh.home directory.
     * Each test gets its own {@code @TempDir Path tempPath} parameter so that
     * tests are isolated from each other without any shared state on the test class.
     */
    private static VaultService newVaultService(Path mhHome) throws Exception {
        Globals globals = new Globals();
        globals.home = mhHome;
        // Mirror Globals#postConstruct() bare minimum — we only need dispatcherPath.
        Path dispatcherPath = mhHome.resolve("dispatcher");
        Files.createDirectories(dispatcherPath);
        globals.dispatcherPath = dispatcherPath;
        return new VaultService(globals);
    }

    @Test
    void initialState_isClosed(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        assertFalse(service.isOpened(), "Newly created service must be locked");
        assertEquals(Optional.empty(), service.getApiKey(1L, "any"));
    }

    @Test
    void unlock_withMissingFile_createsEmptyVault(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        VaultData.UnlockResult r = service.unlock("master-pass-1");
        assertTrue(r.errorMessages == null || r.errorMessages.isEmpty(),
                "Expected no errors, got: " + r.errorMessages);
        assertTrue(r.opened);
        assertTrue(r.created, "Vault file did not exist; service must report created=true");
        assertTrue(service.isOpened());
        assertTrue(Files.exists(tempPath.resolve("dispatcher/vault/mh.vault")));
    }

    @Test
    void unlock_existingFile_correctPassphrase_opensWithoutCreate(@TempDir Path tempPath) throws Exception {
        // Arrange: create the vault once
        VaultService service = newVaultService(tempPath);
        assertTrue(service.unlock("master-pass-2").opened);

        // New service instance over the same directory, simulating a JVM restart
        VaultService freshService = newVaultService(tempPath);

        // Act
        VaultData.UnlockResult r = freshService.unlock("master-pass-2");

        // Assert
        assertTrue(r.opened);
        assertFalse(r.created, "Vault file existed; service must report created=false");
    }

    @Test
    void unlock_existingFile_wrongPassphrase_returnsError(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        assertTrue(service.unlock("correct-pass").opened);

        VaultService freshService = newVaultService(tempPath);
        VaultData.UnlockResult r = freshService.unlock("wrong-pass");

        assertFalse(r.opened);
        assertFalse(freshService.isOpened());
        assertNotNull(r.errorMessages);
        assertFalse(r.errorMessages.isEmpty());
    }

    @Test
    void unlock_blankPassphrase_rejected(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        VaultData.UnlockResult r = service.unlock("");
        assertFalse(r.opened);
        assertFalse(service.isOpened());
        assertNotNull(r.errorMessages);
    }

    @Test
    void getApiKey_afterPut_returnsValue(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        assertTrue(service.putApiKey(42L, "openai", "sk-test-value-1"));
        assertEquals(Optional.of("sk-test-value-1"), service.getApiKey(42L, "openai"));
    }

    @Test
    void getApiKey_unknownCode_returnsEmpty(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        assertEquals(Optional.empty(), service.getApiKey(42L, "nonexistent"));
    }

    @Test
    void getApiKey_isolatesByAccountId(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        service.putApiKey(2L, "openai", "tenant-2-secret");
        service.putApiKey(7L, "openai", "tenant-7-secret");

        assertEquals(Optional.of("tenant-2-secret"), service.getApiKey(2L, "openai"));
        assertEquals(Optional.of("tenant-7-secret"), service.getApiKey(7L, "openai"));
        assertEquals(Optional.empty(), service.getApiKey(3L, "openai"));
    }

    @Test
    void putApiKey_overwritesExistingEntry(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        service.putApiKey(2L, "openai", "old-value");
        service.putApiKey(2L, "openai", "new-value");

        assertEquals(Optional.of("new-value"), service.getApiKey(2L, "openai"));
    }

    @Test
    void getApiKey_whenLocked_returnsEmpty(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        // service was never unlocked
        assertEquals(Optional.empty(), service.getApiKey(2L, "openai"));
    }

    @Test
    void putApiKey_whenLocked_returnsFalse(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        assertFalse(service.putApiKey(2L, "openai", "value"));
    }

    @Test
    void persistedEntry_visibleAfterReopen(@TempDir Path tempPath) throws Exception {
        // Arrange: write through one instance
        VaultService service = newVaultService(tempPath);
        assertTrue(service.unlock("master").opened);
        assertTrue(service.putApiKey(7L, "openai", "persisted-value"));

        // Act: simulate restart with a fresh service over the same files
        VaultService fresh = newVaultService(tempPath);
        assertTrue(fresh.unlock("master").opened);

        // Assert
        assertEquals(Optional.of("persisted-value"), fresh.getApiKey(7L, "openai"));
    }

    @Test
    void corruptFile_unlockFails(@TempDir Path tempPath) throws Exception {
        // Create a real vault, then truncate it so the magic header is broken.
        VaultService service = newVaultService(tempPath);
        assertTrue(service.unlock("master").opened);

        Path vaultFile = tempPath.resolve("dispatcher/vault/mh.vault");
        assertTrue(Files.exists(vaultFile));
        Files.write(vaultFile, new byte[]{0, 1, 2, 3, 4, 5});

        VaultService fresh = newVaultService(tempPath);
        VaultData.UnlockResult r = fresh.unlock("master");
        assertFalse(r.opened);
        assertFalse(fresh.isOpened());
    }

    @Test
    void tamperedHeader_unlockFails(@TempDir Path tempPath) throws Exception {
        // Header bytes (magic|iterations|salt) are bound as GCM AAD.
        // Flipping the iteration count in the on-disk file must fail authentication.
        VaultService service = newVaultService(tempPath);
        assertTrue(service.unlock("master").opened);
        assertTrue(service.putApiKey(7L, "openai", "v"));

        Path vaultFile = tempPath.resolve("dispatcher/vault/mh.vault");
        byte[] file = Files.readAllBytes(vaultFile);
        // iterations is the int at offset 4 (right after MAGIC); flip a bit in it.
        file[4] ^= 0x01;
        Files.write(vaultFile, file);

        VaultService fresh = newVaultService(tempPath);
        VaultData.UnlockResult r = fresh.unlock("master");
        assertFalse(r.opened, "Tampering with header bytes must fail GCM authentication");
        assertFalse(fresh.isOpened());
    }

    @Test
    void entryTitle_format_isAccountIdColonCode() {
        assertEquals(VaultService.entryTitle(42L, "openai"), "42:openai");
    }

    // ---- listEntries -----------------------------------------------------------------

    @Test
    void listEntries_whenLocked_returnsEmptyList(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        assertTrue(service.listEntries(7L).isEmpty());
    }

    @Test
    void listEntries_emptyVault_returnsEmptyList(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        assertTrue(service.listEntries(7L).isEmpty());
    }

    @Test
    void listEntries_returnsTitlesAndSecrets_inInsertionOrder(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        service.putApiKey(7L, "openai", "secret-1");
        service.putApiKey(2L, "anthropic", "secret-2");
        service.putApiKey(7L, "anthropic", "secret-3");

        var entries = service.listEntries(7L);
        assertEquals(2, entries.size());
        assertEquals(7L, entries.get(0).accountId());
        assertEquals("openai", entries.get(0).code());
        assertEquals("secret-1", entries.get(0).secret());
        assertEquals(7L, entries.get(1).accountId());
        assertEquals("anthropic", entries.get(1).code());
        assertEquals("secret-3", entries.get(1).secret());
    }

    @Test
    void listEntries_filtersOutOtherAccounts(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        service.putApiKey(7L, "openai", "tenant-7-secret");
        service.putApiKey(2L, "openai", "tenant-2-secret");

        var entriesFor2 = service.listEntries(2L);
        assertEquals(1, entriesFor2.size());
        assertEquals(2L, entriesFor2.get(0).accountId());
        assertEquals("tenant-2-secret", entriesFor2.get(0).secret());

        var entriesFor7 = service.listEntries(7L);
        assertEquals(1, entriesFor7.size());
        assertEquals(7L, entriesFor7.get(0).accountId());
    }

    @Test
    void listEntries_unknownAccount_returnsEmpty(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        service.putApiKey(7L, "openai", "v");
        assertTrue(service.listEntries(99L).isEmpty());
    }

    // ---- deleteApiKey ----------------------------------------------------------------

    @Test
    void deleteApiKey_existingEntry_removesAndPersists(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        service.putApiKey(7L, "openai", "v");
        assertTrue(service.deleteApiKey(7L, "openai"));
        assertEquals(Optional.empty(), service.getApiKey(7L, "openai"));
        assertTrue(service.listEntries(7L).isEmpty());

        // Deletion must be persisted
        VaultService fresh = newVaultService(tempPath);
        fresh.unlock("pass");
        assertEquals(Optional.empty(), fresh.getApiKey(7L, "openai"));
    }

    @Test
    void deleteApiKey_unknownEntry_returnsFalse(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        assertFalse(service.deleteApiKey(7L, "nonexistent"));
    }

    @Test
    void deleteApiKey_whenLocked_returnsFalse(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        assertFalse(service.deleteApiKey(7L, "any"));
    }

    // ---- verifyPassphrase ------------------------------------------------------------

    @Test
    void verifyPassphrase_correctPass_returnsTrue(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("master-pass");
        assertTrue(service.verifyPassphrase("master-pass"));
    }

    @Test
    void verifyPassphrase_wrongPass_returnsFalse(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("master-pass");
        assertFalse(service.verifyPassphrase("wrong"));
    }

    @Test
    void verifyPassphrase_whenLocked_returnsFalse(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        assertFalse(service.verifyPassphrase("anything"));
    }

    @Test
    void verifyPassphrase_blankOrNull_returnsFalse(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("master-pass");
        assertFalse(service.verifyPassphrase(""));
        assertFalse(service.verifyPassphrase(null));
    }

    // ---- getKeyBytes -----------------------------------------------------------------

    @Test
    void test_getKeyBytes_matchesStringUtf8(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        long accountId = 42L;            // never 1L
        String code = "openai_api_key";
        String expected = "sk-test-1234567890";
        assertTrue(service.putApiKey(accountId, code, expected));

        Optional<byte[]> opt = service.getKeyBytes(accountId, code);

        assertTrue(opt.isPresent());
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), opt.get());
    }

    @Test
    void test_getKeyBytes_missing(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        long accountId = 42L;
        Optional<byte[]> opt = service.getKeyBytes(accountId, "nonexistent");
        assertTrue(opt.isEmpty());
    }

    @Test
    void test_getKeyBytes_callerZero_noSideEffect(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        long accountId = 42L;
        String code = "k";
        String expected = "secret123";
        assertTrue(service.putApiKey(accountId, code, expected));

        byte[] first = service.getKeyBytes(accountId, code).orElseThrow();
        Arrays.fill(first, (byte) 0);

        byte[] second = service.getKeyBytes(accountId, code).orElseThrow();
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), second);

        // first and second must be different instances
        assertNotSame(first, second);
    }

    @Test
    void test_getKeyBytes_locked(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        // do NOT unlock the vault
        Optional<byte[]> opt = service.getKeyBytes(42L, "anything");
        assertTrue(opt.isEmpty());
    }
}
