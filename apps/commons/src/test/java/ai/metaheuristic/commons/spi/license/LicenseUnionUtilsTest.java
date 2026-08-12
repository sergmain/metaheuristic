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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * The fold from a set of per-license results to one effective entitlement. Pure - no clock, no
 * filesystem, no Spring - which is why it can be pinned this thoroughly.
 *
 * @author Serge
 */
@Execution(CONCURRENT)
public class LicenseUnionUtilsTest {

    private static final Instant EXP_SOON = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant EXP_LATE = Instant.parse("2027-01-01T00:00:00Z");

    private static final DeploymentValues ON_H2 = DeploymentValues.of("H2");

    private static LicenseVerificationResult lic(
            LicenseState state, List<String> capabilities, List<String> databases,
            List<String> storages, @Nullable Instant exp) {
        final LicenseClaims c = new LicenseClaims();
        c.licensee = "ACME";
        c.edition = "ENTERPRISE";
        c.capabilities = capabilities;
        c.databases = databases;
        c.storages = storages;
        c.exp = exp;
        return new LicenseVerificationResult(state, c, new ClaimsEntitlements(state, exp, new HashSet<>(capabilities)));
    }

    private static LicenseVerificationResult valid(List<String> capabilities, List<String> databases, List<String> storages) {
        return lic(LicenseState.VALID, capabilities, databases, storages, EXP_LATE);
    }

    @Test
    public void test_emptySet_isNoLicense() {
        final LicenseAggregate a = LicenseUnionUtils.fold(List.of(), ON_H2);

        assertEquals(LicenseState.NO_LICENSE, a.state());
        assertFalse(a.entitlements().valid());
        assertTrue(a.licenses().isEmpty());
        assertNull(a.expiresAt());
    }

    @Test
    public void test_singleValidLicense_grantsItsOwnAxes() {
        final LicenseAggregate a = LicenseUnionUtils.fold(
                List.of(valid(List.of("Cap:A"), List.of("H2"), List.of())), ON_H2);

        assertEquals(LicenseState.VALID, a.state());
        assertTrue(a.entitlements().has(new Feature("Cap", "A")));
        assertEquals(Set.of("Cap:A"), a.capabilities());
        assertEquals(Set.of("H2"), a.databases());
        assertEquals(EXP_LATE, a.expiresAt());
    }

    @Test
    public void test_unionOfCapabilities() {
        final LicenseAggregate a = LicenseUnionUtils.fold(List.of(
                valid(List.of("Cap:A", "Cap:B"), List.of("H2"), List.of()),
                valid(List.of("Cap:C"), List.of("POSTGRES"), List.of("S3"))), ON_H2);

        assertEquals(LicenseState.VALID, a.state());
        assertEquals(Set.of("Cap:A", "Cap:B", "Cap:C"), a.capabilities());
        assertEquals(Set.of("H2", "POSTGRES"), a.databases());
        assertEquals(Set.of("S3"), a.storages());
    }

    @Test
    public void test_unionIsOrderIndependent() {
        final LicenseVerificationResult x = valid(List.of("Cap:A"), List.of("H2"), List.of());
        final LicenseVerificationResult y = valid(List.of("Cap:B"), List.of("POSTGRES"), List.of("S3"));

        final LicenseAggregate a = LicenseUnionUtils.fold(List.of(x, y), ON_H2);
        final LicenseAggregate b = LicenseUnionUtils.fold(List.of(y, x), ON_H2);

        assertEquals(a.capabilities(), b.capabilities());
        assertEquals(a.databases(), b.databases());
        assertEquals(a.storages(), b.storages());
        assertEquals(a.state(), b.state());
    }

    @Test
    public void test_unionWithItself_equalsItself() {
        final LicenseVerificationResult x = valid(List.of("Cap:A"), List.of("H2"), List.of());

        final LicenseAggregate one = LicenseUnionUtils.fold(List.of(x), ON_H2);
        final LicenseAggregate twice = LicenseUnionUtils.fold(List.of(x, x), ON_H2);

        assertEquals(one.capabilities(), twice.capabilities());
        assertEquals(one.databases(), twice.databases());
        assertEquals(one.state(), twice.state());
        assertEquals(one.expiresAt(), twice.expiresAt());
    }

    @Test
    public void test_invalidLicenseIsSkipped_notFatal() {
        final LicenseAggregate a = LicenseUnionUtils.fold(List.of(
                lic(LicenseState.EXPIRED, List.of("Cap:GONE"), List.of("MYSQL"), List.of("S3"), EXP_SOON),
                lic(LicenseState.SIGNATURE_INVALID, List.of("Cap:FAKE"), List.of("MYSQL"), List.of(), null),
                valid(List.of("Cap:A"), List.of("H2"), List.of())), ON_H2);

        assertEquals(LicenseState.VALID, a.state());
        assertEquals(Set.of("Cap:A"), a.capabilities());
        assertFalse(a.entitlements().has(new Feature("Cap", "GONE")));
        assertEquals(3, a.licenses().size(), "the breakdown keeps every installed license");
    }

    @Test
    public void test_expiresAt_isLatestAmongValid() {
        final LicenseAggregate a = LicenseUnionUtils.fold(List.of(
                lic(LicenseState.VALID, List.of("Cap:A"), List.of("H2"), List.of(), EXP_SOON),
                lic(LicenseState.VALID, List.of("Cap:B"), List.of("H2"), List.of(), EXP_LATE)), ON_H2);

        assertEquals(EXP_LATE, a.expiresAt(), "the instant the installation loses ALL coverage");
        assertEquals(EXP_LATE, a.entitlements().expiresAt().orElse(null));
    }

    @Test
    public void test_emptyDatabaseList_grantsNoDatabase() {
        // an empty allow-list is a grant of nothing, not 'unconstrained'.
        final LicenseAggregate a = LicenseUnionUtils.fold(
                List.of(valid(List.of("Cap:A"), List.of(), List.of())), ON_H2);

        assertEquals(LicenseState.DATABASE_NOT_LICENSED, a.state());
    }

    @Test
    public void test_databaseNotLicensed_gatesEveryCapabilityOff() {
        // an unlicensed deployment licenses nothing.
        final LicenseAggregate a = LicenseUnionUtils.fold(
                List.of(valid(List.of("Cap:A"), List.of("POSTGRES"), List.of())), ON_H2);

        assertEquals(LicenseState.DATABASE_NOT_LICENSED, a.state());
        assertFalse(a.entitlements().valid());
        assertFalse(a.entitlements().has(new Feature("Cap", "A")));
        assertTrue(a.capabilities().isEmpty());
        assertTrue(a.databases().isEmpty());
    }

    @Test
    public void test_databaseCoveredByAnotherLicenseInTheSet() {
        // a license that does not list the running database is not invalid - it just contributes nothing there.
        final LicenseAggregate a = LicenseUnionUtils.fold(List.of(
                valid(List.of("Cap:A"), List.of("POSTGRES"), List.of()),
                valid(List.of("Cap:B"), List.of("H2"), List.of())), ON_H2);

        assertEquals(LicenseState.VALID, a.state());
        assertEquals(Set.of("Cap:A", "Cap:B"), a.capabilities());
    }

    @Test
    public void test_storageNotLicensed() {
        final LicenseAggregate a = LicenseUnionUtils.fold(
                List.of(valid(List.of("Cap:A"), List.of("H2"), List.of())),
                new DeploymentValues("H2", "S3"));

        assertEquals(LicenseState.STORAGE_NOT_LICENSED, a.state());
        assertFalse(a.entitlements().has(new Feature("Cap", "A")));
    }

    @Test
    public void test_storageLicensed() {
        final LicenseAggregate a = LicenseUnionUtils.fold(
                List.of(valid(List.of("Cap:A"), List.of("H2"), List.of("S3"))),
                new DeploymentValues("H2", "S3"));

        assertEquals(LicenseState.VALID, a.state());
        assertEquals(Set.of("S3"), a.storages());
    }

    @Test
    public void test_noStorageBackendActive_storageAxisNotChecked() {
        // nothing is running on a storage backend, so an empty 'storages' grant is not a gap.
        final LicenseAggregate a = LicenseUnionUtils.fold(
                List.of(valid(List.of("Cap:A"), List.of("H2"), List.of())), ON_H2);

        assertEquals(LicenseState.VALID, a.state());
    }

    @Test
    public void test_noneValid_reportsHowCoverageWouldBeRegained() {
        final LicenseAggregate a = LicenseUnionUtils.fold(List.of(
                lic(LicenseState.SIGNATURE_INVALID, List.of(), List.of(), List.of(), null),
                lic(LicenseState.EXPIRED, List.of("Cap:A"), List.of("H2"), List.of(), EXP_SOON)), ON_H2);

        assertEquals(LicenseState.EXPIRED, a.state(), "renewing the expired one is the way back to coverage");
        assertFalse(a.entitlements().valid());
        assertEquals(2, a.licenses().size());
    }

    @Test
    public void test_axesAreIndependent() {
        // constraining the database says nothing about storage.
        final LicenseAggregate a = LicenseUnionUtils.fold(List.of(
                valid(List.of("Cap:A"), List.of("H2"), List.of()),
                valid(List.of("Cap:B"), List.of(), List.of("S3"))),
                new DeploymentValues("H2", "S3"));

        assertEquals(LicenseState.VALID, a.state());
        assertEquals(Set.of("H2"), a.databases());
        assertEquals(Set.of("S3"), a.storages());
    }
}
