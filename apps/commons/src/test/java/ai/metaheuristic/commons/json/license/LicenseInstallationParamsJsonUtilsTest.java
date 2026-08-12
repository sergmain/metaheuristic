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

import ai.metaheuristic.api.data.license.LicenseInstallationParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The MH_LICENSE_INSTALLATION.PARAMS chain.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseInstallationParamsJsonUtilsTest {

    @Test
    public void test_roundTrip() {
        final LicenseInstallationParams p = new LicenseInstallationParams();
        p.installationId = "3f2c1a90-0000-0000-0000-000000000000";
        p.createdOn = 1780272000000L;

        final String json = LicenseInstallationParamsJsonUtils.BASE_JSON_UTILS.toString(p);
        final LicenseInstallationParams back = LicenseInstallationParamsJsonUtils.BASE_JSON_UTILS.to(json);

        assertEquals(1, back.version);
        assertEquals("3f2c1a90-0000-0000-0000-000000000000", back.installationId);
        assertEquals(1780272000000L, back.createdOn);
    }

    @Test
    public void test_writtenJson_carriesTopLevelVersion() {
        final LicenseInstallationParams p = new LicenseInstallationParams();
        p.installationId = "uuid-A";

        assertTrue(LicenseInstallationParamsJsonUtils.BASE_JSON_UTILS.toString(p).contains("\"version\":1"));
    }

    @Test
    public void test_v1Document_parsesIntoCurrentType_withoutAVersionBump() {
        final String json = """
                {"version":1,"installationId":"uuid-A","createdOn":17}""";

        final LicenseInstallationParams p = LicenseInstallationParamsJsonUtils.BASE_JSON_UTILS.to(json);

        assertEquals("uuid-A", p.installationId);
        assertEquals(17L, p.createdOn);
    }
}
