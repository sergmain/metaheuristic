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

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip: sign with Nimbus (as the license-signer does), verify with LicenseTokenCodec.
 *
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class LicenseTokenCodecTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final String KID = "lic-key-1";

    private static KeyPair ecKeyPair() throws Exception {
        final KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    private static String sign(ECPrivateKey priv, JWTClaimsSet claims) throws Exception {
        final JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(KID).type(new JOSEObjectType("license+jws")).build();
        final SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(priv));
        return jwt.serialize();
    }

    private static JWTClaimsSet.Builder enterprise() {
        return new JWTClaimsSet.Builder()
                .claim("licensee", "ACME").claim("edition", "ENTERPRISE")
                .claim("features", List.of("FEATURE_A", "FEATURE_B", "FEATURE_C")).claim("ver", 1)
                .issueTime(Date.from(NOW));
    }

    private static Function<String, ECPublicKey> resolver(ECPublicKey pub) {
        return kid -> KID.equals(kid) ? pub : null;
    }

    @Test
    public void test_validRoundTrip() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of(), null);

        assertEquals(LicenseState.VALID, r.state());
        assertTrue(r.entitlements().valid());
        assertTrue(r.entitlements().has(new Feature("FEATURE_C")));
        assertFalse(r.entitlements().has(new Feature("NOT_GRANTED")));
        assertTrue(r.entitlements().expiresAt().isPresent());
    }

    @Test
    public void test_expired() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.minus(Duration.ofDays(1)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of(), null);

        assertEquals(LicenseState.EXPIRED, r.state());
        assertFalse(r.entitlements().valid());
        assertFalse(r.entitlements().has(new Feature("FEATURE_C")));
    }

    @Test
    public void test_notYetValid() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().notBeforeTime(Date.from(NOW.plus(Duration.ofDays(2))))
                        .expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of(), null);

        assertEquals(LicenseState.NOT_YET_VALID, r.state());
    }

    @Test
    public void test_leeway_expJustPast_stillValid() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.minus(Duration.ofSeconds(30)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of(), null);

        assertEquals(LicenseState.VALID, r.state(), "30s past exp is within the +-60s leeway");
    }

    @Test
    public void test_tamperedSignature() throws Exception {
        final KeyPair kp = ecKeyPair();
        String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());
        final char last = tok.charAt(tok.length() - 1);
        tok = tok.substring(0, tok.length() - 1) + (last == 'A' ? 'B' : 'A');

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of(), null);

        assertEquals(LicenseState.SIGNATURE_INVALID, r.state());
    }

    @Test
    public void test_unknownKid() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, kid -> null, NOW, Set.of(), null);

        assertEquals(LicenseState.SIGNATURE_INVALID, r.state());
    }

    @Test
    public void test_wrongKey() throws Exception {
        final KeyPair signer = ecKeyPair();
        final KeyPair other = ecKeyPair();
        final String tok = sign((ECPrivateKey) signer.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) other.getPublic()), NOW, Set.of(), null);

        assertEquals(LicenseState.SIGNATURE_INVALID, r.state());
    }

    @Test
    public void test_profileRequiredMissing() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().claim("required_profiles", List.of("h2"))
                        .expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of("mysql"), null);

        assertEquals(LicenseState.PROFILE_CONSTRAINT_VIOLATED, r.state());
    }

    @Test
    public void test_profileRequiredPresent() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().claim("required_profiles", List.of("h2"))
                        .expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of("dispatcher", "h2"), null);

        assertEquals(LicenseState.VALID, r.state());
    }

    @Test
    public void test_forbiddenProfileActive() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().claim("forbidden_profiles", List.of("mysql"))
                        .expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of("mysql"), null);

        assertEquals(LicenseState.PROFILE_CONSTRAINT_VIOLATED, r.state());
    }

    @Test
    public void test_timelessWithProfiles_valid() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().claim("edition", "TRIAL").claim("required_profiles", List.of("h2")).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of("h2"), null);

        assertEquals(LicenseState.VALID, r.state());
        assertTrue(r.entitlements().expiresAt().isEmpty());
    }

    @Test
    public void test_timelessWithoutProfiles_malformed() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(), enterprise().build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of(), null);

        assertEquals(LicenseState.MALFORMED, r.state());
        assertFalse(r.entitlements().valid());
    }

    @Test
    public void test_installIdMismatch() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().claim("installation_id", "uuid-A")
                        .expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of(), "uuid-B");

        assertEquals(LicenseState.INSTALL_ID_MISMATCH, r.state());
    }

    @Test
    public void test_installIdMatch_valid() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().claim("installation_id", "uuid-A")
                        .expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, Set.of(), "uuid-A");

        assertEquals(LicenseState.VALID, r.state());
    }
}
