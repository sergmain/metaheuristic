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

package ai.metaheuristic.ai.processor.secret;

import ai.metaheuristic.commons.security.SealedSecret;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-company sealed-secret cache. Holds {@link SealedSecret} bytes keyed by
 * {@code (companyId, keyCode)}. Reused across tasks for the same company.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li><strong>Fetch:</strong> {@link DownloadSealedSecretService} POSTs to
 *       Dispatcher, parses the response, and calls
 *       {@link #put(long, String, SealedSecret, String, long)}.</li>
 *   <li><strong>Use:</strong> {@code TaskProcessor} calls
 *       {@link #get(long, String)} at task launch. On hit, decrypts the
 *       {@code SealedSecret} with the Processor's private key, hands the
 *       plaintext to the Function, and zeroes the buffer.</li>
 *   <li><strong>Expiry:</strong> entries past their {@code notAfter} (TTL
 *       backstop, default 1h) are evicted on next access.</li>
 *   <li><strong>Push invalidation:</strong> the keep-alive response handler
 *       calls {@link #invalidate(long, String)} for each entry the Dispatcher
 *       sent. Worst-case staleness under normal operation: one keep-alive
 *       cycle (~20s).</li>
 * </ul>
 *
 * <p>In-memory only — process restart clears the cache. Sealed bytes never
 * touch disk.
 *
 * @author Sergio Lissner
 */
@Component
@Profile("processor")
@Slf4j
public class SealedSecretCache {

    public record CacheKey(long companyId, String keyCode) {}

    private record Entry(SealedSecret sealed, String fingerprint, long expiresAtMs) {}

    private final ConcurrentHashMap<CacheKey, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Returns the sealed bytes for {@code (companyId, keyCode)} if present and
     * not expired. Lazily evicts on TTL miss.
     */
    @Nullable
    public SealedSecret get(long companyId, String keyCode) {
        CacheKey key = new CacheKey(companyId, keyCode);
        Entry e = entries.get(key);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() > e.expiresAtMs) {
            entries.remove(key);
            log.debug("814.010 SealedSecretCache TTL evict for companyId={}, keyCode={}", companyId, keyCode);
            return null;
        }
        return e.sealed;
    }

    /** Store/replace the sealed entry for {@code (companyId, keyCode)}. */
    public void put(long companyId, String keyCode, SealedSecret sealed, String fingerprint, long expiresAtMs) {
        entries.put(new CacheKey(companyId, keyCode), new Entry(sealed, fingerprint, expiresAtMs));
    }

    /** Push-invalidation entry point — called from the keep-alive response handler. */
    public void invalidate(long companyId, String keyCode) {
        CacheKey key = new CacheKey(companyId, keyCode);
        if (entries.remove(key) != null) {
            log.info("814.020 SealedSecretCache push-invalidate companyId={}, keyCode={}", companyId, keyCode);
        }
    }

    /** Test/admin helper: drop everything. */
    public void clearAll() {
        entries.clear();
    }

    /** Test helper: current cache size. */
    public int size() {
        return entries.size();
    }
}
