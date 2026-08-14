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

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.function.Function;

/**
 * The licence the MH suite runs under: a static, checked-in artifact.
 *
 * <p>Issued ONCE by {@code apps/license-signer} with {@code sign mh-test-license-config.yaml --mint-key} — the real
 * signer, so the path from recipe to token is the same one a customer licence travels, not a
 * shortcut written for tests. The private half of that keypair was destroyed afterwards, which is
 * why only a public key sits in {@code application-test.properties} and why the licence is
 * long-dated: it can never be re-signed.
 *
 * <p>Nothing here mints anything. There is no signing key in this repository to mint with, and a
 * test fixture that could sign its own licence would be able to grant itself capabilities the
 * product does not sell.
 *
 * <p>❗ It grants {@code MH.BATCH} and nothing else. MH must not name proprietary capabilities even in a test artifact — the string would be a seal breach sitting in {@code java/metaheuristic}.
 *
 * @author Serge
 */
public class LicenseTestFixture {

    public static final String LICENSE_RESOURCE = "/license/mh-test-license.jws";

    private LicenseTestFixture() {
    }

    public static ECPublicKey publicKey(String base64X509) {
        try {
            final byte[] der = Base64.getDecoder().decode(base64X509);
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
        }
        catch (Exception e) {
            throw new IllegalStateException("malformed test licence public key", e);
        }
    }

    /**
     * A resolver that knows ONLY the test key.
     *
     * <p>Deliberately not chained to {@code LicenseVerificationKeys}: a test run must not be able
     * to accept a production-signed licence by accident, and the production verifier must never
     * learn this key.
     */
    public static Function<String, @Nullable ECPublicKey> keyResolver(ECPublicKey pub) {
        return kid -> "test-key-1".equals(kid) ? pub : null;
    }

    /** The checked-in compact JWS. */
    public static String readLicense() {
        try (InputStream is = LicenseTestFixture.class.getResourceAsStream(LICENSE_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("test licence not found on the classpath: " + LICENSE_RESOURCE);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
        catch (IOException e) {
            throw new IllegalStateException("can't read the test licence: " + LICENSE_RESOURCE, e);
        }
    }
}
