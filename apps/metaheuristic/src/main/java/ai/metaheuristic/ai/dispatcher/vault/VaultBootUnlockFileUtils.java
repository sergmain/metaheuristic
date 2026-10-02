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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * The encrypted Vault passphrase for boot-unlock, kept in {@code ${mh.home}/vault-boot-unlock.txt}.
 *
 * <p>The Dispatcher writes it when the management company's admin presses "Auto-unlock at restart" on the
 * Key Vault page, and reads it at start - nothing has to be put into application.properties by hand.
 *
 * <p>❗ Same place and same handling as {@code installation-id.txt}: the ROOT of {@code mh.home}, not
 * {@code mh.home/config}. {@code config} is operator input and deployments may mount it read-only; this file
 * is state the Dispatcher writes itself.
 *
 * <p>The file holds ciphertext only. Without the KEK from the environment it opens nothing.
 *
 * <p>⚠️ Unlike installation-id.txt, whose value lives in the database and is only mirrored to the file, this
 * file IS the value: an {@code mh.home} that does not survive a redeploy loses auto-unlock with it.
 *
 * @author Sergio Lissner
 * Date: 10/2/2026
 */
public final class VaultBootUnlockFileUtils {

    public static final String BOOT_UNLOCK_FILE = "vault-boot-unlock.txt";

    private VaultBootUnlockFileUtils() {
        // utility class
    }

    /**
     * The file wins - it is what the Key Vault page writes. The property is the fallback for a box configured by
     * hand before the file existed. A blank file counts as absent.
     *
     * @param home         {@code mh.home}
     * @param fromProperty value of {@code mh.dispatcher.vault.boot-unlock.encrypted-passphrase}
     * @return the Base64 ciphertext, or null when neither source has one
     */
    public static @Nullable String readEncryptedPassphrase(Path home, @Nullable String fromProperty) throws IOException {
        final Path file = home.resolve(BOOT_UNLOCK_FILE);
        if (Files.exists(file)) {
            final String fromFile = Files.readString(file, StandardCharsets.UTF_8).strip();
            if (!fromFile.isEmpty()) {
                return fromFile;
            }
        }
        return fromProperty == null || fromProperty.isBlank() ? null : fromProperty.strip();
    }

    /**
     * Write (or replace) the file. Owner-only permissions where the file system has POSIX permissions.
     *
     * <p>Written to a temp file next to the target and moved over it in one step: a crash mid-write leaves the
     * previous file intact instead of a truncated one, which would fail to decrypt at the next start. The temp
     * file is created owner-only, so the content is never readable by others, not even for a moment.
     *
     * @return the file written
     */
    public static Path writeEncryptedPassphrase(Path home, String encryptedB64) throws IOException {
        Files.createDirectories(home);
        final Path file = home.resolve(BOOT_UNLOCK_FILE);
        final Path tmp = home.getFileSystem().supportedFileAttributeViews().contains("posix")
            ? Files.createTempFile(home, BOOT_UNLOCK_FILE, ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            : Files.createTempFile(home, BOOT_UNLOCK_FILE, ".tmp");
        try {
            Files.writeString(tmp, encryptedB64, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        finally {
            Files.deleteIfExists(tmp);
        }
        return file;
    }
}
