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

package ai.metaheuristic.commons.spi.license;

import org.jspecify.annotations.Nullable;

import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Scenario B backend (offline signed file). Reads a compact JWS license (file first, DB-row
 * fallback - abstracted by the token supplier), verifies it via LicenseTokenCodec against the
 * embedded public key, and answers has(feature) offline. License authority = us; no network at
 * verify time.
 *
 * Spring-less by construction: token source, clock, active profiles, install id and key resolver
 * are injected, so the dispatcher wiring (Globals.license.*, @Profile("internal-lm"), DB-row
 * fallback, REST/admin UI) is a thin adapter over this class.
 *
 * Pull-with-refresh: current() re-verifies at most once per cache TTL, so validity naturally flips
 * VALID -> EXPIRED as exp passes (bounded by TTL + the codec's +-60s leeway).
 *
 * @author Serge
 */
public class SignedFileLicenseSource implements LicenseSource {

    private record Cached(Instant at, LicenseVerificationResult result) {
    }

    private final Supplier<Optional<String>> tokenSupplier;
    private final Function<String, @Nullable ECPublicKey> keyByKid;
    private final Supplier<Instant> clock;
    private final Supplier<Set<String>> activeProfiles;
    private final Supplier<@Nullable String> installationId;
    private final Duration cacheTtl;

    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public SignedFileLicenseSource(
            Supplier<Optional<String>> tokenSupplier,
            Function<String, @Nullable ECPublicKey> keyByKid,
            Supplier<Instant> clock,
            Supplier<Set<String>> activeProfiles,
            Supplier<@Nullable String> installationId,
            Duration cacheTtl) {
        this.tokenSupplier = tokenSupplier;
        this.keyByKid = keyByKid;
        this.clock = clock;
        this.activeProfiles = activeProfiles;
        this.installationId = installationId;
        this.cacheTtl = cacheTtl;
    }

    /** Default wiring against the embedded verification key (LicenseVerificationKeys). */
    public static SignedFileLicenseSource withEmbeddedKey(
            Supplier<Optional<String>> tokenSupplier,
            Supplier<Instant> clock,
            Supplier<Set<String>> activeProfiles,
            Supplier<@Nullable String> installationId,
            Duration cacheTtl) {
        return new SignedFileLicenseSource(
                tokenSupplier, LicenseVerificationKeys::byKid, clock, activeProfiles, installationId, cacheTtl);
    }

    @Override
    public Entitlements current() {
        return currentResult().entitlements();
    }

    /** Full result (state + claims) for the admin UI; also drives current(). */
    public LicenseVerificationResult currentResult() {
        final Instant now = clock.get();
        final Cached c = cache.get();
        if (c != null && now.isBefore(c.at().plus(cacheTtl))) {
            return c.result();
        }
        final LicenseVerificationResult fresh = evaluate(now);
        cache.set(new Cached(now, fresh));
        return fresh;
    }

    private LicenseVerificationResult evaluate(Instant now) {
        final Optional<String> token = tokenSupplier.get();
        if (token.isEmpty() || token.get().isBlank()) {
            return new LicenseVerificationResult(
                    LicenseState.NO_LICENSE, null, ClaimsEntitlements.invalid(LicenseState.NO_LICENSE));
        }
        return LicenseTokenCodec.verify(token.get(), keyByKid, now, activeProfiles.get(), installationId.get());
    }
}
