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

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.jspecify.annotations.Nullable;

import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Mints the licence the test suite runs under.
 *
 * <p><b>Minted per run, never checked in.</b> {@code exp} is absolute from {@code iat} at signing,
 * so a committed short-lived token would expire a couple of hours after somebody signed it and the
 * suite would go red forever after. Minting makes the window mean what it looks like it means: two
 * hours from THIS run, comfortably more than the suite needs, and short enough that a token which
 * somehow escaped the test classpath is worthless by the time anyone found it.
 *
 * <p>❗ <b>This licence grants {@code Capability:BATCH} and nothing else.</b> MH must not name
 * proprietary capabilities even in a test fixture — the string would be a seal breach sitting in
 * {@code java/metaheuristic}. The proprietary side mints its own licence, with its own key, in its
 * own repository.
 *
 * <p>H2 only, and no storage backend: exactly the deployment the tests run on, so the licence
 * proves the deployment axes are being checked rather than waved through.
 *
 * @author Serge
 */
public class LicenseTestFixture {

    /** Long enough for the whole suite, short enough to be worthless if it leaks. */
    public static final Duration TTL = Duration.ofHours(2);

    public static final String KID = "test-key-1";

    private LicenseTestFixture() {
    }

    public static ECPrivateKey privateKey(String base64Pkcs8) {
        try {
            final byte[] der = Base64.getDecoder().decode(base64Pkcs8);
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        }
        catch (Exception e) {
            throw new IllegalStateException("malformed test licence private key", e);
        }
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
     * to accept a production-signed licence by accident, and — far more important — the production
     * verifier must never learn this key, whose private half is in the repository.
     */
    public static Function<String, @Nullable ECPublicKey> keyResolver(ECPublicKey pub) {
        return kid -> KID.equals(kid) ? pub : null;
    }

    /** The suite's licence: BATCH only, H2 only, no storage, valid for {@link #TTL} from now. */
    public static String mintSuiteLicense(ECPrivateKey priv, Instant now) {
        return mint(priv, now, List.of("Capability:BATCH"), List.of("H2"), List.of(), now.plus(TTL));
    }

    /** Full control, so a test can mint an expired / not-yet-valid / wrong-deployment licence too. */
    public static String mint(
            ECPrivateKey priv, Instant iat,
            List<String> capabilities, List<String> databases, List<String> storages, Instant exp) {
        try {
            final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .claim("licensee", "MH test suite")
                    .claim("edition", "TRIAL")
                    .claim("capabilities", capabilities)
                    .claim("databases", databases)
                    .claim("storages", storages)
                    .claim("version", 1)
                    .issueTime(Date.from(iat))
                    .expirationTime(Date.from(exp))
                    .build();
            final JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(KID).type(new JOSEObjectType("license+jws")).build();
            final SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new ECDSASigner(priv));
            return jwt.serialize();
        }
        catch (Exception e) {
            throw new IllegalStateException("can't mint the test licence", e);
        }
    }
}
