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
    private static final String COMPANY1_ONLY = "ROLE_COMPANY1_ONLY";

    /** Implements only the old method — exactly what an untouched provider looks like. */
    private static final RoleProvider LEGACY_PROVIDER = () -> List.of(LEGACY);

    private static final RoleProvider RICH_PROVIDER = new RoleProvider() {
        @Override
        public List<String> getAdditionalRoles() {
            return List.of(MANAGED, REGULAR_ONLY, COMPANY1_ONLY);
        }
        @Override
        public List<RoleDescriptor> getAdditionalRoleDescriptors() {
            return List.of(
                new RoleDescriptor(MANAGED, EnumsApi.RoleManager.commChannel, EnumsApi.RoleScope.notCompany1),
                new RoleDescriptor(REGULAR_ONLY, EnumsApi.RoleManager.admin, EnumsApi.RoleScope.notCompany1),
                new RoleDescriptor(COMPANY1_ONLY, EnumsApi.RoleManager.admin, EnumsApi.RoleScope.company1));
        }
    };

    // ---------- backward compatibility ----------

    /**
     * A provider that never heard of descriptors must behave exactly as before:
     * admin-assignable, offered in BOTH company universes.
     */
    @Test
    public void test_providerWithoutDescriptors_behavesAsBefore() {
        RoleService s = new RoleService(List.of(LEGACY_PROVIDER));

        assertTrue(s.getPossibleRoles().contains(LEGACY));
        assertTrue(s.getCompany1PossibleRoles().contains(LEGACY));
        assertTrue(s.isValidRole(LEGACY));
        assertTrue(s.isValidCompany1Role(LEGACY));
        assertTrue(s.isAssignableByAdmin(LEGACY));
        assertEquals(EnumsApi.RoleManager.admin, s.getRoleManager(LEGACY));
    }

    @Test
    public void test_baseRolesAreUntouched() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        for (String r : SecConsts.POSSIBLE_ROLES) {
            assertTrue(s.getPossibleRoles().contains(r), r);
            assertTrue(s.isAssignableByAdmin(r), r);
        }
        for (String r : SecConsts.COMPANY_1_POSSIBLE_ROLES) {
            assertTrue(s.getCompany1PossibleRoles().contains(r), r);
            assertTrue(s.isAssignableByAdmin(r), r);
        }
    }

    @Test
    public void test_noProviders_isSafe() {
        RoleService s = new RoleService(null);

        assertEquals(SecConsts.POSSIBLE_ROLES, s.getPossibleRoles());
        assertEquals(SecConsts.COMPANY_1_POSSIBLE_ROLES, s.getCompany1PossibleRoles());
    }

    // ---------- scope ----------

    @Test
    public void test_notCompany1Scope_keepsTheRoleOutOfTheManagementUniverse() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertTrue(s.getPossibleRoles().contains(REGULAR_ONLY));
        assertFalse(s.getCompany1PossibleRoles().contains(REGULAR_ONLY));
        assertTrue(s.isValidRole(REGULAR_ONLY));
        assertFalse(s.isValidCompany1Role(REGULAR_ONLY));
    }

    @Test
    public void test_company1Scope_isTheMirrorImage() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertFalse(s.getPossibleRoles().contains(COMPANY1_ONLY));
        assertTrue(s.getCompany1PossibleRoles().contains(COMPANY1_ONLY));
    }

    // ---------- the trap ----------

    /**
     * ❗ A mechanism-managed role must remain a VALID, listed role. Withholding it
     * from the list to make it unassignable would be caught by the wrong
     * mechanism: {@code storeRolesForUserById} strips any held role missing from
     * the list, so the managed role would vanish from managed accounts on the
     * next unrelated toggle.
     */
    @Test
    public void test_managedRole_staysValidButIsNotAdminAssignable() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertTrue(s.getPossibleRoles().contains(MANAGED),
                "a managed role must stay in the list or the strip loop deletes it");
        assertTrue(s.isValidRole(MANAGED));

        assertFalse(s.isAssignableByAdmin(MANAGED));
        assertEquals(EnumsApi.RoleManager.commChannel, s.getRoleManager(MANAGED));
    }

    /** Membership and assignability are independent questions. */
    @Test
    public void test_validityAndAssignability_areIndependent() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        // valid + assignable
        assertTrue(s.isValidRole(REGULAR_ONLY) && s.isAssignableByAdmin(REGULAR_ONLY));
        // valid + NOT assignable
        assertTrue(s.isValidRole(MANAGED) && !s.isAssignableByAdmin(MANAGED));
        // not valid for regular companies, still admin-assignable where it is valid
        assertFalse(s.isValidRole(COMPANY1_ONLY));
        assertTrue(s.isAssignableByAdmin(COMPANY1_ONLY));
    }

    /** An unknown role is admin-managed rather than throwing. */
    @Test
    public void test_unknownRole_isAdminManaged() {
        RoleService s = new RoleService(List.of(RICH_PROVIDER));

        assertEquals(EnumsApi.RoleManager.admin, s.getRoleManager("ROLE_NEVER_HEARD_OF"));
        assertTrue(s.isAssignableByAdmin("ROLE_NEVER_HEARD_OF"));
    }

    // ---------- EnumsApi.RoleScope predicates ----------

    @Test
    public void test_roleScopePredicates() {
        assertTrue(EnumsApi.RoleScope.all.appliesToCompany1());
        assertTrue(EnumsApi.RoleScope.all.appliesToRegularCompany());

        assertTrue(EnumsApi.RoleScope.company1.appliesToCompany1());
        assertFalse(EnumsApi.RoleScope.company1.appliesToRegularCompany());

        assertFalse(EnumsApi.RoleScope.notCompany1.appliesToCompany1());
        assertTrue(EnumsApi.RoleScope.notCompany1.appliesToRegularCompany());
    }
}
