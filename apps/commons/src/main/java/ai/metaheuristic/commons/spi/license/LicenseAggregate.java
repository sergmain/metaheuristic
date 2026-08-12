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

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The effective entitlement of an installation: every installed license verified independently and
 * the valid ones folded into one answer by union.
 *
 * Because a license only ever grants - there is no denial, no exclusion and no revocation list -
 * the union is order-independent, idempotent and conflict-free by construction. There is no
 * precedence rule to write here because two grants can never disagree.
 *
 * <p>The three grant sets are EFFECTIVE, not merely unioned: when the aggregate is invalid they are
 * empty, because an unlicensed deployment licenses nothing. Callers therefore cannot read a grant
 * out of an aggregate that is not entitled to it.
 *
 * <p>{@link #licenses} keeps the per-license results alongside the aggregate. Without it an admin
 * cannot tell which license is about to lapse or which one is contributing a capability, and a set
 * of licenses stops being operable.
 *
 * @author Serge
 */
public record LicenseAggregate(
        LicenseState state,
        Entitlements entitlements,
        Set<String> capabilities,
        Set<String> databases,
        Set<String> storages,
        @Nullable Instant expiresAt,
        List<LicenseVerificationResult> licenses) {
}
