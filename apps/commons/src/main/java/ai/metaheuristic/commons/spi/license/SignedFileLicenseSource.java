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

package ai.metaheuristic.commons.spi.license;

import org.jspecify.annotations.Nullable;

import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Scenario B backend (offline signed file). Reads the SET of compact JWS licenses installed on this
 * dispatcher (a license directory and DB rows - both abstracted by the token supplier), verifies
 * each one independently via LicenseTokenCodec against the embedded public key, folds the valid
 * ones into one effective entitlement, and answers has(feature) offline. License authority = us; no
 * network at verify time.
 *
 * An installation holds a SET, not one license: a trial cannot be extended because iat is fixed at
 * signing, so continuing one means installing a second license beside the first. Directory and DB
 * are additive rather than a precedence pair - the old 'file wins over row' rule existed only
 * because a single license could be active, and under a set there is nothing to break a tie.
 * Duplicates are dropped so the same token installed twice is one license, not two.
 *
 * Spring-less by construction: token source, clock, resolved deployment values, install id and key
 * resolver are injected, so the dispatcher wiring (Globals.license.*, @Profile("internal-lm"), the
 * DB rows, REST/admin UI) is a thin adapter over this class.
 *
 * Pull-with-refresh: current() re-verifies at most once per cache TTL, so validity naturally flips
 * VALID -> EXPIRED as exp passes (bounded by TTL + the codec's +-60s leeway).
 *
 * @author Serge
 */
public class SignedFileLicenseSource implements LicenseSource {

    private record Cached(Instant at, LicenseAggregate aggregate) {
    }

    private final Supplier<Collection<String>> tokensSupplier;
    private final Function<String, @Nullable ECPublicKey> keyByKid;
    private final Supplier<Instant> clock;
    private final Supplier<DeploymentValues> deployment;
    private final Supplier<@Nullable String> installationId;
    private final Duration cacheTtl;

    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public SignedFileLicenseSource(
            Supplier<Collection<String>> tokensSupplier,
            Function<String, @Nullable ECPublicKey> keyByKid,
            Supplier<Instant> clock,
            Supplier<DeploymentValues> deployment,
            Supplier<@Nullable String> installationId,
            Duration cacheTtl) {
        this.tokensSupplier = tokensSupplier;
        this.keyByKid = keyByKid;
        this.clock = clock;
        this.deployment = deployment;
        this.installationId = installationId;
        this.cacheTtl = cacheTtl;
    }

    @Override
    public Entitlements current() {
        return currentResult().entitlements();
    }

    /** Full aggregate (state + per-license breakdown) for the admin UI; also drives current(). */
    public LicenseAggregate currentResult() {
        final Instant now = clock.get();
        final Cached c = cache.get();
        if (c != null && now.isBefore(c.at().plus(cacheTtl))) {
            return c.aggregate();
        }
        final LicenseAggregate fresh = evaluate(now);
        cache.set(new Cached(now, fresh));
        return fresh;
    }

    /**
     * Drop the cached aggregate so the next {@link #current()} re-reads the token set.
     *
     * <p>Needed because the cache TTL exists to bound how long validity lags the CLOCK, not how
     * long it lags an ADMIN. When a license is installed or removed through the UI the token set
     * changed under us and there is nothing to wait for: without this, a freshly installed license
     * would stay invisible for up to the TTL and the admin would reasonably conclude the upload
     * failed. Time-driven staleness still resolves on its own.
     */
    public void invalidate() {
        cache.set(null);
    }

    private LicenseAggregate evaluate(Instant now) {
        final String localId = installationId.get();
        final List<LicenseVerificationResult> results = new ArrayList<>();
        for (String token : distinctTokens()) {
            results.add(LicenseTokenCodec.verify(token, keyByKid, now, localId));
        }
        return LicenseUnionUtils.fold(results, deployment.get());
    }

    /**
     * The same token can reach us from the directory and from a DB row; installing a license twice
     * is a no-op and not a second license, otherwise the set fills with copies of one grant and the
     * admin page becomes unreadable. Insertion order is kept so the breakdown is stable.
     */
    private Collection<String> distinctTokens() {
        final Collection<String> raw = tokensSupplier.get();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        final LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String t : raw) {
            if (t != null && !t.isBlank()) {
                distinct.add(t);
            }
        }
        return distinct;
    }
}
