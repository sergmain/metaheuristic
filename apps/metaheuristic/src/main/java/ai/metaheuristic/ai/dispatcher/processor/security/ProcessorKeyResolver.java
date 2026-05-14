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

package ai.metaheuristic.ai.dispatcher.processor.security;

import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorCache;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-side resolver for a Processor's RSA public key.
 *
 * <p>Reads {@code publicKeySpki} + {@code keyFingerprint} off the Processor's
 * persisted {@link ProcessorStatusYaml}, decodes the SPKI bytes into a JCA
 * {@link PublicKey} via {@code KeyFactory("RSA")}, and caches the decoded key
 * keyed by the fingerprint. A change in the Processor's fingerprint (Processor
 * reboot → fresh keypair) silently evicts the stale cache entry.
 *
 * <p>Stateless and idempotent for callers. No `@Transactional` — read-only.
 *
 * @author Sergio Lissner
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ProcessorKeyResolver {

    private final ProcessorCache processorCache;

    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Returns the Processor's current RSA public key, or {@link Optional#empty()}
     * if the Processor is unknown or has not yet uploaded a public key.
     *
     * <p>Implementation detail: a fingerprint mismatch between the cache entry
     * and the persisted YAML invalidates the cache transparently. The next read
     * decodes the new SPKI and replaces the entry.
     */
    public Optional<PublicKey> publicKeyFor(long processorId) {
        Processor row = processorCache.findById(processorId);
        if (row == null) {
            return Optional.empty();
        }
        ProcessorStatusYaml psy = row.getProcessorStatusYaml();
        String spkiB64 = psy.publicKeySpki;
        String fingerprint = psy.keyFingerprint;
        if (spkiB64 == null || fingerprint == null) {
            return Optional.empty();
        }

        CacheEntry hit = cache.get(processorId);
        if (hit != null && hit.fingerprint.equals(fingerprint)) {
            return Optional.of(hit.publicKey);
        }

        try {
            byte[] spki = Base64.getDecoder().decode(spkiB64);
            PublicKey pk = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(spki));
            cache.put(processorId, new CacheEntry(fingerprint, pk));
            return Optional.of(pk);
        } catch (Exception e) {
            log.error("808.010 Failed to decode SPKI for processorId={}, fingerprint={}: {}",
                processorId, fingerprint, e.getMessage());
            return Optional.empty();
        }
    }

    /** Test/admin helper: drop all cache entries. */
    public void clearCache() {
        cache.clear();
    }

    private record CacheEntry(String fingerprint, PublicKey publicKey) {}
}
