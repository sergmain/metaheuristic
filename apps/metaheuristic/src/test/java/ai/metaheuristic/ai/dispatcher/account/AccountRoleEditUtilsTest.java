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

package ai.metaheuristic.ai.dispatcher.account;

import ai.metaheuristic.ai.sec.SecConsts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * AccountRoleEditUtils has no Spring dependency, so the whole ADMIN role-edit rule set
 * is exercisable directly.
 *
 * @author Sergio Lissner
 * Date: 8/21/2026
 */
@Execution(CONCURRENT)
public class AccountRoleEditUtilsTest {

    private static final String MANAGED = "ROLE_MANAGED_BY_MECHANISM";

    /** The universe an ADMIN is offered: the regular roles plus one mechanism-managed role. */
    private static final List<String> ADMIN_UNIVERSE =
            Stream.concat(SecConsts.POSSIBLE_ROLES.stream(), Stream.of(MANAGED)).toList();

    private static final Predicate<String> ASSIGNABLE = r -> !MANAGED.equals(r);

    private static AccountRoleEditUtils.Verdict validate(
            Long targetCompanyId, Long callerCompanyId, String managedBy, String role) {
        return AccountRoleEditUtils.validateToggle(targetCompanyId, callerCompanyId, managedBy, role, ADMIN_UNIVERSE, ASSIGNABLE);
    }

    // ---------- company scope: the rule this whole path exists for ----------

    @Test
    public void test_sameCompany_isAllowed() {
        AccountRoleEditUtils.Verdict v = validate(7L, 7L, null, "ROLE_OPERATOR");

        assertTrue(v.allowed());
        assertNull(v.error());
    }

    @Test
    public void test_otherCompany_isRefused() {
        AccountRoleEditUtils.Verdict v = validate(8L, 7L, null, "ROLE_OPERATOR");

        assertFalse(v.allowed());
        assertNotNull(v.error());
        assertTrue(v.error().startsWith("01.242.020"), v.error());
    }

    /** An account with no company at all is not "in my company" either. */
    @Test
    public void test_targetWithoutCompany_isRefused() {
        AccountRoleEditUtils.Verdict v = validate(null, 7L, null, "ROLE_OPERATOR");

        assertFalse(v.allowed());
        assertTrue(v.error().startsWith("01.242.020"), v.error());
    }

    @Test
    public void test_callerWithoutCompany_isRefused() {
        AccountRoleEditUtils.Verdict v = validate(7L, null, null, "ROLE_OPERATOR");

        assertFalse(v.allowed());
        assertTrue(v.error().startsWith("01.242.010"), v.error());
    }

    /**
     * Company 1 is the management company, so its own ADMIN is the account most worth
     * pinning down: a MAIN_* role must not be reachable from this path in ANY company.
     */
    @Test
    public void test_adminOfCompany1_cannotGrantAMainRole() {
        for (String mainRole : SecConsts.COMPANY_1_POSSIBLE_ROLES) {
            AccountRoleEditUtils.Verdict v = validate(1L, 1L, null, mainRole);

            assertFalse(v.allowed(), mainRole);
            assertTrue(v.error().startsWith("01.242.050"), mainRole + " -> " + v.error());
        }
        AccountRoleEditUtils.Verdict v = validate(1L, 1L, null, SecConsts.ROLE_MAIN_ADMIN);
        assertFalse(v.allowed());
        assertTrue(v.error().startsWith("01.242.050"), v.error());
    }

    /** Inside company 1 the regular roles are still ordinary business. */
    @Test
    public void test_adminOfCompany1_canStillGrantARegularRole() {
        assertTrue(validate(1L, 1L, null, "ROLE_OPERATOR").allowed());
    }

    // ---------- mechanism gates ----------

    @Test
    public void test_mechanismManagedAccount_isRefusedEvenForAnOrdinaryRole() {
        AccountRoleEditUtils.Verdict v = validate(7L, 7L, "commChannel", "ROLE_OPERATOR");

        assertFalse(v.allowed());
        assertTrue(v.error().startsWith("01.242.030"), v.error());
        assertTrue(v.error().contains("commChannel"), v.error());
    }

    /**
     * A managed role stays IN the universe by design, so list membership alone would let it
     * through — assignability is the separate gate that stops it.
     */
    @Test
    public void test_managedRole_isListedYetRefused() {
        assertTrue(ADMIN_UNIVERSE.contains(MANAGED));

        AccountRoleEditUtils.Verdict v = validate(7L, 7L, null, MANAGED);

        assertFalse(v.allowed());
        assertTrue(v.error().startsWith("01.242.060"), v.error());
    }

    @Test
    public void test_unknownRole_isRefused() {
        AccountRoleEditUtils.Verdict v = validate(7L, 7L, null, "ROLE_NEVER_HEARD_OF");

        assertFalse(v.allowed());
        assertTrue(v.error().startsWith("01.242.050"), v.error());
    }

    @Test
    public void test_blankRole_isRefused() {
        assertTrue(validate(7L, 7L, null, "").error().startsWith("01.242.040"));
        assertTrue(validate(7L, 7L, null, "   ").error().startsWith("01.242.040"));
        assertTrue(validate(7L, 7L, null, null).error().startsWith("01.242.040"));
    }

    /** Company scope is checked before anything else — a foreign account reveals nothing further. */
    @Test
    public void test_companyScopeIsCheckedBeforeTheRoleItself() {
        AccountRoleEditUtils.Verdict v = validate(8L, 7L, "commChannel", "ROLE_NEVER_HEARD_OF");

        assertTrue(v.error().startsWith("01.242.020"), v.error());
    }

    // ---------- the toggle ----------

    @Test
    public void test_grantAppendsTheRole() {
        assertEquals(List.of("ROLE_OPERATOR", "ROLE_MANAGER"),
                AccountRoleEditUtils.toggle(List.of("ROLE_OPERATOR"), "ROLE_MANAGER", true));
    }

    @Test
    public void test_revokeRemovesOnlyTheNamedRole() {
        assertEquals(List.of("ROLE_OPERATOR", "ROLE_DATA"),
                AccountRoleEditUtils.toggle(List.of("ROLE_OPERATOR", "ROLE_MANAGER", "ROLE_DATA"), "ROLE_MANAGER", false));
    }

    @Test
    public void test_grantingAHeldRoleIsANoOp() {
        assertEquals(List.of("ROLE_OPERATOR"),
                AccountRoleEditUtils.toggle(List.of("ROLE_OPERATOR"), "ROLE_OPERATOR", true));
    }

    @Test
    public void test_revokingAnAbsentRoleIsANoOp() {
        assertEquals(List.of("ROLE_OPERATOR"),
                AccountRoleEditUtils.toggle(List.of("ROLE_OPERATOR"), "ROLE_MANAGER", false));
    }

    /**
     * The reason this is a toggle and not a rewrite. An ADMIN never sees ROLE_MAIN_ADMIN, so
     * toggling a role they DO see must leave it exactly where it was. A strip-the-unlisted loop
     * here would be a silent demotion.
     */
    @Test
    public void test_rolesOutsideTheAdminUniverseSurviveAToggle() {
        List<String> before = List.of(SecConsts.ROLE_MAIN_ADMIN, SecConsts.ROLE_SERVER_REST_ACCESS, "ROLE_OPERATOR");

        List<String> after = AccountRoleEditUtils.toggle(before, "ROLE_OPERATOR", false);

        assertEquals(List.of(SecConsts.ROLE_MAIN_ADMIN, SecConsts.ROLE_SERVER_REST_ACCESS), after);

        List<String> afterGrant = AccountRoleEditUtils.toggle(before, "ROLE_DATA", true);
        assertTrue(afterGrant.contains(SecConsts.ROLE_MAIN_ADMIN));
        assertTrue(afterGrant.contains(SecConsts.ROLE_SERVER_REST_ACCESS));
        assertTrue(afterGrant.contains("ROLE_DATA"));
    }

    @Test
    public void test_toggleOfAnEmptyRoleSet() {
        assertEquals(List.of("ROLE_OPERATOR"), AccountRoleEditUtils.toggle(List.of(), "ROLE_OPERATOR", true));
        assertEquals(List.of(), AccountRoleEditUtils.toggle(List.of(), "ROLE_OPERATOR", false));
    }
}
