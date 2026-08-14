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

import ai.metaheuristic.commons.spi.license.Feature;
import ai.metaheuristic.commons.spi.license.LicenseState;
import ai.metaheuristic.commons.spi.license.LicenseTokenCodec;
import ai.metaheuristic.commons.spi.license.LicenseVerificationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The checked-in test licence still verifies under the checked-in public key, and still grants what
 * the suite runs on.
 *
 * <p>❗ <b>This is the guard for changing the capability vocabulary or the wire format.</b> The
 * capability strings live INSIDE the signed payload, so a rename that touches only Java is not a
 * rename at all - the licence keeps granting the old string, every gate refuses, and the failure
 * surfaces far away as an unrelated Spring test failing on a permission. The licence has to be
 * re-issued by {@code apps/license-signer} whenever the vocabulary moves, and nothing else in the
 * suite notices when it was not.
 *
 * <p>Spring-less on purpose: the licence, the key and the codec are all it takes to answer this,
 * and pinning it here means the answer arrives in milliseconds rather than after a context boots.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseTestFixtureIsCurrentTest {

    /** The suite runs on MH's own capability and no other; a proprietary name here breaches the seal. */
    private static final List<String> EXPECTED = List.of("MH.BATCH");

    private static ECPublicKey testKey() throws IOException {
        final Properties props = new Properties();
        try (InputStream is = LicenseTestFixtureIsCurrentTest.class.getResourceAsStream("/application-test.properties")) {
            assertNotNull(is, "application-test.properties is not on the test classpath");
            props.load(is);
        }
        final String base64 = props.getProperty("mh.test-license.public-key");
        assertNotNull(base64, "mh.test-license.public-key is not set");
        return LicenseTestFixture.publicKey(base64);
    }

    private static LicenseVerificationResult verified() throws IOException {
        return LicenseTokenCodec.verify(
                LicenseTestFixture.readLicense(),
                LicenseTestFixture.keyResolver(testKey()),
                Instant.now(),
                null);
    }

    @Test
    public void test_theCheckedInLicenceVerifiesUnderTheCheckedInKey() throws IOException {
        // a re-issued licence and a stale key are the failure this catches: the signature check is
        // the only thing that notices, and every other symptom is remote from the cause.
        assertEquals(LicenseState.VALID, verified().state());
    }

    @Test
    public void test_itGrantsTheCurrentWireForm() throws IOException {
        final LicenseVerificationResult r = verified();
        assertNotNull(r.claims());
        assertEquals(EXPECTED, r.claims().capabilities);
    }

    @Test
    public void test_theGrantIsReachableThroughAFeature() throws IOException {
        // the string in the payload and the string a gate builds must be the SAME string; asserting
        // the list alone would pass even if Feature composed its key some other way.
        assertTrue(verified().entitlements().has(new Feature("MH", "BATCH")));
    }
}
