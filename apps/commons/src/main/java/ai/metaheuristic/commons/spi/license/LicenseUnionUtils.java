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

import ai.metaheuristic.api.data.license.LicenseClaims;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Folds a set of per-license verification results into one effective entitlement.
 *
 * Pure: no Spring, no clock, no filesystem, no verification of its own. Everything it needs has
 * already been decided by LicenseTokenCodec, so this is a function over verified claim sets and is
 * meant to stay that way - it is the piece most likely to be reasoned about later.
 *
 * <p>Rules, none of which may be re-derived at a call site:
 * <ul>
 *   <li>An invalid license contributes nothing and is never fatal. Expired, not-yet-valid, badly
 *       signed and bound-to-another-installation licenses are skipped, and the rest still count.</li>
 *   <li>The grant sets are unioned. Adding a license can only add grants; adding the same license
 *       twice changes nothing.</li>
 *   <li>The deployment axes are checked ONCE, against the union. A license that does not list the
 *       running database is not invalid - it just does not contribute that database.</li>
 *   <li>An empty allow-list grants nothing on that axis. It is not 'unconstrained'.</li>
 *   <li>When the running deployment is not licensed, no capability is licensed either.</li>
 * </ul>
 *
 * @author Serge
 */
public class LicenseUnionUtils {

    private LicenseUnionUtils() {
    }

    /**
     * Which missing-coverage reason to report when nothing is valid. Ordered by how the installation
     * would recover coverage: renew, wait, move the license to the right machine, re-issue, and only
     * then 'this file is not a license of ours'.
     */
    private static final List<LicenseState> COVERAGE_GAP_PRECEDENCE = List.of(
            LicenseState.EXPIRED,
            LicenseState.NOT_YET_VALID,
            LicenseState.INSTALL_ID_MISMATCH,
            LicenseState.MALFORMED,
            LicenseState.SIGNATURE_INVALID);

    public static LicenseAggregate fold(List<LicenseVerificationResult> results, DeploymentValues deployment) {
        if (results.isEmpty()) {
            return empty(LicenseState.NO_LICENSE, List.of());
        }

        final List<LicenseClaims> valid = results.stream()
                .filter(r -> r.state()==LicenseState.VALID)
                .map(LicenseVerificationResult::claims)
                .filter(Objects::nonNull)
                .toList();

        if (valid.isEmpty()) {
            return empty(coverageGapState(results), results);
        }

        final Set<String> capabilities = union(valid, c -> c.capabilities);
        final Set<String> databases = union(valid, c -> c.databases);
        final Set<String> storages = union(valid, c -> c.storages);

        // the latest exp among currently-valid licenses: the instant the installation loses all coverage.
        @Nullable final Instant expiresAt = valid.stream()
                .map(c -> c.exp).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);

        final LicenseState state = deploymentState(deployment, databases, storages);
        if (state!=LicenseState.VALID) {
            return new LicenseAggregate(
                    state, new ClaimsEntitlements(state, expiresAt, Set.of()),
                    Set.of(), Set.of(), Set.of(), expiresAt, List.copyOf(results));
        }
        return new LicenseAggregate(
                state, new ClaimsEntitlements(state, expiresAt, capabilities),
                capabilities, databases, storages, expiresAt, List.copyOf(results));
    }

    private static LicenseState deploymentState(DeploymentValues deployment, Set<String> databases, Set<String> storages) {
        if (!databases.contains(deployment.database())) {
            return LicenseState.DATABASE_NOT_LICENSED;
        }
        final String storage = deployment.storage();
        // no external storage backend active -> nothing to license on that axis.
        if (storage!=null && !storages.contains(storage)) {
            return LicenseState.STORAGE_NOT_LICENSED;
        }
        return LicenseState.VALID;
    }

    private static LicenseState coverageGapState(List<LicenseVerificationResult> results) {
        for (LicenseState candidate : COVERAGE_GAP_PRECEDENCE) {
            for (LicenseVerificationResult r : results) {
                if (r.state()==candidate) {
                    return candidate;
                }
            }
        }
        return LicenseState.NO_LICENSE;
    }

    private static Set<String> union(List<LicenseClaims> valid, Function<LicenseClaims, List<String>> axis) {
        final Set<String> acc = new LinkedHashSet<>();
        valid.forEach(c -> acc.addAll(axis.apply(c)));
        return Set.copyOf(acc);
    }

    private static LicenseAggregate empty(LicenseState state, List<LicenseVerificationResult> results) {
        return new LicenseAggregate(
                state, ClaimsEntitlements.invalid(state),
                Set.of(), Set.of(), Set.of(), null, List.copyOf(results));
    }
}
