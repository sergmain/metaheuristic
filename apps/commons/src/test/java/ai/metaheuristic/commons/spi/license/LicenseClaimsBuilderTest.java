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

import ai.metaheuristic.api.data.license.LicenseClaims;
import ai.metaheuristic.api.data.license.LicenseConfigYaml;

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

    private static LicenseConfigYaml.License baseLicense() {
        final LicenseConfigYaml.License lic = new LicenseConfigYaml.License();
        lic.licensee = "ACME Corp";
        lic.edition = "ENTERPRISE";
        lic.capabilities = List.of("Cat.FEATURE_A", "Cat.FEATURE_B", "Cat.FEATURE_C");
        lic.databases = List.of("H2", "POSTGRES");
        lic.storages = List.of("S3");
        return lic;
    }

    @Test
    public void test_explicitExpiresAt() {
        final LicenseConfigYaml.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";

        final LicenseClaims claims = LicenseClaimsBuilder.build(lic, NOW);

        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), claims.exp);
        assertEquals(NOW, claims.iat);
        assertNull(claims.nbf);
    }

    @Test
    public void test_validityDuration() {
        final LicenseConfigYaml.License lic = baseLicense();
        lic.validityDuration = "P30D";

        final LicenseClaims claims = LicenseClaimsBuilder.build(lic, NOW);

        assertEquals(NOW.plus(Duration.parse("P30D")), claims.exp);
    }

    @Test
    public void test_notBefore_parsed() {
        final LicenseConfigYaml.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";
        lic.notBefore = "2026-02-01T00:00:00Z";

        final LicenseClaims claims = LicenseClaimsBuilder.build(lic, NOW);

        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), claims.nbf);
    }

    @Test
    public void test_bothExpiresAtAndDuration_rejected() {
        final LicenseConfigYaml.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";
        lic.validityDuration = "P30D";

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> LicenseClaimsBuilder.build(lic, NOW));
        assertTrue(ex.getMessage().startsWith("248.010"), ex.getMessage());
    }

    @Test
    public void test_noExp_rejected() {
        // there is no timeless license: neither an absolute instant nor a duration means no signing.
        final LicenseConfigYaml.License lic = baseLicense();

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> LicenseClaimsBuilder.build(lic, NOW));
        assertTrue(ex.getMessage().startsWith("01.248.020"), ex.getMessage());
    }

    @Test
    public void test_blankLicensee_rejected() {
        final LicenseConfigYaml.License lic = baseLicense();
        lic.licensee = "  ";
        lic.expiresAt = "2027-01-01T00:00:00Z";

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> LicenseClaimsBuilder.build(lic, NOW));
        assertTrue(ex.getMessage().startsWith("248.030"), ex.getMessage());
    }

    @Test
    public void test_blankEdition_rejected() {
        final LicenseConfigYaml.License lic = baseLicense();
        lic.edition = "";
        lic.expiresAt = "2027-01-01T00:00:00Z";

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> LicenseClaimsBuilder.build(lic, NOW));
        assertTrue(ex.getMessage().startsWith("248.040"), ex.getMessage());
    }

    @Test
    public void test_capabilities_opaque_passthrough() {
        final LicenseConfigYaml.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";

        final LicenseClaims claims = LicenseClaimsBuilder.build(lic, NOW);

        assertEquals(List.of("Cat.FEATURE_A", "Cat.FEATURE_B", "Cat.FEATURE_C"), claims.capabilities);
    }

    @Test
    public void test_deploymentAxes_passthrough() {
        final LicenseConfigYaml.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";

        final LicenseClaims claims = LicenseClaimsBuilder.build(lic, NOW);

        assertEquals(List.of("H2", "POSTGRES"), claims.databases);
        assertEquals(List.of("S3"), claims.storages);
    }

    @Test
    public void test_emptyDeploymentAxes_areLegal() {
        // empty grants nothing on that axis, which is a meaningful license and not an error.
        final LicenseConfigYaml.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";
        lic.databases = List.of();
        lic.storages = List.of();

        final LicenseClaims claims = LicenseClaimsBuilder.build(lic, NOW);

        assertTrue(claims.databases.isEmpty());
        assertTrue(claims.storages.isEmpty());
    }

    @Test
    public void test_blankCapability_rejected() {
        final LicenseConfigYaml.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";
        lic.capabilities = List.of(" ");

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> LicenseClaimsBuilder.build(lic, NOW));
        assertTrue(ex.getMessage().startsWith("01.248.050"), ex.getMessage());
    }

    @Test
    public void test_capabilityNeedsNoShape_isTakenVerbatim() {
        // A capability is ONE opaque name. The issuer does not get to have an opinion on what a
        // name may look like: whatever shape it demanded would become a rule every future
        // capability had to obey, enforced in the one place that only ever copies the string.
        final LicenseConfigYaml.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";
        lic.capabilities = List.of("FEATURE_A", "MH.BATCH", "a.b.c");

        final LicenseClaims claims = LicenseClaimsBuilder.build(lic, NOW);

        assertEquals(List.of("FEATURE_A", "MH.BATCH", "a.b.c"), claims.capabilities);
    }

    @Test
    public void test_blankDeploymentValue_rejected() {
        final LicenseConfigYaml.License lic = baseLicense();
        lic.expiresAt = "2027-01-01T00:00:00Z";
        lic.databases = List.of("H2", " ");

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> LicenseClaimsBuilder.build(lic, NOW));
        assertTrue(ex.getMessage().startsWith("01.248.070"), ex.getMessage());
    }
}
