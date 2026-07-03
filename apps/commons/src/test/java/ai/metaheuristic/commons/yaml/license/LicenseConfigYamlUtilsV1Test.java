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

package ai.metaheuristic.commons.yaml.license;

import ai.metaheuristic.commons.spi.license.LicenseClaimsBuilder;
import ai.metaheuristic.commons.spi.license.LicenseClaimsV1;
import ai.metaheuristic.api.data.license.LicenseConfigYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the YAML parse path (LicenseConfigYamlUtils.to) and its integration with
 * LicenseClaimsBuilder, using inline recipes shaped like the RG-side examples. Kept in MH
 * with no external files so it stays seal-clean and self-contained.
 *
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class LicenseConfigYamlUtilsV1Test {

    private static final String ENTERPRISE_YAML = """
            version: 1
            license:
              licensee: "ACME Aerospace, Inc."
              edition: "ENTERPRISE"
              features:
                - "FEATURE_A"
                - "FEATURE_B"
                - "FEATURE_C"
              validityDuration: "P365D"
            signing:
              algorithm: "ES256"
              privateKeyFile: "/path/to/vendor-ec-private.key"
              kid: "lic-key-1"
              outputFile: "./license.jws"
            """;

    private static final String TRIAL_TIMELESS_YAML = """
            version: 1
            license:
              licensee: "Evaluation User"
              edition: "TRIAL"
              features:
                - "FEATURE_A"
                - "FEATURE_B"
                - "FEATURE_C"
              requiredProfiles:
                - "h2"
              forbiddenProfiles:
                - "mysql"
                - "generic"
            signing:
              algorithm: "ES256"
              privateKeyFile: "/path/to/vendor-ec-private.key"
              kid: "lic-key-1"
              outputFile: "./license-trial.jws"
            """;

    @Test
    public void test_parse_enterprise() {
        final LicenseConfigYaml c = LicenseConfigYamlUtils.BASE_YAML_UTILS.to(ENTERPRISE_YAML);
        assertEquals(1, c.version);
        assertEquals("ACME Aerospace, Inc.", c.license.licensee);
        assertEquals("ENTERPRISE", c.license.edition);
        assertEquals(List.of("FEATURE_A", "FEATURE_B", "FEATURE_C"), c.license.features);
        assertEquals("P365D", c.license.validityDuration);
        assertNull(c.license.expiresAt);
        assertEquals("ES256", c.signing.algorithm);
        assertEquals("lic-key-1", c.signing.kid);
        assertEquals("./license.jws", c.signing.outputFile);
    }

    @Test
    public void test_parse_trialTimeless() {
        final LicenseConfigYaml c = LicenseConfigYamlUtils.BASE_YAML_UTILS.to(TRIAL_TIMELESS_YAML);
        assertEquals("TRIAL", c.license.edition);
        assertNull(c.license.expiresAt);
        assertNull(c.license.validityDuration);
        assertEquals(List.of("h2"), c.license.requiredProfiles);
        assertEquals(List.of("mysql", "generic"), c.license.forbiddenProfiles);
    }

    @Test
    public void test_parse_then_build_enterprise() {
        final Instant now = Instant.parse("2026-01-01T00:00:00Z");
        final LicenseConfigYaml c = LicenseConfigYamlUtils.BASE_YAML_UTILS.to(ENTERPRISE_YAML);
        final LicenseClaimsV1 claims = LicenseClaimsBuilder.build(c.license, now);
        assertEquals(now.plus(Duration.parse("P365D")), claims.exp);
        assertEquals(List.of("FEATURE_A", "FEATURE_B", "FEATURE_C"), claims.features);
    }

    @Test
    public void test_parse_then_build_trialTimeless() {
        final Instant now = Instant.parse("2026-01-01T00:00:00Z");
        final LicenseConfigYaml c = LicenseConfigYamlUtils.BASE_YAML_UTILS.to(TRIAL_TIMELESS_YAML);
        final LicenseClaimsV1 claims = LicenseClaimsBuilder.build(c.license, now);
        assertNull(claims.exp);
        assertEquals(List.of("h2"), claims.requiredProfiles);
        assertEquals(List.of("mysql", "generic"), claims.forbiddenProfiles);
    }
}
