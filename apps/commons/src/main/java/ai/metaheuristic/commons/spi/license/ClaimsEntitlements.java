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

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable Entitlements snapshot backed by a verified claim set. Backends re-run verification on
 * each current() (bounded by a short cache TTL), so a fresh snapshot naturally reflects exp passing.
 *
 * @author Serge
 */
public final class ClaimsEntitlements implements Entitlements {

    private final LicenseState state;
    @Nullable private final Instant expiresAt;
    private final Set<String> grantedKeys;

    public ClaimsEntitlements(LicenseState state, @Nullable Instant expiresAt, Set<String> grantedKeys) {
        this.state = state;
        this.expiresAt = expiresAt;
        this.grantedKeys = new HashSet<>(grantedKeys);
    }

    public static ClaimsEntitlements invalid(LicenseState state) {
        return new ClaimsEntitlements(state, null, Set.of());
    }

    @Override
    public boolean valid() {
        return state == LicenseState.VALID;
    }

    @Override
    public boolean has(Feature f) {
        return valid() && grantedKeys.contains(f.key());
    }

    @Override
    public Optional<Instant> expiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    public LicenseState state() {
        return state;
    }
}
