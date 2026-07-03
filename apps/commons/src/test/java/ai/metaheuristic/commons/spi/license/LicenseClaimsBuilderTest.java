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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class LicenseClaimsBuilderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static LicenseConfigYamlV1.License baseLicense() {
        final LicenseConfigYamlV1.License lic = new LicenseConfigYamlV1.License();
        lic.licensee = "ACME Corp";
        lic.edition = "ENTERPRISE";
        lic.features = List.of("JCONS", "LEGAL", "RG");
        return lic;
    }

    @Test
    public void test_explicitExpiresAt() {
        final LicenseConfigYamlV1.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";

        final LicenseClaimsV1 claims = LicenseClaimsBuilder.build(lic, NOW);

        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), claims.exp);
        assertEquals(NOW, claims.iat);
        assertNull(claims.nbf);
    }

    @Test
    public void test_validityDuration() {
        final LicenseConfigYamlV1.License lic = baseLicense();
        lic.validityDuration = "P30D";

        final LicenseClaimsV1 claims = LicenseClaimsBuilder.build(lic, NOW);

        assertEquals(NOW.plus(Duration.parse("P30D")), claims.exp);
    }

    @Test
    public void test_notBefore_parsed() {
        final LicenseConfigYamlV1.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";
        lic.notBefore = "2026-02-01T00:00:00Z";

        final LicenseClaimsV1 claims = LicenseClaimsBuilder.build(lic, NOW);

        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), claims.nbf);
    }

    @Test
    public void test_bothExpiresAtAndDuration_rejected() {
        final LicenseConfigYamlV1.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";
        lic.validityDuration = "P30D";

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> LicenseClaimsBuilder.build(lic, NOW));
        assertTrue(ex.getMessage().startsWith("248.010"), ex.getMessage());
    }

    @Test
    public void test_timeless_withoutRequiredProfiles_rejected() {
        final LicenseConfigYamlV1.License lic = baseLicense();

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> LicenseClaimsBuilder.build(lic, NOW));
        assertTrue(ex.getMessage().startsWith("248.020"), ex.getMessage());
    }

    @Test
    public void test_timeless_withRequiredProfiles_ok() {
        final LicenseConfigYamlV1.License lic = baseLicense();
        lic.edition = "TRIAL";
        lic.requiredProfiles = List.of("h2");

        final LicenseClaimsV1 claims = LicenseClaimsBuilder.build(lic, NOW);

        assertNull(claims.exp);
        assertEquals(List.of("h2"), claims.requiredProfiles);
    }

    @Test
    public void test_blankLicensee_rejected() {
        final LicenseConfigYamlV1.License lic = baseLicense();
        lic.licensee = "  ";
        lic.expiresAt = "2027-01-01T00:00:00Z";

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> LicenseClaimsBuilder.build(lic, NOW));
        assertTrue(ex.getMessage().startsWith("248.030"), ex.getMessage());
    }

    @Test
    public void test_blankEdition_rejected() {
        final LicenseConfigYamlV1.License lic = baseLicense();
        lic.edition = "";
        lic.expiresAt = "2027-01-01T00:00:00Z";

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> LicenseClaimsBuilder.build(lic, NOW));
        assertTrue(ex.getMessage().startsWith("248.040"), ex.getMessage());
    }

    @Test
    public void test_features_opaque_passthrough() {
        final LicenseConfigYamlV1.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";

        final LicenseClaimsV1 claims = LicenseClaimsBuilder.build(lic, NOW);

        assertEquals(List.of("JCONS", "LEGAL", "RG"), claims.features);
    }
}
