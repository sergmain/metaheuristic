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
import ai.metaheuristic.commons.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-company Key Vault.
 *
 * <p>API keys are stored inside {@code Company.params} as a
 * {@link CompanyParamsYaml.VaultEntries} blob: per-company PBKDF2 salt +
 * iteration count, plus an AES/GCM-encrypted JSON map {@code {code: secret}}.
 *
 * <p>Unlock state lives in dispatcher memory keyed by {@code companyUniqueId}:
 * decrypted entries, the derived AES session key, the salt, and the iteration
 * count. The master passphrase is never persisted. JVM restart locks every
 * company's vault.
 *
 * <p>Write paths delegate to {@link VaultTxService} so the DB write is
 * transactional and the {@code VaultEntryChangedTxEvent} fires only after
 * commit.
 *
 * <p>GCM AAD layout (binds KDF parameters to the ciphertext, so tampering
 * with salt or iterations fails authentication on decrypt):
 * <pre>
 *   [4 bytes int iterations][16 bytes salt]
 * </pre>
 *
 * @author Sergio Lissner
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class VaultService {

    /** PBKDF2 iteration count used for new vaults. Existing vaults reuse the value stored on the entity. */
    static final int PBKDF2_ITERATIONS = 200_000;
    static final int SALT_LEN = 16;
    /** AES-256 key. */
    static final int KEY_LEN_BITS = 256;
    /** Length of the AAD that binds the KDF params to the ciphertext. */
    static final int AAD_LEN = 4 + SALT_LEN;

    private static final SecureRandom RNG = new SecureRandom();

    private final VaultTxService vaultTxService;

    /** Per-company in-memory unlocked state. Cleared on lock or JVM restart. */
    private final ConcurrentHashMap<Long, UnlockedState> unlocked = new ConcurrentHashMap<>();

    /** Unlocked vault state for a single company. */
    static final class UnlockedState {
        /** Mutable map of {@code code -> secret}. Guarded by {@link #lock}. */
        final Map<String, String> entries;
        final byte[] salt;
        final int iterations;
        /** Derived AES-256 key. */
        final byte[] key;
        /** Per-company lock so put/delete don't race a concurrent rewrite. */
        final Object lock = new Object();

        UnlockedState(Map<String, String> entries, byte[] salt, int iterations, byte[] key) {
            this.entries = entries;
            this.salt = salt;
            this.iterations = iterations;
            this.key = key;
        }
    }

    /** @return whether the given company's vault is currently unlocked in dispatcher memory. */
    public boolean isOpened(long companyUniqueId) {
        return unlocked.containsKey(companyUniqueId);
    }

    /**
     * Resolve the API key for a given company + code.
     * Returns empty if the vault is locked or the entry does not exist.
     */
    public Optional<String> getApiKey(long companyUniqueId, String code) {
        UnlockedState s = unlocked.get(companyUniqueId);
        if (s == null) {
            return Optional.empty();
        }
        synchronized (s.lock) {
            return Optional.ofNullable(s.entries.get(code));
        }
    }

    /**
     * Returns the decrypted secret as a fresh byte[] (UTF-8 of the underlying String).
     * The caller owns the returned buffer and is expected to zero it via
     * Arrays.fill(buf, (byte) 0) after use.
     *
     * <p>Successive calls return independent byte[] instances; zeroing one does
     * not affect Vault's internal state nor any other previously returned buffer.
     */
    public Optional<byte[]> getKeyBytes(long companyUniqueId, String code) {
        Optional<String> opt = getApiKey(companyUniqueId, code);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(opt.get().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Unlock the vault for a single company. If the company has no vault yet,
     * an empty one is created using {@code passphrase} as the master password
     * — but the empty blob is NOT yet persisted (persistence happens on the
     * first put). This lets a user unlock and inspect an empty vault without
     * dirtying Company.params until they actually add a key.
     *
     * @param companyUniqueId company.uniqueId from UserContext
     * @param passphrase      master password (must not be blank)
     */
    public VaultData.UnlockResult unlock(long companyUniqueId, String passphrase) {
        if (passphrase == null || passphrase.isBlank()) {
            return new VaultData.UnlockResult("Passphrase must not be blank");
        }
        try {
            CompanyParamsYaml.VaultEntries blob = vaultTxService.loadVaultBlob(companyUniqueId);
            boolean created = false;
            Map<String, String> map;
            byte[] salt;
            int iterations;
            byte[] sessionKey;

            if (blob == null) {
                // First-time use for this company. Allocate fresh KDF params,
                // empty in-memory entries. Persistence is deferred until put().
                salt = new byte[SALT_LEN];
                RNG.nextBytes(salt);
                iterations = PBKDF2_ITERATIONS;
                map = new LinkedHashMap<>();
                sessionKey = deriveKey(passphrase, salt, iterations);
                created = true;
            } else {
                salt = Base64.getDecoder().decode(blob.salt);
                iterations = blob.iterations;
                sessionKey = deriveKey(passphrase, salt, iterations);
                byte[] payload = Base64.getDecoder().decode(blob.encryptedEntries);
                byte[] aad = buildAad(salt, iterations);
                String json = JsonCrypto.decrypt(sessionKey, payload, aad);
                map = readEntries(json);
            }

            // Atomic-ish swap of state for this company. Any previous derived
            // key for this company gets zeroed.
            UnlockedState previous = unlocked.put(companyUniqueId,
                new UnlockedState(map, salt, iterations, sessionKey));
            if (previous != null) {
                Arrays.fill(previous.key, (byte) 0);
            }
            log.info("0670.020 Vault unlocked for companyUniqueId={} (created={})", companyUniqueId, created);
            return new VaultData.UnlockResult(true, created);
        } catch (Exception e) {
            log.error("0670.030 Failed to unlock vault for companyUniqueId={}: {}", companyUniqueId, e.getMessage());
            // Do not leak details about why unlock failed (wrong passphrase vs IO error).
            return new VaultData.UnlockResult("Unable to open vault");
        }
    }

    /**
     * Add or replace an entry under the given company+code with the supplied
     * secret and persist the vault.
     *
     * <p>Uses the cached session key established at {@link #unlock(long, String)}
     * time; caller is responsible for verifying the user's proof-of-knowledge
     * of the master passphrase via {@link #verifyPassphrase(long, String)}
     * before calling this.
     *
     * @return true if persisted, false if the vault is locked or persistence failed
     */
    public boolean putApiKey(long companyUniqueId, String code, String secret) {
        UnlockedState s = unlocked.get(companyUniqueId);
        if (s == null) {
            return false;
        }
        // Snapshot the encrypted blob under the per-company lock so we don't
        // race with a concurrent put/delete on the same company.
        CompanyParamsYaml.VaultEntries blob;
        synchronized (s.lock) {
            s.entries.put(code, secret);
            try {
                blob = serialize(s);
            } catch (Exception e) {
                // Roll back the in-memory mutation so memory and DB stay consistent.
                s.entries.remove(code);
                log.error("0670.040 Failed to encrypt entry companyUniqueId={}, code={}: {}",
                    companyUniqueId, code, e.getMessage());
                return false;
            }
        }
        boolean ok = vaultTxService.saveVaultBlob(companyUniqueId, blob, code, VaultEntryChangedEvent.ACTION_PUT);
        if (!ok) {
            // Persistence failed (company not found). Revert the in-memory put.
            synchronized (s.lock) {
                s.entries.remove(code);
            }
        }
        return ok;
    }

    /**
     * Remove an entry and persist the vault.
     *
     * <p>Caller must have verified the master passphrase before calling.
     *
     * @return true if the entry existed and the vault was persisted; false if
     *         the vault is locked, the entry did not exist, or persistence failed.
     */
    public boolean deleteApiKey(long companyUniqueId, String code) {
        UnlockedState s = unlocked.get(companyUniqueId);
        if (s == null) {
            return false;
        }
        CompanyParamsYaml.VaultEntries blob;
        String previousValue;
        synchronized (s.lock) {
            if (!s.entries.containsKey(code)) {
                return false;
            }
            previousValue = s.entries.remove(code);
            try {
                blob = serialize(s);
            } catch (Exception e) {
                // Roll back the in-memory mutation.
                s.entries.put(code, previousValue);
                log.error("0670.050 Failed to encrypt vault on delete companyUniqueId={}, code={}: {}",
                    companyUniqueId, code, e.getMessage());
                return false;
            }
        }
        boolean ok = vaultTxService.saveVaultBlob(companyUniqueId, blob, code, VaultEntryChangedEvent.ACTION_DELETE);
        if (!ok) {
            // Persistence failed. Revert.
            synchronized (s.lock) {
                s.entries.put(code, previousValue);
            }
        }
        return ok;
    }

    /**
     * List the contents of the vault scoped to a single company.
     * Returns empty list when locked or when the company has no entries.
     * Per design, once the vault is unlocked the UI is allowed to show secrets
     * in plain text.
     */
    public List<VaultData.Entry> listEntries(long companyUniqueId) {
        UnlockedState s = unlocked.get(companyUniqueId);
        if (s == null) {
            return List.of();
        }
        synchronized (s.lock) {
            List<VaultData.Entry> out = new ArrayList<>(s.entries.size());
            for (Map.Entry<String, String> e : s.entries.entrySet()) {
                out.add(new VaultData.Entry(companyUniqueId, e.getKey(), e.getValue()));
            }
            return out;
        }
    }

    /**
     * Proof-of-knowledge check: does the supplied passphrase match the one
     * used to unlock this company's vault?
     *
     * <p>Re-derives a key from the supplied passphrase using the in-use salt
     * and iteration count for this company, and compares against the cached
     * key in constant time. Returns false if the vault is locked or the
     * passphrase does not match.
     */
    public boolean verifyPassphrase(long companyUniqueId, String passphrase) {
        if (passphrase == null || passphrase.isEmpty()) {
            return false;
        }
        UnlockedState s = unlocked.get(companyUniqueId);
        if (s == null) {
            return false;
        }
        byte[] candidate = null;
        try {
            candidate = deriveKey(passphrase, s.salt, s.iterations);
            return MessageDigest.isEqual(s.key, candidate);
        } catch (GeneralSecurityException e) {
            log.error("0670.060 Failed to derive key during passphrase verification: {}", e.getMessage());
            return false;
        } finally {
            if (candidate != null) {
                Arrays.fill(candidate, (byte) 0);
            }
        }
    }

    /** Re-encrypt the company's entries under its session key + KDF params. Caller must hold the lock. */
    private static CompanyParamsYaml.VaultEntries serialize(UnlockedState s) throws Exception {
        String json = JsonUtils.getMapper().writeValueAsString(s.entries);
        byte[] aad = buildAad(s.salt, s.iterations);
        byte[] encrypted = JsonCrypto.encrypt(s.key, json, aad);
        return new CompanyParamsYaml.VaultEntries(
            Base64.getEncoder().encodeToString(s.salt),
            s.iterations,
            Base64.getEncoder().encodeToString(encrypted));
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

    /** Build the AAD that binds KDF params to the ciphertext. */
    static byte[] buildAad(byte[] salt, int iterations) {
        ByteBuffer h = ByteBuffer.allocate(AAD_LEN);
        h.putInt(iterations);
        h.put(salt);
        return h.array();
    }

    /** Decode the JSON payload into a mutable map. */
    static Map<String, String> readEntries(String json) throws Exception {
        Map<String, String> parsed = JsonUtils.getMapper().readValue(json, new TypeReference<Map<String, String>>() {});
        // Always return a mutable LinkedHashMap so subsequent putApiKey() calls work.
        return new LinkedHashMap<>(parsed);
    }

    /** Visible for tests: clear in-memory state. Does not change persisted data. */
    void resetForTests() {
        for (UnlockedState s : unlocked.values()) {
            Arrays.fill(s.key, (byte) 0);
        }
        unlocked.clear();
    }

    /** Visible for tests: how many companies have an unlocked vault right now. */
    @Nullable
    UnlockedState peekUnlockedState(long companyUniqueId) {
        return unlocked.get(companyUniqueId);
    }
}
