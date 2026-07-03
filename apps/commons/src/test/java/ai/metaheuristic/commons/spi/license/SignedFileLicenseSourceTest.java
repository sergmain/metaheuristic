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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class SignedFileLicenseSourceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final String KID = "lic-key-1";

    private record Keys(ECPrivateKey priv, ECPublicKey pub) {
    }

    private static Keys keys() throws Exception {
        final KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        final KeyPair kp = g.generateKeyPair();
        return new Keys((ECPrivateKey) kp.getPrivate(), (ECPublicKey) kp.getPublic());
    }

    private static String signEnterprise(ECPrivateKey priv, Instant exp) throws Exception {
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("licensee", "ACME").claim("edition", "ENTERPRISE")
                .claim("features", List.of("FEATURE_A", "FEATURE_B", "FEATURE_C")).claim("ver", 1)
                .issueTime(Date.from(NOW)).expirationTime(Date.from(exp)).build();
        final JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(KID).type(new JOSEObjectType("license+jws")).build();
        final SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(priv));
        return jwt.serialize();
    }

    private static Function<String, ECPublicKey> resolver(ECPublicKey pub) {
        return kid -> KID.equals(kid) ? pub : null;
    }

    @Test
    public void test_validToken_grantsFeatures() throws Exception {
        final Keys k = keys();
        final String tok = signEnterprise(k.priv(), NOW.plus(Duration.ofDays(365)));
        final SignedFileLicenseSource src = new SignedFileLicenseSource(
                () -> Optional.of(tok), resolver(k.pub()), () -> NOW, Set::of, () -> null, Duration.ofSeconds(60));

        assertTrue(src.current().valid());
        assertTrue(src.current().has(new Feature("FEATURE_C")));
        assertEquals(LicenseState.VALID, src.currentResult().state());
    }

    @Test
    public void test_noToken_isNoLicense() {
        final SignedFileLicenseSource src = new SignedFileLicenseSource(
                Optional::empty, kid -> null, () -> NOW, Set::of, () -> null, Duration.ofSeconds(60));

        assertFalse(src.current().valid());
        assertEquals(LicenseState.NO_LICENSE, src.currentResult().state());
    }

    @Test
    public void test_expiredToken_isExpired() throws Exception {
        final Keys k = keys();
        final String tok = signEnterprise(k.priv(), NOW.minus(Duration.ofDays(1)));
        final SignedFileLicenseSource src = new SignedFileLicenseSource(
                () -> Optional.of(tok), resolver(k.pub()), () -> NOW, Set::of, () -> null, Duration.ofSeconds(60));

        assertFalse(src.current().valid());
        assertEquals(LicenseState.EXPIRED, src.currentResult().state());
    }

    @Test
    public void test_cachesWithinTtl_andRefreshesAfter() throws Exception {
        final Keys k = keys();
        final String validTok = signEnterprise(k.priv(), NOW.plus(Duration.ofDays(365)));

        final AtomicReference<Instant> clock = new AtomicReference<>(NOW);
        final AtomicReference<Optional<String>> token = new AtomicReference<>(Optional.of(validTok));

        final SignedFileLicenseSource src = new SignedFileLicenseSource(
                token::get, resolver(k.pub()), clock::get, Set::of, () -> null, Duration.ofSeconds(60));

        // first read verifies and caches VALID at NOW
        assertEquals(LicenseState.VALID, src.currentResult().state());

        // token yanked, but within TTL the cached VALID is still returned
        token.set(Optional.empty());
        clock.set(NOW.plus(Duration.ofSeconds(30)));
        assertEquals(LicenseState.VALID, src.currentResult().state(), "within TTL -> cached");

        // past TTL it re-evaluates and now sees no token
        clock.set(NOW.plus(Duration.ofSeconds(61)));
        assertEquals(LicenseState.NO_LICENSE, src.currentResult().state(), "past TTL -> refreshed");
    }
}
