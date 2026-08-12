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

import ai.metaheuristic.api.data.license.LicenseArtifactParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The MH_LICENSE_ARTIFACT.PARAMS chain.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseArtifactParamsJsonUtilsTest {

    private static LicenseArtifactParams sample() {
        final LicenseArtifactParams p = new LicenseArtifactParams();
        p.token = "eyJhbGciOiJFUzI1NiJ9.payload.signature";
        p.installedOn = 1780272000000L;
        p.installedByAccountId = 42L;
        p.origin = LicenseArtifactParams.Origin.DB;
        return p;
    }

    @Test
    public void test_roundTrip() {
        final String json = LicenseArtifactParamsJsonUtils.BASE_JSON_UTILS.toString(sample());
        final LicenseArtifactParams back = LicenseArtifactParamsJsonUtils.BASE_JSON_UTILS.to(json);

        assertEquals(1, back.version);
        assertEquals("eyJhbGciOiJFUzI1NiJ9.payload.signature", back.token);
        assertEquals(1780272000000L, back.installedOn);
        assertEquals(42L, back.installedByAccountId);
        assertEquals(LicenseArtifactParams.Origin.DB, back.origin);
    }

    @Test
    public void test_writtenJson_carriesTopLevelVersion() {
        final String json = LicenseArtifactParamsJsonUtils.BASE_JSON_UTILS.toString(sample());
        assertTrue(json.contains("\"version\":1"), json);
    }

    @Test
    public void test_v1Document_parsesIntoCurrentType_withoutAVersionBump() {
        final String json = """
                {"version":1,"token":"a.b.c","installedOn":17,"installedByAccountId":null,"origin":"DIRECTORY"}""";

        final LicenseArtifactParams p = LicenseArtifactParamsJsonUtils.BASE_JSON_UTILS.to(json);

        assertEquals("a.b.c", p.token);
        assertEquals(17L, p.installedOn);
        assertNull(p.installedByAccountId);
        assertEquals(LicenseArtifactParams.Origin.DIRECTORY, p.origin);
    }

    @Test
    public void test_directoryOrigin_hasNoInstallingAccount() {
        // a license found on disk was never uploaded by anyone, so the field stays null rather
        // than being back-filled with a fictitious installer.
        final LicenseArtifactParams p = sample();
        p.origin = LicenseArtifactParams.Origin.DIRECTORY;
        p.installedByAccountId = null;

        final LicenseArtifactParams back =
                LicenseArtifactParamsJsonUtils.BASE_JSON_UTILS.to(LicenseArtifactParamsJsonUtils.BASE_JSON_UTILS.toString(p));

        assertNull(back.installedByAccountId);
        assertEquals(LicenseArtifactParams.Origin.DIRECTORY, back.origin);
    }

    @Test
    public void test_absentOrigin_defaultsToDb() {
        final String json = """
                {"version":1,"token":"a.b.c","installedOn":17}""";

        assertEquals(LicenseArtifactParams.Origin.DB, LicenseArtifactParamsJsonUtils.BASE_JSON_UTILS.to(json).origin);
    }
}
