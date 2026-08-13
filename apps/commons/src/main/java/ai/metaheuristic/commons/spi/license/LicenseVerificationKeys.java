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

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.function.Function;

/**
 * Builds a kid -> public-key resolver from CONFIGURED key material.
 *
 * <p>❗ The key used to be a compiled-in constant so a customer could not swap it. That was dropped
 * deliberately: this product's value is S3 compliance mode, which cannot be forged at all — it needs
 * a real Object Lock bucket in a real AWS account, and access to it is structural (the S3 beans
 * require the {@code aws-lm} profile, so there is no check to delete). Desktop piracy is therefore
 * not the risk being defended against, and a configurable key costs little while buying rotation
 * without a binary release.
 *
 * <p>What it does NOT buy: a customer who supplies their own public key can sign themselves a
 * desktop licence. That is accepted, and it is why nothing valuable rides on the desktop licence.
 *
 * <p>Error code prefix: {@code 01.253.} (unique to this class).
 *
 * @author Serge
 */
public final class LicenseVerificationKeys {

    /** The kid a production desktop licence is signed under. */
    public static final String KID_V1 = "mh-lm-1";

    private LicenseVerificationKeys() {
    }

    /**
     * A resolver that accepts exactly one kid. Returns null for anything else, which the codec
     * reports as SIGNATURE_INVALID.
     *
     * @param base64X509 the configured public half, or null/blank when none is configured — in
     *                   which case nothing verifies and every licence is refused, which is the
     *                   correct answer for a dispatcher that was never given a key
     */
    public static Function<String, @Nullable ECPublicKey> resolver(@Nullable String base64X509) {
        if (base64X509 == null || base64X509.isBlank()) {
            return kid -> null;
        }
        final ECPublicKey key = parse(base64X509);
        return kid -> KID_V1.equals(kid) ? key : null;
    }

    public static ECPublicKey parse(String base64X509) {
        try {
            final byte[] der = Base64.getDecoder().decode(base64X509);
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
        }
        catch (Exception e) {
            throw new IllegalStateException("01.253.010 malformed licence public key", e);
        }
    }
}
