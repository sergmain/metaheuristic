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

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.jspecify.annotations.Nullable;
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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip: sign with Nimbus (as the license-signer does), verify with LicenseTokenCodec.
 *
 * The vocabulary here is deliberately made-up. The codec must behave identically for capability
 * categories it has never seen, which is the whole point of keeping them opaque strings.
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
                .claim("capabilities", List.of("Cat:FEATURE_A", "Cat:FEATURE_B", "Cat:FEATURE_C"))
                .claim("databases", List.of("H2", "POSTGRES"))
                .claim("storages", List.of("S3"))
                .claim("version", 1)
                .issueTime(Date.from(NOW));
    }

    private static Function<String, @Nullable ECPublicKey> resolver(ECPublicKey pub) {
        return kid -> KID.equals(kid) ? pub : null;
    }

    @Test
    public void test_validRoundTrip() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, null);

        assertEquals(LicenseState.VALID, r.state());
        assertTrue(r.entitlements().valid());
        assertTrue(r.entitlements().has(new Feature("Cat", "FEATURE_C")));
        assertFalse(r.entitlements().has(new Feature("Cat", "NOT_GRANTED")));
        assertTrue(r.entitlements().expiresAt().isPresent());
    }

    @Test
    public void test_deploymentAxes_areParsedButNotEnforcedHere() throws Exception {
        // a token that lists no database at all is still a perfectly VALID license: whether the
        // running deployment is covered is decided once, against the union of every valid license.
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().claim("databases", List.of()).claim("storages", List.of())
                        .expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, null);

        assertEquals(LicenseState.VALID, r.state());
        assertNotNull(r.claims());
        assertTrue(r.claims().databases.isEmpty());
        assertTrue(r.claims().storages.isEmpty());
    }

    @Test
    public void test_claimsAreReadThroughTheChain() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, null);

        assertNotNull(r.claims());
        assertEquals(1, r.claims().version);
        assertEquals("ACME", r.claims().licensee);
        assertEquals("ENTERPRISE", r.claims().edition);
        assertEquals(List.of("Cat:FEATURE_A", "Cat:FEATURE_B", "Cat:FEATURE_C"), r.claims().capabilities);
        assertEquals(List.of("H2", "POSTGRES"), r.claims().databases);
        assertEquals(List.of("S3"), r.claims().storages);
        assertEquals(NOW, r.claims().iat);
        assertEquals(NOW.plus(Duration.ofDays(365)), r.claims().exp);
    }

    @Test
    public void test_expired() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.minus(Duration.ofDays(1)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, null);

        assertEquals(LicenseState.EXPIRED, r.state());
        assertFalse(r.entitlements().valid());
        assertFalse(r.entitlements().has(new Feature("Cat", "FEATURE_C")));
    }

    @Test
    public void test_notYetValid() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().notBeforeTime(Date.from(NOW.plus(Duration.ofDays(2))))
                        .expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, null);

        assertEquals(LicenseState.NOT_YET_VALID, r.state());
    }

    @Test
    public void test_leeway_expJustPast_stillValid() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.minus(Duration.ofSeconds(30)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, null);

        assertEquals(LicenseState.VALID, r.state(), "30s past exp is within the +-60s leeway");
    }

    @Test
    public void test_tamperedSignature() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r =
                LicenseTokenCodec.verify(tamperSignature(tok), resolver((ECPublicKey) kp.getPublic()), NOW, null);

        assertEquals(LicenseState.SIGNATURE_INVALID, r.state());
    }

    /**
     * Flip the FIRST character of the signature segment, not the last.
     *
     * An ES256 signature is 64 bytes and its base64url form is 86 characters, so 86*6 = 516 bits
     * carry 512 significant ones: the LAST character holds 2 significant bits and 4 unused padding
     * bits. 'A' and 'B' differ only in a padding bit, so swapping them at the end decodes to the
     * same 64 bytes and the token still verifies - which made the obvious "change the last char"
     * tamper a test that passed 63 times out of 64 and failed whenever the signature happened to
     * end in 'A'. The first character is fully significant, so this always mutates the signature.
     */
    private static String tamperSignature(String token) {
        final int lastDot = token.lastIndexOf('.');
        final char first = token.charAt(lastDot + 1);
        return token.substring(0, lastDot + 1) + (first == 'C' ? 'D' : 'C') + token.substring(lastDot + 2);
    }

    @Test
    public void test_unknownKid() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, _ -> null, NOW, null);

        // NOT SIGNATURE_INVALID: the signature here is good and is never examined. The kid selected
        // no key, and saying "bad signature" would send the reader to inspect key material that was
        // never in question.
        assertEquals(LicenseState.UNKNOWN_KID, r.state());
    }

    @Test
    public void test_wrongKey() throws Exception {
        final KeyPair signer = ecKeyPair();
        final KeyPair other = ecKeyPair();
        final String tok = sign((ECPrivateKey) signer.getPrivate(),
                enterprise().expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) other.getPublic()), NOW, null);

        assertEquals(LicenseState.SIGNATURE_INVALID, r.state());
    }

    @Test
    public void test_noExp_isMalformed() throws Exception {
        // there is no timeless license: an omitted exp is a defect, not a perpetual grant.
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(), enterprise().build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, null);

        assertEquals(LicenseState.MALFORMED, r.state());
        assertFalse(r.entitlements().valid());
    }

    @Test
    public void test_unreadableClaimVersion_isMalformed() throws Exception {
        // the signature checks out, so blaming the signature would send the admin after the wrong fault.
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().claim("version", 7)
                        .expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, null);

        assertEquals(LicenseState.MALFORMED, r.state());
    }

    @Test
    public void test_installIdMismatch() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().claim("installationId", "uuid-A")
                        .expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, "uuid-B");

        assertEquals(LicenseState.INSTALL_ID_MISMATCH, r.state());
    }

    @Test
    public void test_installIdMatch_valid() throws Exception {
        final KeyPair kp = ecKeyPair();
        final String tok = sign((ECPrivateKey) kp.getPrivate(),
                enterprise().claim("installationId", "uuid-A")
                        .expirationTime(Date.from(NOW.plus(Duration.ofDays(365)))).build());

        final LicenseVerificationResult r = LicenseTokenCodec.verify(tok, resolver((ECPublicKey) kp.getPublic()), NOW, "uuid-A");

        assertEquals(LicenseState.VALID, r.state());
    }
}
