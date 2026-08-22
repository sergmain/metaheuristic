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

package ai.metaheuristic.ai.sec;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.account.RoleProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * RoleService has no Spring dependency in its constructor, so the whole
 * aggregation rule is exercisable directly.
 *
 * @author Sergio Lissner
 * Date: 8/2/2026
 */
@Execution(CONCURRENT)
public class RoleServiceTest {

    private static final String LEGACY = "ROLE_LEGACY_PROVIDER";
    private static final String MANAGED = "ROLE_MANAGED_BY_MECHANISM";
    private static final String REGULAR_ONLY = "ROLE_REGULAR_ONLY";
    private static final String MANAGEMENT_COMPANY_ONLY = "ROLE_MANAGEMENT_COMPANY_ONLY";

    /** Implements only the old method — exactly what an untouched provider looks like. */
    private static final RoleProvider LEGACY_PROVIDER = () -> List.of(LEGACY);

    private static final RoleProvider RICH_PROVIDER = new RoleProvider() {
        @Override
        public List<String> getAdditionalRoles() {
            return List.of(MANAGED, REGULAR_ONLY, MANAGEMENT_COMPANY_ONLY);
        }
        @Override
        public List<RoleDescriptor> getAdditionalRoleDescriptors() {
            return List.of(
                new RoleDescriptor(MANAGED, EnumsApi.RoleManager.commChannel, EnumsApi.RoleScope.notManagementCompany),
                new RoleDescriptor(REGULAR_ONLY, EnumsApi.RoleManager.admin, EnumsApi.RoleScope.notManagementCompany),
                new RoleDescriptor(MANAGEMENT_COMPANY_ONLY, EnumsApi.RoleManager.admin, EnumsApi.RoleScope.managementCompany));
        }
    };

    // ---------- the universe an administrator acts in ----------

    /**
     * A company's universe is what its administrator may assign, in full. Nothing is
     * subtracted for being mechanism-minted: ROLE_RG_ENSEMBLE is scoped
     * notManagementCompany, so it is an ordinary assignable role of a regular company.
     */
    @Test
    public void test_rolesOfCompany_offerTheWholeRegularUniverseIncludingAManagedRole() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertEquals(s.getPossibleRoles(), s.rolesOfCompany(7L));
        assertTrue(s.rolesOfCompany(7L).contains(MANAGED));
    }

    @Test
    public void test_rolesOfCompany_giveTheManagementCompanyItsOwnUniverse() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertEquals(s.getManagementCompanyPossibleRoles(), s.rolesOfCompany(1L));
        assertTrue(s.rolesOfCompany(1L).contains(MANAGEMENT_COMPANY_ONLY));
        assertFalse(s.rolesOfCompany(1L).contains(REGULAR_ONLY));
    }

    /** No company resolves to the regular universe rather than throwing. */
    @Test
    public void test_rolesOfCompany_defaultToTheRegularUniverse() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertEquals(s.getPossibleRoles(), s.rolesOfCompany(null));
    }

    // ---------- backward compatibility ----------

    /**
     * A provider that never heard of descriptors must behave exactly as before:
     * admin-assignable, offered in BOTH company universes.
     */
    @Test
    public void test_providerWithoutDescriptors_behavesAsBefore() {
        RoleService s = new RoleService(List.of(LEGACY_PROVIDER));

        assertTrue(s.getPossibleRoles().contains(LEGACY));
        assertTrue(s.getManagementCompanyPossibleRoles().contains(LEGACY));
        assertTrue(s.isValidRole(LEGACY));
        assertTrue(s.isValidManagementCompanyRole(LEGACY));
        assertEquals(EnumsApi.RoleManager.admin, s.getRoleManager(LEGACY));
    }

    @Test
    public void test_baseRolesAreUntouched() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        for (String r : SecConsts.POSSIBLE_ROLES) {
            assertTrue(s.getPossibleRoles().contains(r), r);
            assertEquals(EnumsApi.RoleManager.admin, s.getRoleManager(r), r);
        }
        for (String r : SecConsts.MANAGEMENT_COMPANY_POSSIBLE_ROLES) {
            assertTrue(s.getManagementCompanyPossibleRoles().contains(r), r);
            assertEquals(EnumsApi.RoleManager.admin, s.getRoleManager(r), r);
        }
    }

    @Test
    public void test_noProviders_isSafe() {
        RoleService s = new RoleService(null);

        assertEquals(SecConsts.POSSIBLE_ROLES, s.getPossibleRoles());
        assertEquals(SecConsts.MANAGEMENT_COMPANY_POSSIBLE_ROLES, s.getManagementCompanyPossibleRoles());
    }

    // ---------- scope ----------

    @Test
    public void test_notManagementCompanyScope_keepsTheRoleOutOfTheManagementUniverse() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertTrue(s.getPossibleRoles().contains(REGULAR_ONLY));
        assertFalse(s.getManagementCompanyPossibleRoles().contains(REGULAR_ONLY));
        assertTrue(s.isValidRole(REGULAR_ONLY));
        assertFalse(s.isValidManagementCompanyRole(REGULAR_ONLY));
    }

    @Test
    public void test_managementCompanyScope_isTheMirrorImage() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertFalse(s.getPossibleRoles().contains(MANAGEMENT_COMPANY_ONLY));
        assertTrue(s.getManagementCompanyPossibleRoles().contains(MANAGEMENT_COMPANY_ONLY));
    }

    // ---------- the trap ----------

    /**
     * A mechanism-managed role is a VALID, listed, ASSIGNABLE role. The manager records
     * which mechanism mints one when one is minted — provenance — and withholds nothing
     * from the administrator of the company the role is scoped to.
     */
    @Test
    public void test_managedRole_isAnOrdinaryMemberOfItsUniverse() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertTrue(s.getPossibleRoles().contains(MANAGED));
        assertTrue(s.isValidRole(MANAGED));
        assertTrue(s.rolesOfCompany(7L).contains(MANAGED));

        assertEquals(EnumsApi.RoleManager.commChannel, s.getRoleManager(MANAGED));
    }

    /**
     * The manager is still read — CommChannelServiceRegistry refuses to start if a service
     * declaration names a role some other mechanism owns — so it must stay accurate even
     * though it no longer gates assignment.
     */
    @Test
    public void test_roleManager_isReportedAccuratelyForTheRegistry() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertEquals(EnumsApi.RoleManager.commChannel, s.getRoleManager(MANAGED));
        assertEquals(EnumsApi.RoleManager.admin, s.getRoleManager(REGULAR_ONLY));
    }

    /** An unknown role is admin-managed rather than throwing. */
    @Test
    public void test_unknownRole_isAdminManaged() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertEquals(EnumsApi.RoleManager.admin, s.getRoleManager("ROLE_NEVER_HEARD_OF"));
    }

    // ---------- EnumsApi.RoleScope predicates ----------

    @Test
    public void test_roleScopePredicates() {
        assertTrue(EnumsApi.RoleScope.all.managementCompanyUniverse);
        assertTrue(EnumsApi.RoleScope.all.regularUniverse);

        assertTrue(EnumsApi.RoleScope.managementCompany.managementCompanyUniverse);
        assertFalse(EnumsApi.RoleScope.managementCompany.regularUniverse);

        assertFalse(EnumsApi.RoleScope.notManagementCompany.managementCompanyUniverse);
        assertTrue(EnumsApi.RoleScope.notManagementCompany.regularUniverse);
    }
}
