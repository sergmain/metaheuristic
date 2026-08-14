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

import ai.metaheuristic.api.data.license.LicenseArtifactParams;
import ai.metaheuristic.api.data.license.LicenseClaims;
import ai.metaheuristic.commons.spi.license.ClaimsEntitlements;
import ai.metaheuristic.commons.spi.license.LicenseState;
import ai.metaheuristic.commons.spi.license.LicenseVerificationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The per-license breakdown. Spring-less: correlating results to rows is a map lookup, and the one
 * thing that can go wrong with it — joining by position instead — is exactly what these pin.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseInfoUtilsTest {

    private static final Instant IAT = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant EXP = Instant.parse("2027-06-01T00:00:00Z");

    private static LicenseVerificationResult result(String token, LicenseState state, String capability) {
        final LicenseClaims c = new LicenseClaims();
        c.licensee = "ACME";
        c.edition = "ENTERPRISE";
        c.capabilities = List.of(capability);
        c.databases = List.of("H2");
        c.storages = List.of();
        c.iat = IAT;
        c.exp = EXP;
        return new LicenseVerificationResult(token, state, c, new ClaimsEntitlements(state, EXP, Set.of(capability)));
    }

    private static LicenseInfoUtils.RowInfo row(long id, long installedOn) {
        return new LicenseInfoUtils.RowInfo(id, LicenseArtifactParams.Origin.DB, installedOn);
    }

    @Test
    public void test_licenseWithNoRow_camefromTheDirectory() {
        final List<LicenseInfoData.InstalledLicense> out =
                LicenseInfoUtils.breakdown(
                        List.of(result("tok-A", LicenseState.VALID, "Cap.A")), Map.of(), Set.of());

        assertEquals(1, out.size());
        assertNull(out.getFirst().artifactId());
        assertEquals(LicenseArtifactParams.Origin.DIRECTORY, out.getFirst().origin());
        assertNull(out.getFirst().installedOn());
    }

    @Test
    public void test_licenseWithARow_carriesItsIdAndInstallDate() {
        final List<LicenseInfoData.InstalledLicense> out = LicenseInfoUtils.breakdown(
                List.of(result("tok-A", LicenseState.VALID, "Cap.A")),
                Map.of(LicenseTokenHashUtils.hash("tok-A"), row(7L, 1234L)), Set.of());

        assertEquals(7L, out.getFirst().artifactId());
        assertEquals(LicenseArtifactParams.Origin.DB, out.getFirst().origin());
        assertEquals(1234L, out.getFirst().installedOn());
    }

    @Test
    public void test_correlationIsByHash_notByPosition() {
        // the aggregate lists a directory license FIRST and a DB license second; a positional join
        // would hand the row to the wrong one. Only the second has a row.
        final List<LicenseInfoData.InstalledLicense> out = LicenseInfoUtils.breakdown(
                List.of(result("from-disk", LicenseState.VALID, "Cap.A"),
                        result("from-row", LicenseState.VALID, "Cap.B")),
                Map.of(LicenseTokenHashUtils.hash("from-row"), row(9L, 555L)), Set.of());

        assertNull(out.get(0).artifactId(), "the directory license must not inherit the row");
        assertEquals(LicenseArtifactParams.Origin.DIRECTORY, out.get(0).origin());
        assertEquals(9L, out.get(1).artifactId());
        assertEquals(LicenseArtifactParams.Origin.DB, out.get(1).origin());
    }

    @Test
    public void test_whitespaceDoesNotSplitOneLicenseIntoTwo() {
        // a file read with a trailing newline and the same token pasted into the UI are ONE license.
        final List<LicenseInfoData.InstalledLicense> out = LicenseInfoUtils.breakdown(
                List.of(result("tok-A\n", LicenseState.VALID, "Cap.A")),
                Map.of(LicenseTokenHashUtils.hash("tok-A"), row(3L, 99L)), Set.of());

        assertEquals(3L, out.getFirst().artifactId());
    }

    @Test
    public void test_invalidLicensesStayInTheBreakdown() {
        // this is how an admin finds the expired one; dropping them would hide the problem.
        final List<LicenseInfoData.InstalledLicense> out = LicenseInfoUtils.breakdown(
                List.of(result("a", LicenseState.EXPIRED, "Cap.A"),
                        result("b", LicenseState.VALID, "Cap.B"),
                        result("c", LicenseState.INSTALL_ID_MISMATCH, "Cap.C")), Map.of(), Set.of());

        assertEquals(3, out.size());
        assertEquals(LicenseState.EXPIRED, out.get(0).state());
        assertEquals(LicenseState.VALID, out.get(1).state());
        assertEquals(LicenseState.INSTALL_ID_MISMATCH, out.get(2).state());
    }

    @Test
    public void test_eachLicenseReportsItsOwnGrants_notTheUnion() {
        final List<LicenseInfoData.InstalledLicense> out = LicenseInfoUtils.breakdown(
                List.of(result("a", LicenseState.VALID, "Cap.A"),
                        result("b", LicenseState.VALID, "Cap.B")), Map.of(), Set.of());

        assertEquals(List.of("Cap.A"), out.get(0).capabilities());
        assertEquals(List.of("Cap.B"), out.get(1).capabilities());
    }

    @Test
    public void test_unparseableLicenseHasNoClaims_butStillListed() {
        final LicenseVerificationResult r =
                new LicenseVerificationResult("junk", LicenseState.SIGNATURE_INVALID, null,
                        ClaimsEntitlements.invalid(LicenseState.SIGNATURE_INVALID));

        final List<LicenseInfoData.InstalledLicense> out =
                LicenseInfoUtils.breakdown(List.of(r), Map.of(), Set.of());

        assertEquals(1, out.size());
        assertNull(out.getFirst().licensee());
        assertNull(out.getFirst().exp());
        assertTrue(out.getFirst().capabilities().isEmpty());
        assertEquals(LicenseState.SIGNATURE_INVALID, out.getFirst().state());
    }

    @Test
    public void test_timesAreEpochMillis() {
        final List<LicenseInfoData.InstalledLicense> out =
                LicenseInfoUtils.breakdown(
                        List.of(result("a", LicenseState.VALID, "Cap.A")), Map.of(), Set.of());

        assertEquals(IAT.toEpochMilli(), out.getFirst().iat());
        assertEquals(EXP.toEpochMilli(), out.getFirst().exp());
        assertNull(LicenseInfoUtils.toMillis(null));
    }

    @Test
    public void test_emptyAggregate_isEmptyBreakdown() {
        assertTrue(LicenseInfoUtils.breakdown(List.of(), Map.of(), Set.of()).isEmpty());
    }

    @Test
    public void test_sameTokenFromDirectoryAndDb_isListedOncePerChannel() {
        // The SAME licence file lying in the licence directory AND uploaded through the UI. That is
        // TWO installed artifacts, retired two different ways: one by deleting a file, one by the
        // remove button. The token set is de-duplicated before verification, so the aggregate holds
        // ONE result for both - which must not cost the admin the directory copy.
        final List<LicenseInfoData.InstalledLicense> out = LicenseInfoUtils.breakdown(
                List.of(result("shared", LicenseState.VALID, "Cap.A")),
                Map.of(LicenseTokenHashUtils.hash("shared"), row(4L, 777L)),
                Set.of(LicenseTokenHashUtils.hash("shared")));

        assertEquals(2, out.size());
        assertNull(out.get(0).artifactId(), "the copy on disk has no row to flip");
        assertEquals(LicenseArtifactParams.Origin.DIRECTORY, out.get(0).origin());
        assertNull(out.get(0).installedOn());
        assertEquals(4L, out.get(1).artifactId());
        assertEquals(LicenseArtifactParams.Origin.DB, out.get(1).origin());
        assertEquals(777L, out.get(1).installedOn());
        // one licence, one verdict: both channels report the state the token actually has.
        assertEquals(LicenseState.VALID, out.get(0).state());
        assertEquals(LicenseState.VALID, out.get(1).state());
    }
}
