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

package ai.metaheuristic.ai.dispatcher.license;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * The identity of an installed license, for de-duplication and lookup.
 *
 * <p>A hash rather than the token itself because {@code MH_LICENSE_ARTIFACT.TOKEN_HASH} carries a
 * UNIQUE index and a compact JWS is far too long to index. It is NOT a security measure — the
 * token is a signed, public artifact and nothing here depends on the hash being hard to reverse.
 *
 * <p>Whitespace is stripped first so the same license installed from a file with a trailing
 * newline and pasted into the UI without one is ONE license, not two.
 *
 * @author Serge
 */
public class LicenseTokenHashUtils {

    private LicenseTokenHashUtils() {
    }

    public static String hash(String token) {
        return DigestUtils.sha256Hex(token.strip());
    }
}
