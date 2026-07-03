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

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

/**
 * Hard-coded license verification public key(s), compiled into MH (section 7.1). Deliberately a
 * source constant - NOT a config property, NOT read from a file or env var - so a customer cannot
 * swap it without recompiling MH. It is public key material, so hard-coding leaks no secret. The
 * kid selects among keys for rotation (Appendix D/F). Distinct from the configurable
 * mh.dispatcher.public-key used for function-signature verification.
 *
 * @author Serge
 */
public final class LicenseVerificationKeys {

    public static final String KID_V1 = "lic-key-1";

    // TODO PLACEHOLDER EC P-256 X.509 public key. Replace with the production vendor signing key's
    // public half (its private half is held only by the keygen / license-signer side) before shipping.
    private static final Map<String, String> PUBLIC_KEYS_BY_KID = Map.of(
            KID_V1, "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAESSTrnlCbKmGu3nh/pLDWomvqGK+1ZA+u92H1hax58LJXycUQHrAU6yulg6ja+jBgHuh+O3L7S0IgDwaUIfXkcQ=="
    );

    private LicenseVerificationKeys() {
    }

    public static @Nullable ECPublicKey byKid(String kid) {
        final String b64 = PUBLIC_KEYS_BY_KID.get(kid);
        if (b64 == null) {
            return null;
        }
        try {
            final byte[] der = Base64.getDecoder().decode(b64);
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
        }
        catch (Exception e) {
            throw new IllegalStateException("253.010 malformed embedded license public key for kid: " + kid, e);
        }
    }
}
