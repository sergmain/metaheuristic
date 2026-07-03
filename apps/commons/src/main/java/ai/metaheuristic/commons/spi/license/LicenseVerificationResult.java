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

/**
 * Output of LicenseTokenCodec.verify: the resolved state, the parsed claims (when the token could
 * be parsed), and a ready Entitlements snapshot for gating. claims is null when the token could not
 * be parsed/verified at all.
 *
 * @author Serge
 */
public record LicenseVerificationResult(LicenseState state, @Nullable LicenseClaimsV1 claims, Entitlements entitlements) {
}
