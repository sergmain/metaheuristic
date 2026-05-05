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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests for VaultService — no Spring context. Drives a real
 * {@link org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase} on a temp directory
 * to keep tests integration-style honest without the test containers/H2 etc.
 *
 * @author Sergio Lissner
 */
class VaultServiceTest {

    @TempDir
    Path mhHome;

    private VaultService service;

    @BeforeEach
    void setUp() throws Exception {
        Globals globals = new Globals();
        globals.home = mhHome;
        // Mirror Globals#postConstruct() bare minimum — we only need dispatcherPath.
        Path dispatcherPath = mhHome.resolve("dispatcher");
        Files.createDirectories(dispatcherPath);
        globals.dispatcherPath = dispatcherPath;
        service = new VaultService(globals);
    }

    @Test
    void initialState_isClosed() {
        assertFalse(service.isOpened(), "Newly created service must be locked");
        assertEquals(Optional.empty(), service.getApiKey(1L, "any"));
    }

    @Test
    void unlock_withMissingFile_createsEmptyVault() {
        VaultData.UnlockResult r = service.unlock("master-pass-1");
        assertTrue(r.errorMessages == null || r.errorMessages.isEmpty(),
                "Expected no errors, got: " + r.errorMessages);
        assertTrue(r.opened);
        assertTrue(r.created, "Vault file did not exist; service must report created=true");
        assertTrue(service.isOpened());
        assertTrue(Files.exists(mhHome.resolve("dispatcher/vault/vault.kdbx")));
    }

    @Test
    void unlock_existingFile_correctPassphrase_opensWithoutCreate() {
        // Arrange: create the vault once
        assertTrue(service.unlock("master-pass-2").opened);
        // New service instance over the same directory, simulating a JVM restart
        Globals globals = new Globals();
        globals.home = mhHome;
        globals.dispatcherPath = mhHome.resolve("dispatcher");
        VaultService freshService = new VaultService(globals);

        // Act
        VaultData.UnlockResult r = freshService.unlock("master-pass-2");

        // Assert
        assertTrue(r.opened);
        assertFalse(r.created, "Vault file existed; service must report created=false");
    }

    @Test
    void unlock_existingFile_wrongPassphrase_returnsError() {
        assertTrue(service.unlock("correct-pass").opened);
        Globals globals = new Globals();
        globals.home = mhHome;
        globals.dispatcherPath = mhHome.resolve("dispatcher");
        VaultService freshService = new VaultService(globals);

        VaultData.UnlockResult r = freshService.unlock("wrong-pass");

        assertFalse(r.opened);
        assertFalse(freshService.isOpened());
        assertNotNull(r.errorMessages);
        assertFalse(r.errorMessages.isEmpty());
    }

    @Test
    void unlock_blankPassphrase_rejected() {
        VaultData.UnlockResult r = service.unlock("");
        assertFalse(r.opened);
        assertFalse(service.isOpened());
        assertNotNull(r.errorMessages);
    }

    @Test
    void getApiKey_afterPut_returnsValue() {
        service.unlock("pass");
        assertTrue(service.putApiKey(42L, "openai", "sk-test-value-1", "pass"));
        assertEquals(Optional.of("sk-test-value-1"), service.getApiKey(42L, "openai"));
    }

    @Test
    void getApiKey_unknownCode_returnsEmpty() {
        service.unlock("pass");
        assertEquals(Optional.empty(), service.getApiKey(42L, "nonexistent"));
    }

    @Test
    void getApiKey_isolatesByAccountId() {
        service.unlock("pass");
        service.putApiKey(1L, "openai", "tenant-1-secret", "pass");
        service.putApiKey(2L, "openai", "tenant-2-secret", "pass");

        assertEquals(Optional.of("tenant-1-secret"), service.getApiKey(1L, "openai"));
        assertEquals(Optional.of("tenant-2-secret"), service.getApiKey(2L, "openai"));
        assertEquals(Optional.empty(), service.getApiKey(3L, "openai"));
    }

    @Test
    void putApiKey_overwritesExistingEntry() {
        service.unlock("pass");
        service.putApiKey(1L, "openai", "old-value", "pass");
        service.putApiKey(1L, "openai", "new-value", "pass");

        assertEquals(Optional.of("new-value"), service.getApiKey(1L, "openai"));
    }

    @Test
    void getApiKey_whenLocked_returnsEmpty() {
        // service was never unlocked
        assertEquals(Optional.empty(), service.getApiKey(1L, "openai"));
    }

    @Test
    void putApiKey_whenLocked_returnsFalse() {
        assertFalse(service.putApiKey(1L, "openai", "value", "pass"));
    }

    @Test
    void persistedEntry_visibleAfterReopen() {
        // Arrange: write through one instance
        assertTrue(service.unlock("master").opened);
        assertTrue(service.putApiKey(7L, "openai", "persisted-value", "master"));

        // Act: simulate restart with a fresh service over the same files
        Globals globals = new Globals();
        globals.home = mhHome;
        globals.dispatcherPath = mhHome.resolve("dispatcher");
        VaultService fresh = new VaultService(globals);
        assertTrue(fresh.unlock("master").opened);

        // Assert
        assertEquals(Optional.of("persisted-value"), fresh.getApiKey(7L, "openai"));
    }

    @Test
    void entryTitle_format_isAccountIdColonCode() {
        assertEquals("42:openai", VaultService.entryTitle(42L, "openai"));
    }
}
