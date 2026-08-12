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
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The offline backend over a SET of installed licenses.
 *
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class SignedFileLicenseSourceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final String KID = "lic-key-1";
    private static final DeploymentValues ON_H2 = DeploymentValues.of("H2");

    private record Keys(ECPrivateKey priv, ECPublicKey pub) {
    }

    private static Keys keys() throws Exception {
        final KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        final KeyPair kp = g.generateKeyPair();
        return new Keys((ECPrivateKey) kp.getPrivate(), (ECPublicKey) kp.getPublic());
    }

    private static String sign(ECPrivateKey priv, List<String> capabilities, List<String> databases, Instant exp) throws Exception {
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("licensee", "ACME").claim("edition", "ENTERPRISE")
                .claim("capabilities", capabilities)
                .claim("databases", databases)
                .claim("storages", List.of())
                .claim("version", 1)
                .issueTime(Date.from(NOW)).expirationTime(Date.from(exp)).build();
        final JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(KID).type(new JOSEObjectType("license+jws")).build();
        final SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(priv));
        return jwt.serialize();
    }

    private static String signEnterprise(ECPrivateKey priv, Instant exp) throws Exception {
        return sign(priv, List.of("Cat:FEATURE_A", "Cat:FEATURE_B", "Cat:FEATURE_C"), List.of("H2"), exp);
    }

    private static Function<String, ECPublicKey> resolver(ECPublicKey pub) {
        return kid -> KID.equals(kid) ? pub : null;
    }

    private static SignedFileLicenseSource source(Keys k, Collection<String> tokens) {
        return new SignedFileLicenseSource(
                () -> tokens, resolver(k.pub()), () -> NOW, () -> ON_H2, () -> null, Duration.ofSeconds(60));
    }

    @Test
    public void test_validToken_grantsCapabilities() throws Exception {
        final Keys k = keys();
        final SignedFileLicenseSource src = source(k, List.of(signEnterprise(k.priv(), NOW.plus(Duration.ofDays(365)))));

        assertTrue(src.current().valid());
        assertTrue(src.current().has(new Feature("Cat", "FEATURE_C")));
        assertEquals(LicenseState.VALID, src.currentResult().state());
        assertEquals(1, src.currentResult().licenses().size());
    }

    @Test
    public void test_noToken_isNoLicense() {
        final SignedFileLicenseSource src = new SignedFileLicenseSource(
                List::of, kid -> null, () -> NOW, () -> ON_H2, () -> null, Duration.ofSeconds(60));

        assertFalse(src.current().valid());
        assertEquals(LicenseState.NO_LICENSE, src.currentResult().state());
    }

    @Test
    public void test_expiredToken_isExpired() throws Exception {
        final Keys k = keys();
        final SignedFileLicenseSource src = source(k, List.of(signEnterprise(k.priv(), NOW.minus(Duration.ofDays(1)))));

        assertFalse(src.current().valid());
        assertEquals(LicenseState.EXPIRED, src.currentResult().state());
    }

    @Test
    public void test_validBesideExpired_onlyTheValidOneGrants() throws Exception {
        final Keys k = keys();
        final String expired = sign(k.priv(), List.of("Cat:GONE"), List.of("H2"), NOW.minus(Duration.ofDays(1)));
        final String live = sign(k.priv(), List.of("Cat:LIVE"), List.of("H2"), NOW.plus(Duration.ofDays(365)));

        final SignedFileLicenseSource src = source(k, List.of(expired, live));

        assertEquals(LicenseState.VALID, src.currentResult().state());
        assertTrue(src.current().has(new Feature("Cat", "LIVE")));
        assertFalse(src.current().has(new Feature("Cat", "GONE")));
        assertEquals(2, src.currentResult().licenses().size(), "both stay in the breakdown");
    }

    @Test
    public void test_twoValidTokens_grantTheSumOfTheirCapabilities() throws Exception {
        final Keys k = keys();
        final String a = sign(k.priv(), List.of("Cat:A"), List.of("H2"), NOW.plus(Duration.ofDays(365)));
        final String b = sign(k.priv(), List.of("Cat:B"), List.of("H2"), NOW.plus(Duration.ofDays(10)));

        final SignedFileLicenseSource src = source(k, List.of(a, b));

        assertTrue(src.current().has(new Feature("Cat", "A")));
        assertTrue(src.current().has(new Feature("Cat", "B")));
    }

    @Test
    public void test_sameTokenTwice_countsOnce() throws Exception {
        final Keys k = keys();
        final String tok = signEnterprise(k.priv(), NOW.plus(Duration.ofDays(365)));

        // the same license reaching us from the directory and from a DB row is one license, not two.
        final SignedFileLicenseSource src = source(k, List.of(tok, tok));

        assertEquals(1, src.currentResult().licenses().size());
        assertTrue(src.current().valid());
    }

    @Test
    public void test_blankTokensIgnored() throws Exception {
        final Keys k = keys();
        final String tok = signEnterprise(k.priv(), NOW.plus(Duration.ofDays(365)));

        final SignedFileLicenseSource src = source(k, List.of("", "   ", tok));

        assertEquals(1, src.currentResult().licenses().size());
        assertTrue(src.current().valid());
    }

    @Test
    public void test_runningDatabaseNotGranted_nothingIsLicensed() throws Exception {
        final Keys k = keys();
        final String tok = sign(k.priv(), List.of("Cat:A"), List.of("POSTGRES"), NOW.plus(Duration.ofDays(365)));

        final SignedFileLicenseSource src = source(k, List.of(tok));

        assertEquals(LicenseState.DATABASE_NOT_LICENSED, src.currentResult().state());
        assertFalse(src.current().has(new Feature("Cat", "A")));
    }

    @Test
    public void test_invalidate_makesAnInstallVisibleImmediately() throws Exception {
        final Keys k = keys();
        final String tok = signEnterprise(k.priv(), NOW.plus(Duration.ofDays(365)));

        final AtomicReference<Collection<String>> tokens = new AtomicReference<>(List.of());
        final SignedFileLicenseSource src = new SignedFileLicenseSource(
                tokens::get, resolver(k.pub()), () -> NOW, () -> ON_H2, () -> null, Duration.ofSeconds(60));

        assertEquals(LicenseState.NO_LICENSE, src.currentResult().state());

        // the admin installs a license; the clock has not moved, so only invalidation can show it.
        tokens.set(List.of(tok));
        assertEquals(LicenseState.NO_LICENSE, src.currentResult().state(), "still cached");

        src.invalidate();
        assertEquals(LicenseState.VALID, src.currentResult().state(), "invalidated -> re-read");
    }

    @Test
    public void test_invalidate_onAnEmptyCacheIsHarmless() {
        final SignedFileLicenseSource src = new SignedFileLicenseSource(
                List::of, kid -> null, () -> NOW, () -> ON_H2, () -> null, Duration.ofSeconds(60));

        src.invalidate();

        assertEquals(LicenseState.NO_LICENSE, src.currentResult().state());
    }

    @Test
    public void test_cachesWithinTtl_andRefreshesAfter() throws Exception {
        final Keys k = keys();
        final String validTok = signEnterprise(k.priv(), NOW.plus(Duration.ofDays(365)));

        final AtomicReference<Instant> clock = new AtomicReference<>(NOW);
        final AtomicReference<Collection<String>> tokens = new AtomicReference<>(List.of(validTok));

        final SignedFileLicenseSource src = new SignedFileLicenseSource(
                tokens::get, resolver(k.pub()), clock::get, () -> ON_H2, () -> null, Duration.ofSeconds(60));

        // first read verifies and caches VALID at NOW
        assertEquals(LicenseState.VALID, src.currentResult().state());

        // token yanked, but within TTL the cached VALID is still returned
        tokens.set(List.of());
        clock.set(NOW.plus(Duration.ofSeconds(30)));
        assertEquals(LicenseState.VALID, src.currentResult().state(), "within TTL -> cached");

        // past TTL it re-evaluates and now sees no token
        clock.set(NOW.plus(Duration.ofSeconds(61)));
        assertEquals(LicenseState.NO_LICENSE, src.currentResult().state(), "past TTL -> refreshed");
    }
}
