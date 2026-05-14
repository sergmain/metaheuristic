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

package ai.metaheuristic.ai.dispatcher.secret;

import ai.metaheuristic.ai.dispatcher.processor.security.ProcessorKeyResolver;
import ai.metaheuristic.ai.dispatcher.vault.VaultService;
import ai.metaheuristic.commons.security.AsymmetricEncryptor;
import ai.metaheuristic.commons.security.SealedSecret;
import ai.metaheuristic.commons.security.SealedSecretCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Non-transactional orchestrator for the per-task sealed-secret endpoint.
 *
 * <p>For an authenticated Processor request asking for {@code (companyId, keyCode)}:
 * <ol>
 *   <li>Resolve the Processor's RSA public key from {@link ProcessorKeyResolver}.
 *       Absent → {@link Outcome#processorNotEnrolled()}.</li>
 *   <li>Read the plaintext from Vault via {@link VaultService#getKeyBytes(long, String)}.
 *       Absent → {@link Outcome#vaultEntryMissing()}.</li>
 *   <li>Hybrid-seal under the Processor's public key via
 *       {@link AsymmetricEncryptor#encrypt(byte[], PublicKey)}.</li>
 *   <li>Base64-encode the wire bytes, fingerprint the plaintext (SHA-256 hex),
 *       zero the plaintext buffer, and return.</li>
 * </ol>
 *
 * <p>Error-code prefix: {@code 0664.} is unique to this class per project
 * convention; suffixes distinguish scenarios.
 *
 * @author Sergio Lissner
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class SealedSecretService {

    /** Default TTL backstop. Signal-bus invalidation is the primary mechanism. */
    private static final long DEFAULT_TTL_MILLIS = TimeUnit.HOURS.toMillis(1);

    private final VaultService vaultService;
    private final ProcessorKeyResolver processorKeyResolver;

    /** Outcome of a sealed-secret request. Either a {@link #payload} or a reason. */
    public record Outcome(
        @Nullable SealedPayload payload,
        Reason reason
    ) {
        public enum Reason {
            OK,
            PROCESSOR_NOT_ENROLLED,    // 404 — Stage 4 prerequisite not satisfied
            VAULT_ENTRY_MISSING,       // 410 — Vault has no entry for (companyId, keyCode)
            INTERNAL_ERROR             // 500 — seal/decode failed unexpectedly
        }
        public static Outcome ok(SealedPayload p) { return new Outcome(p, Reason.OK); }
        public static Outcome processorNotEnrolled() { return new Outcome(null, Reason.PROCESSOR_NOT_ENROLLED); }
        public static Outcome vaultEntryMissing() { return new Outcome(null, Reason.VAULT_ENTRY_MISSING); }
        public static Outcome internalError() { return new Outcome(null, Reason.INTERNAL_ERROR); }
    }

    /** Sealed-payload bundle returned in the 200 response. */
    public record SealedPayload(
        String sealed,
        String fingerprint,
        long issuedOn,
        long notAfter
    ) {}

    /**
     * Resolve, seal, and return one Vault entry for the requesting Processor.
     *
     * @param processorId authenticated Processor id (resolved upstream by the controller).
     * @param companyId   ownership anchor for the Vault lookup.
     * @param keyCode     Vault entry code.
     * @return an {@link Outcome} carrying either the sealed payload or a reason.
     */
    public Outcome sealFor(long processorId, long companyId, String keyCode) {
        Optional<PublicKey> pubKeyOpt = processorKeyResolver.publicKeyFor(processorId);
        if (pubKeyOpt.isEmpty()) {
            log.info("0664.010 Processor {} has no publicKeySpki on file yet — answering processorNotEnrolled", processorId);
            return Outcome.processorNotEnrolled();
        }
        PublicKey pubKey = pubKeyOpt.get();

        Optional<byte[]> plainOpt = vaultService.getKeyBytes(companyId, keyCode);
        if (plainOpt.isEmpty()) {
            log.info("0664.020 Vault has no entry for companyId={}, keyCode={} — answering vaultEntryMissing", companyId, keyCode);
            return Outcome.vaultEntryMissing();
        }
        byte[] plaintext = plainOpt.get();
        try {
            SealedSecret sealed = AsymmetricEncryptor.encrypt(plaintext, pubKey);
            byte[] wire = SealedSecretCodec.toBytes(sealed);
            String sealedB64 = Base64.getEncoder().encodeToString(wire);
            String fingerprint = sha256Hex(plaintext);

            long now = System.currentTimeMillis();
            long notAfter = now + DEFAULT_TTL_MILLIS;
            return Outcome.ok(new SealedPayload(sealedB64, fingerprint, now, notAfter));
        } catch (Exception e) {
            log.error("0664.030 Failed to seal entry for companyId={}, keyCode={}: {}", companyId, keyCode, e.getMessage(), e);
            return Outcome.internalError();
        } finally {
            // Zero the plaintext buffer the moment we're done. The caller (VaultService.getKeyBytes)
            // returned a fresh copy that we own per its contract.
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private static String sha256Hex(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }

    // Charset reference kept for future signed-AAD use (not load-bearing right now).
    @SuppressWarnings("unused")
    private static final java.nio.charset.Charset UTF8 = StandardCharsets.UTF_8;
}
