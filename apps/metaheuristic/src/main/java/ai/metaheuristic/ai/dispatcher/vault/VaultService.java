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
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.linguafranca.pwdb.Credentials;
import org.linguafranca.pwdb.Database;
import org.linguafranca.pwdb.Entry;
import org.linguafranca.pwdb.Group;
import org.linguafranca.pwdb.format.KdbxCreds;
import org.linguafranca.pwdb.kdbx.jackson.KdbxDatabase;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Vault service backed by KeePass (KDBX) file storage — KeePassJava2 v3 API.
 *
 * <p>Stores API keys per-tenant. Each entry's title encodes {@code accountId:code}
 * and the entry's password field contains the secret key value.
 *
 * <p>Lifetime: the unlocked database is held as a global singleton in dispatcher
 * memory until JVM restart. The master passphrase is never persisted.
 *
 * <p>Vault file location: {@code {mh.home}/dispatcher/vault/vault.kdbx}.
 * If the file does not exist on first unlock, an empty KDBX is auto-created
 * using the supplied passphrase.
 *
 * @author Sergio Lissner
 */
@Service
@Slf4j
@Profile("dispatcher")
public class VaultService {

    public static final String VAULT_DIR = "vault";
    public static final String VAULT_FILE = "vault.kdbx";

    private final Globals globals;

    /** Held in memory after unlock; null when locked. Volatile for safe publication. */
    @Nullable
    private volatile Database database;

    /** Cached path to the KDBX file. */
    @Nullable
    private volatile Path vaultPath;

    public VaultService(Globals globals) {
        this.globals = globals;
    }

    /** @return whether the vault is currently unlocked in dispatcher memory. */
    public boolean isOpened() {
        return database != null;
    }

    /**
     * Resolve the API key for a given tenant + code.
     * Returns empty if the vault is locked or the entry does not exist.
     */
    public Optional<String> getApiKey(long accountId, String code) {
        Database db = this.database;
        if (db == null) {
            return Optional.empty();
        }
        return findEntry(db, accountId, code).map(Entry::getPassword);
    }

    /**
     * Unlock the vault. If the KDBX file does not exist, an empty one is created
     * using {@code passphrase} as the master password.
     *
     * @param passphrase master password (must not be blank)
     * @return result with {@code opened} and {@code created} flags, or an error
     */
    public synchronized VaultData.UnlockResult unlock(String passphrase) {
        if (passphrase == null || passphrase.isBlank()) {
            return new VaultData.UnlockResult("Passphrase must not be blank");
        }
        try {
            Path path = resolveVaultFile();
            Credentials creds = new KdbxCreds(passphrase.getBytes(StandardCharsets.UTF_8));

            boolean created = false;
            Database db;
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    db = KdbxDatabase.load(creds, in);
                }
            } else {
                db = createEmptyDatabase(path, creds);
                created = true;
            }
            this.database = db;
            this.vaultPath = path;
            log.info("Vault unlocked (created={})", created);
            return new VaultData.UnlockResult(true, created);
        } catch (Exception e) {
            log.error("Failed to unlock vault: {}", e.getMessage());
            // Do not leak details about why unlock failed (wrong passphrase vs IO error).
            return new VaultData.UnlockResult("Unable to open vault");
        }
    }

    /**
     * Static helper, package-private, for unit testing the entry lookup logic
     * without a real KeePass database wrapped in Spring context.
     */
    static Optional<? extends Entry> findEntry(Database db, long accountId, String code) {
        String title = entryTitle(accountId, code);
        // findEntries() walks the whole database recursively
        return db.findEntries(title).stream()
                .filter(e -> title.equals(e.getTitle()))
                .findFirst();
    }

    /** Title format used inside KeePass: {@code accountId:code}. */
    static String entryTitle(long accountId, String code) {
        return accountId + ":" + code;
    }

    private Database createEmptyDatabase(Path path, Credentials creds) throws IOException {
        Files.createDirectories(path.getParent());
        KdbxDatabase db = new KdbxDatabase();
        db.setName("Metaheuristic Vault");
        try (OutputStream out = Files.newOutputStream(path)) {
            db.save(creds, out);
        }
        return db;
    }

    private Path resolveVaultFile() {
        return globals.dispatcherPath.resolve(VAULT_DIR).resolve(VAULT_FILE);
    }

    /**
     * Visible for tests: clear in-memory state. Does not delete the file on disk.
     */
    void resetForTests() {
        this.database = null;
        this.vaultPath = null;
    }

    /**
     * Visible for tests: inject an already-unlocked database (used by unit tests
     * that exercise the lookup path without performing a real unlock).
     */
    void setDatabaseForTests(Database db) {
        this.database = db;
    }

    /**
     * Add or replace an entry under the given accountId+code with the supplied secret
     * and persist the database. Synchronised to avoid concurrent writers corrupting
     * the on-disk file.
     *
     * @return true if persisted, false if the vault is locked or persistence failed
     */
    public synchronized boolean putApiKey(long accountId, String code, String secret, String passphrase) {
        Database db = this.database;
        Path path = this.vaultPath;
        if (db == null || path == null) {
            return false;
        }
        try {
            String title = entryTitle(accountId, code);
            Optional<? extends Entry> existing = findEntry(db, accountId, code);
            Entry entry;
            if (existing.isPresent()) {
                entry = existing.get();
            } else {
                entry = db.newEntry();
                entry.setTitle(title);
                Group root = db.getRootGroup();
                root.addEntry(entry);
            }
            entry.setPassword(secret);
            try (OutputStream out = Files.newOutputStream(path)) {
                db.save(new KdbxCreds(passphrase.getBytes(StandardCharsets.UTF_8)), out);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to write entry {}:{}: {}", accountId, code, e.getMessage());
            return false;
        }
    }
}
