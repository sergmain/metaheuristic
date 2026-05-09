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

import java.nio.file.Files;
import java.nio.file.Path;
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
        assertTrue(service.putApiKey(42L, "openai", "sk-test-value-1", "pass"));
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
        service.putApiKey(2L, "openai", "tenant-2-secret", "pass");
        service.putApiKey(7L, "openai", "tenant-7-secret", "pass");

        assertEquals(Optional.of("tenant-2-secret"), service.getApiKey(2L, "openai"));
        assertEquals(Optional.of("tenant-7-secret"), service.getApiKey(7L, "openai"));
        assertEquals(Optional.empty(), service.getApiKey(3L, "openai"));
    }

    @Test
    void putApiKey_overwritesExistingEntry(@TempDir Path tempPath) throws Exception {
        VaultService service = newVaultService(tempPath);
        service.unlock("pass");
        service.putApiKey(2L, "openai", "old-value", "pass");
        service.putApiKey(2L, "openai", "new-value", "pass");

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
        assertFalse(service.putApiKey(2L, "openai", "value", "pass"));
    }

    @Test
    void persistedEntry_visibleAfterReopen(@TempDir Path tempPath) throws Exception {
        // Arrange: write through one instance
        VaultService service = newVaultService(tempPath);
        assertTrue(service.unlock("master").opened);
        assertTrue(service.putApiKey(7L, "openai", "persisted-value", "master"));

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
        assertTrue(service.putApiKey(7L, "openai", "v", "master"));

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
}
