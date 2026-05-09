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
import ai.metaheuristic.commons.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Vault service backed by an AES/GCM-encrypted JSON file.
 *
 * <p>Stores API keys per-tenant in a single JSON map: the key is
 * {@code accountId:code} and the value is the secret. The whole map is
 * encrypted as one blob via {@link JsonCrypto}.
 *
 * <p>Lifetime: the unlocked plaintext map is held as a global singleton in
 * dispatcher memory until JVM restart. The master passphrase is never persisted.
 *
 * <p>Vault file location: {@code {mh.home}/dispatcher/vault/mh.vault}.
 * If the file does not exist on first unlock, an empty vault is auto-created
 * using the supplied passphrase.
 *
 * <p>File layout:
 * <pre>
 *   [4 bytes magic "MHV1"]
 *   [4 bytes int iterations  (PBKDF2)]
 *   [16 bytes salt]
 *   [12 bytes IV || ciphertext || 16 bytes GCM auth tag]
 * </pre>
 *
 * <p>The header bytes (magic | iterations | salt) are passed as AAD to GCM,
 * so any tampering with header fields — flipping iterations, swapping in a
 * different salt, etc. — fails authentication on decrypt.
 *
 * <p>Plaintext payload is JSON: {@code {"42:openai": "sk-...", ...}}.
 *
 * @author Sergio Lissner
 */
@Service
@Slf4j
@Profile("dispatcher")
public class VaultService {

    public static final String VAULT_DIR = "vault";
    public static final String VAULT_FILE = "mh.vault";

    /** File-format magic to detect a valid vault file early. */
    static final byte[] MAGIC = new byte[] {'M', 'H', 'V', '1'};

    /** PBKDF2 iteration count used for new vaults. Existing vaults reuse the value stored in the file. */
    static final int PBKDF2_ITERATIONS = 200_000;
    static final int SALT_LEN = 16;
    /** AES-256 key. */
    static final int KEY_LEN_BITS = 256;
    /** Length of the file header that is also bound as GCM AAD. */
    static final int HEADER_LEN = MAGIC.length + 4 + SALT_LEN;

    private static final SecureRandom RNG = new SecureRandom();

    private final Globals globals;

    /** Held in memory after unlock; null when locked. Volatile for safe publication. */
    @Nullable
    private volatile Map<String, String> entries;

    /** Cached path to the vault file. */
    @Nullable
    private volatile Path vaultPath;

    /** Salt of the currently unlocked vault — needed to re-derive the key for writes. */
    @Nullable
    private volatile byte[] saltInUse;

    /** Iteration count of the currently unlocked vault. */
    private volatile int iterationsInUse;

    public VaultService(Globals globals) {
        this.globals = globals;
    }

    /** @return whether the vault is currently unlocked in dispatcher memory. */
    public boolean isOpened() {
        return entries != null;
    }

    /**
     * Resolve the API key for a given tenant + code.
     * Returns empty if the vault is locked or the entry does not exist.
     */
    public Optional<String> getApiKey(long accountId, String code) {
        Map<String, String> map = this.entries;
        if (map == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(entryTitle(accountId, code)));
    }

    /**
     * Unlock the vault. If the vault file does not exist, an empty one is created
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
            boolean created = false;
            Map<String, String> map;
            byte[] salt;
            int iterations;

            if (Files.exists(path)) {
                byte[] file = Files.readAllBytes(path);
                ParsedFile parsed = parseFile(file);
                byte[] key = deriveKey(passphrase, parsed.salt, parsed.iterations);
                try {
                    String json = JsonCrypto.decrypt(key, parsed.ciphertext, parsed.header);
                    map = readEntries(json);
                } finally {
                    Arrays.fill(key, (byte) 0);
                }
                salt = parsed.salt;
                iterations = parsed.iterations;
            } else {
                Files.createDirectories(path.getParent());
                salt = new byte[SALT_LEN];
                RNG.nextBytes(salt);
                iterations = PBKDF2_ITERATIONS;
                map = new LinkedHashMap<>();
                byte[] key = deriveKey(passphrase, salt, iterations);
                try {
                    writeFile(path, salt, iterations, key, map);
                } finally {
                    Arrays.fill(key, (byte) 0);
                }
                created = true;
            }

            this.entries = map;
            this.vaultPath = path;
            this.saltInUse = salt;
            this.iterationsInUse = iterations;
            log.info("Vault unlocked (created={})", created);
            return new VaultData.UnlockResult(true, created);
        } catch (Exception e) {
            log.error("Failed to unlock vault: {}", e.getMessage());
            // Do not leak details about why unlock failed (wrong passphrase vs IO error).
            return new VaultData.UnlockResult("Unable to open vault");
        }
    }

    /**
     * Add or replace an entry under the given accountId+code with the supplied secret
     * and persist the vault. Synchronised to avoid concurrent writers corrupting
     * the on-disk file.
     *
     * @return true if persisted, false if the vault is locked or persistence failed
     */
    public synchronized boolean putApiKey(long accountId, String code, String secret, String passphrase) {
        Map<String, String> map = this.entries;
        Path path = this.vaultPath;
        byte[] salt = this.saltInUse;
        int iterations = this.iterationsInUse;
        if (map == null || path == null || salt == null) {
            return false;
        }
        byte[] key = null;
        try {
            map.put(entryTitle(accountId, code), secret);
            key = deriveKey(passphrase, salt, iterations);
            writeFile(path, salt, iterations, key, map);
            return true;
        } catch (Exception e) {
            log.error("Failed to write entry {}:{}: {}", accountId, code, e.getMessage());
            return false;
        } finally {
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
        }
    }

    /** Title format used inside the vault map: {@code accountId:code}. */
    static String entryTitle(long accountId, String code) {
        return accountId + ":" + code;
    }

    /** Derive a 256-bit AES key from a passphrase using PBKDF2-HMAC-SHA256. */
    static byte[] deriveKey(String passphrase, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_LEN_BITS);
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    /** Build the on-disk header bytes (also used as GCM AAD). */
    static byte[] buildHeader(byte[] salt, int iterations) {
        ByteBuffer h = ByteBuffer.allocate(HEADER_LEN);
        h.put(MAGIC);
        h.putInt(iterations);
        h.put(salt);
        return h.array();
    }

    /** Parsed file frame: header + ciphertext blob ready for {@link JsonCrypto#decrypt}. */
    record ParsedFile(byte[] salt, int iterations, byte[] ciphertext, byte[] header) {}

    /** Parse the on-disk frame: magic | iterations | salt | encrypted-payload. */
    static ParsedFile parseFile(byte[] file) throws IOException {
        if (file.length < HEADER_LEN) {
            throw new IOException("Vault file too short");
        }
        ByteBuffer buf = ByteBuffer.wrap(file);
        byte[] magic = new byte[MAGIC.length];
        buf.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("Bad vault magic");
        }
        int iterations = buf.getInt();
        if (iterations <= 0) {
            throw new IOException("Bad iteration count");
        }
        byte[] salt = new byte[SALT_LEN];
        buf.get(salt);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);
        byte[] header = Arrays.copyOfRange(file, 0, HEADER_LEN);
        return new ParsedFile(salt, iterations, ciphertext, header);
    }

    /** Encrypt {@code map} as JSON and atomically write the framed file. */
    static void writeFile(Path path, byte[] salt, int iterations, byte[] key, Map<String, String> map) throws IOException, GeneralSecurityException {
        String json = JsonUtils.getMapper().writeValueAsString(map);
        byte[] header = buildHeader(salt, iterations);
        byte[] encrypted = JsonCrypto.encrypt(key, json, header);

        ByteBuffer out = ByteBuffer.allocate(HEADER_LEN + encrypted.length);
        out.put(header);
        out.put(encrypted);

        // Atomic-ish write: write to .tmp, then move. ATOMIC_MOVE is best-effort across filesystems.
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.write(tmp, out.array());
        try {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Decode the JSON payload into a mutable map. */
    static Map<String, String> readEntries(String json) throws IOException {
        Map<String, String> parsed = JsonUtils.getMapper().readValue(json, new TypeReference<Map<String, String>>() {});
        // Always return a mutable LinkedHashMap so subsequent putApiKey() calls work.
        return new LinkedHashMap<>(parsed);
    }

    private Path resolveVaultFile() {
        return globals.dispatcherPath.resolve(VAULT_DIR).resolve(VAULT_FILE);
    }

    /**
     * Visible for tests: clear in-memory state. Does not delete the file on disk.
     */
    void resetForTests() {
        this.entries = null;
        this.vaultPath = null;
        this.saltInUse = null;
        this.iterationsInUse = 0;
    }
}
