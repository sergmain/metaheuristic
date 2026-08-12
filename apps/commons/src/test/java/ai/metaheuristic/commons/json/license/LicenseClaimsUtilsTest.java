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

package ai.metaheuristic.commons.json.license;

import ai.metaheuristic.api.data.license.LicenseClaims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The license-claims JSON chain. The payload lives inside a JOSE token, so the two shapes that
 * matter are: what the chain writes, and what it reads back from a claims set whose registered
 * date claims are NumericDate (seconds since the epoch, per RFC 7519).
 *
 * The capability vocabulary here is deliberately made-up. MH stores these strings as data and
 * never as symbols, so the chain must behave identically for values it has never seen - and
 * naming the real proprietary ones here would breach the seal for no gain.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseClaimsUtilsTest {

    private static final Instant IAT = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant EXP = Instant.parse("2027-06-01T00:00:00Z");

    private static LicenseClaims sample() {
        final LicenseClaims c = new LicenseClaims();
        c.licensee = "ACME Aerospace, Inc.";
        c.edition = "ENTERPRISE";
        c.capabilities = List.of("Cat:ALPHA", "Cat:BETA");
        c.databases = List.of("H2", "POSTGRES");
        c.storages = List.of("S3");
        c.iat = IAT;
        c.exp = EXP;
        return c;
    }

    @Test
    public void test_roundTrip() {
        final String json = LicenseClaimsUtils.BASE_JSON_UTILS.toString(sample());
        final LicenseClaims back = LicenseClaimsUtils.BASE_JSON_UTILS.to(json);

        assertEquals(1, back.version);
        assertEquals("ACME Aerospace, Inc.", back.licensee);
        assertEquals("ENTERPRISE", back.edition);
        assertEquals(List.of("Cat:ALPHA", "Cat:BETA"), back.capabilities);
        assertEquals(List.of("H2", "POSTGRES"), back.databases);
        assertEquals(List.of("S3"), back.storages);
        assertEquals(IAT, back.iat);
        assertEquals(EXP, back.exp);
        assertNull(back.nbf);
        assertNull(back.installationId);
    }

    @Test
    public void test_writtenJson_carriesTopLevelVersion() {
        final String json = LicenseClaimsUtils.BASE_JSON_UTILS.toString(sample());
        assertTrue(json.contains("\"version\":1"), json);
    }

    @Test
    public void test_fromJson_readsNumericDateClaims() {
        // exactly the shape a JWTClaimsSet serializes to: registered date claims as NumericDate.
        final String json = """
                {"licensee":"ACME","edition":"TRIAL","version":1,\
                "capabilities":["Cat:ALPHA"],"databases":["H2"],"storages":[],\
                "iat":1780272000,"exp":1782864000,"installationId":"uuid-A"}""";

        final LicenseClaims c = LicenseClaimsUtils.fromJson(1, json);

        assertEquals("ACME", c.licensee);
        assertEquals("TRIAL", c.edition);
        assertEquals(List.of("Cat:ALPHA"), c.capabilities);
        assertEquals(List.of("H2"), c.databases);
        assertTrue(c.storages.isEmpty());
        assertEquals(Instant.ofEpochSecond(1780272000L), c.iat);
        assertEquals(Instant.ofEpochSecond(1782864000L), c.exp);
        assertNull(c.nbf);
        assertEquals("uuid-A", c.installationId);
    }

    @Test
    public void test_fromJson_absentListsBecomeEmpty() {
        final String json = """
                {"licensee":"ACME","edition":"TRIAL","version":1,"exp":1782864000}""";

        final LicenseClaims c = LicenseClaimsUtils.fromJson(1, json);

        assertTrue(c.capabilities.isEmpty());
        assertTrue(c.databases.isEmpty());
        assertTrue(c.storages.isEmpty());
    }

    @Test
    public void test_fromJson_unsupportedVersion_rejected() {
        final String json = """
                {"licensee":"ACME","edition":"TRIAL","version":7,"exp":1782864000}""";

        assertThrows(RuntimeException.class, () -> LicenseClaimsUtils.fromJson(7, json));
    }
}
