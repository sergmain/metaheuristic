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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link VaultBootUnlockFileUtils}: real files in a per-test temp {@code mh.home}, no Spring.
 *
 * @author Sergio Lissner
 * Date: 10/2/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class VaultBootUnlockFileUtilsTest {

    private static final String VALUE = "QUJDREVGR0hJSktMTU5PUA==";

    @Test
    public void test_writeThenRead_returnsTheValue(@TempDir Path home) throws Exception {
        VaultBootUnlockFileUtils.writeEncryptedPassphrase(home, VALUE);
        assertEquals(VALUE, VaultBootUnlockFileUtils.readEncryptedPassphrase(home, null));
    }

    @Test
    public void test_write_putsTheFileAtTheRootOfMhHome_notUnderConfig(@TempDir Path home) throws Exception {
        final Path file = VaultBootUnlockFileUtils.writeEncryptedPassphrase(home, VALUE);
        assertEquals(home.resolve("vault-boot-unlock.txt"), file);
        assertEquals(VALUE, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    public void test_write_createsAMissingMhHome(@TempDir Path tmp) throws Exception {
        final Path home = tmp.resolve("not-yet").resolve("mh-home");
        VaultBootUnlockFileUtils.writeEncryptedPassphrase(home, VALUE);
        assertEquals(VALUE, VaultBootUnlockFileUtils.readEncryptedPassphrase(home, null));
    }

    @Test
    public void test_write_replacesThePreviousValue(@TempDir Path home) throws Exception {
        VaultBootUnlockFileUtils.writeEncryptedPassphrase(home, "b2xk");
        VaultBootUnlockFileUtils.writeEncryptedPassphrase(home, VALUE);
        assertEquals(VALUE, VaultBootUnlockFileUtils.readEncryptedPassphrase(home, null));
    }

    @Test
    public void test_write_leavesNoTempFileBehind(@TempDir Path home) throws Exception {
        VaultBootUnlockFileUtils.writeEncryptedPassphrase(home, "b2xk");
        VaultBootUnlockFileUtils.writeEncryptedPassphrase(home, VALUE);
        try (var files = Files.list(home)) {
            assertEquals(List.of(home.resolve(VaultBootUnlockFileUtils.BOOT_UNLOCK_FILE)), files.toList(),
                "the temp file used for the atomic replace must not stay in mh.home");
        }
    }

    @Test
    public void test_read_fileWinsOverTheProperty(@TempDir Path home) throws Exception {
        VaultBootUnlockFileUtils.writeEncryptedPassphrase(home, VALUE);
        assertEquals(VALUE, VaultBootUnlockFileUtils.readEncryptedPassphrase(home, "cHJvcGVydHk="),
            "the Key Vault page writes the file, so a stale property must not override it");
    }

    @Test
    public void test_read_withoutFile_fallsBackToTheProperty(@TempDir Path home) throws Exception {
        assertEquals("cHJvcGVydHk=", VaultBootUnlockFileUtils.readEncryptedPassphrase(home, " cHJvcGVydHk= "));
    }

    @Test
    public void test_read_blankFileCountsAsAbsent(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve(VaultBootUnlockFileUtils.BOOT_UNLOCK_FILE), " \n", StandardCharsets.UTF_8);
        assertEquals("cHJvcGVydHk=", VaultBootUnlockFileUtils.readEncryptedPassphrase(home, "cHJvcGVydHk="));
    }

    @Test
    public void test_read_neitherSource_isNotConfigured(@TempDir Path home) throws Exception {
        assertNull(VaultBootUnlockFileUtils.readEncryptedPassphrase(home, null));
        assertNull(VaultBootUnlockFileUtils.readEncryptedPassphrase(home, "  "));
    }

    @Test
    public void test_read_stripsTheTrailingNewlineAnEditorAdds(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve(VaultBootUnlockFileUtils.BOOT_UNLOCK_FILE), VALUE + "\r\n", StandardCharsets.UTF_8);
        assertEquals(VALUE, VaultBootUnlockFileUtils.readEncryptedPassphrase(home, null));
    }

    @Test
    public void test_write_isOwnerOnlyWherePosixPermissionsExist(@TempDir Path home) throws Exception {
        assumeTrue(home.getFileSystem().supportedFileAttributeViews().contains("posix"), "no POSIX permissions on this file system");
        final Path file = VaultBootUnlockFileUtils.writeEncryptedPassphrase(home, VALUE);
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
    }

    /** The whole value path without Spring: encrypt with the KEK, write, read back at "start", decrypt. */
    @Test
    public void test_encryptWriteReadDecrypt_recoversThePassphrase(@TempDir Path home) throws Exception {
        final byte[] k = new byte[VaultBootUnlockUtils.KEK_LEN];
        new SecureRandom().nextBytes(k);
        final String kek = Base64.getEncoder().encodeToString(k);

        VaultBootUnlockFileUtils.writeEncryptedPassphrase(home, VaultBootUnlockUtils.encryptPassphrase(kek, "master pp", 1L));
        final String read = VaultBootUnlockFileUtils.readEncryptedPassphrase(home, null);

        assertNotNull(read);
        assertEquals("master pp", VaultBootUnlockUtils.decryptPassphrase(kek, read, 1L));
    }

    @Test
    public void test_describeFile_absent(@TempDir Path home) {
        final String d = VaultBootUnlockFileUtils.describeFile(home);
        assertTrue(d.endsWith("vault-boot-unlock.txt does NOT exist"), d);
        assertTrue(d.startsWith(home.toAbsolutePath().toString()), "the log must show which mh.home was looked at: " + d);
    }

    @Test
    public void test_describeFile_blank(@TempDir Path home) throws Exception {
        Files.writeString(home.resolve(VaultBootUnlockFileUtils.BOOT_UNLOCK_FILE), " \n", StandardCharsets.UTF_8);
        assertTrue(VaultBootUnlockFileUtils.describeFile(home).endsWith(" exists but is blank"));
    }

    @Test
    public void test_describeFile_present_showsSizeNeverContent(@TempDir Path home) throws Exception {
        VaultBootUnlockFileUtils.writeEncryptedPassphrase(home, VALUE);
        final String d = VaultBootUnlockFileUtils.describeFile(home);
        assertTrue(d.endsWith(" exists, " + VALUE.length() + " bytes"), d);
        assertFalse(d.contains(VALUE));
    }
}
