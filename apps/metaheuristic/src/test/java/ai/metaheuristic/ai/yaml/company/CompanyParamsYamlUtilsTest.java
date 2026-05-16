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

package ai.metaheuristic.ai.yaml.company;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CompanyParams YAML version chain.
 *
 * <p>The {@code vault} field was added to V2 as a {@code @Nullable} field
 * without a version bump (per the multi-versioning {@code @Nullable}-exception
 * rule), so V2 stays the latest version — these tests assert {@code version==2}
 * end-to-end and confirm older V2 YAML lacking {@code vault} still loads.
 *
 * @author Sergio Lissner
 */
@Execution(ExecutionMode.CONCURRENT)
class CompanyParamsYamlUtilsTest {

    @Test
    void upgradeFromV1_carriesAccessControl_andLeavesVaultNull() {
        String v1Yaml = """
            version: 1
            ac:
              groups: 'admins'
            """;
        CompanyParamsYaml result = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(v1Yaml);
        assertNotNull(result);
        assertEquals(2, result.version);
        assertNotNull(result.ac);
        assertEquals("admins", result.ac.groups);
        assertNull(result.vault, "V1 had no vault, should remain null after upgrade");
    }

    @Test
    void loadV2_withoutVaultField_deserializesWithNullVault() {
        // Old V2 YAML predating the @Nullable vault field. setSkipMissingProperties(true)
        // means the missing field deserializes fine and stays null.
        String v2Yaml = """
            version: 2
            createdOn: 1700000000000
            updatedOn: 1700000001000
            ac:
              groups: 'devs'
            """;
        CompanyParamsYaml result = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(v2Yaml);
        assertNotNull(result);
        assertEquals(2, result.version);
        assertNotNull(result.ac);
        assertEquals("devs", result.ac.groups);
        assertEquals(1700000000000L, result.createdOn);
        assertEquals(1700000001000L, result.updatedOn);
        assertNull(result.vault, "Old V2 YAML lacks vault, should remain null");
    }

    @Test
    void roundTripV2_withVault_preservesAllFields() {
        CompanyParamsYaml src = new CompanyParamsYaml();
        src.createdOn = 1234567890L;
        src.updatedOn = 1234567891L;
        src.ac = new CompanyParamsYaml.AccessControl("ops");
        src.vault = new CompanyParamsYaml.VaultEntries("c2FsdC1iYXNlNjQ=", 200_000, "Y2lwaGVydGV4dC1iYXNlNjQ=");

        String yaml = CompanyParamsYamlUtils.BASE_YAML_UTILS.toString(src);
        CompanyParamsYaml round = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertNotNull(round);
        assertEquals(2, round.version);
        assertEquals(1234567890L, round.createdOn);
        assertEquals(1234567891L, round.updatedOn);
        assertNotNull(round.ac);
        assertEquals("ops", round.ac.groups);
        assertNotNull(round.vault);
        assertEquals("c2FsdC1iYXNlNjQ=", round.vault.salt);
        assertEquals(200_000, round.vault.iterations);
        assertEquals("Y2lwaGVydGV4dC1iYXNlNjQ=", round.vault.encryptedEntries);
    }

    @Test
    void emptyDocument_givesDefaultLatest() {
        // No version field — defaults to V1, upgrades to latest (V2) cleanly.
        String emptyYaml = "ac:\n  groups: 'x'\n";
        CompanyParamsYaml result = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(emptyYaml);
        assertNotNull(result);
        assertEquals(2, result.version);
        assertNull(result.vault);
    }
}
