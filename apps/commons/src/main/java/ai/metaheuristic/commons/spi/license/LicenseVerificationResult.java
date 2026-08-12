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

/**
 * Output of LicenseTokenCodec.verify for ONE license: the token it is about, the resolved state,
 * the parsed claims (when the token could be parsed), and a ready Entitlements snapshot. claims is
 * null when the token could not be parsed/verified at all.
 *
 * An installation holds a set of licenses, so this is a per-license result and not the answer the
 * runtime gates on - that is the folded LicenseAggregate.
 *
 * The token is carried so a caller can tell WHICH license a result belongs to. Correlating by
 * position in the aggregate's list would look sufficient and is not: duplicates are dropped before
 * verification, so positions do not line up with whatever the caller read the tokens from. A token
 * is not a secret - it is a signed, inspectable artifact - so carrying it costs nothing.
 *
 * @author Serge
 */
public record LicenseVerificationResult(
        String token, LicenseState state, @Nullable LicenseClaims claims, Entitlements entitlements) {
}
