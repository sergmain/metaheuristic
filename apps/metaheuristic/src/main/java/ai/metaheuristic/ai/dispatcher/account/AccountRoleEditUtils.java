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

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The decision half of an ADMIN-initiated role edit, kept free of Spring, JPA and
 * the account beans so the whole rule set is exercisable as plain functions.
 *
 * <p>An ADMIN edits roles WITHIN ONE COMPANY — their own. That company never arrives
 * from the request; the caller resolves it from the authentication principal and hands
 * it in here as {@code callerCompanyId}. This class only decides whether the edit is
 * permitted and what the resulting role set is; persistence stays in
 * {@link AccountTxService}.
 *
 * <p>Error code prefix: {@code 01.242.} (unique to this class).
 *
 * @author Sergio Lissner
 * Date: 8/21/2026
 */
public final class AccountRoleEditUtils {

    private AccountRoleEditUtils() {
    }

    /**
     * Outcome of validating a single role toggle. {@code error} is null exactly when
     * the toggle may proceed.
     */
    public record Verdict(@Nullable String error) {
        public static final Verdict ALLOWED = new Verdict(null);

        public boolean allowed() {
            return error == null;
        }
    }

    /**
     * Whether an ADMIN may toggle {@code role} on the account described by the first
     * three parameters.
     *
     * <p>Four independent refusals, in the order a reader needs them:
     * <ol>
     *   <li>the target account belongs to another company — the scope rule the whole
     *       feature exists to enforce;</li>
     *   <li>the account's role set is owned by a mechanism ({@code managedBy}), so no
     *       hand-editing of it in either direction;</li>
     *   <li>the role is outside the universe an ADMIN may hand out;</li>
     *   <li>the role is in that universe but is granted by a mechanism.</li>
     * </ol>
     *
     * <p>(3) and (4) are separate on purpose: a mechanism-managed role must REMAIN a
     * listed, valid role, or every path that reasons about list membership would treat
     * it as junk. See {@code RoleService#isAssignableByAdmin}.
     *
     * @param adminUniverse roles an ADMIN may hand out. Always the regular universe,
     *   never the management-company one — an ADMIN of the management company must not be able
     *   to grant themselves a {@code ROLE_MAIN_*} role, which would be an escalation out
     *   of their own company rather than an edit inside it.
     */
    public static Verdict validateToggle(
            @Nullable Long targetAccountCompanyId, @Nullable Long callerCompanyId, @Nullable String managedBy,
            @Nullable String role, List<String> adminUniverse, Predicate<String> assignableByAdmin) {

        if (callerCompanyId == null) {
            return new Verdict("01.242.010 System error, companyId of the current user is null");
        }
        if (targetAccountCompanyId == null || !targetAccountCompanyId.equals(callerCompanyId)) {
            return new Verdict("01.242.020 Account doesn't belong to the current company and can't be edited");
        }
        if (managedBy != null) {
            return new Verdict("01.242.030 Account's roles are managed by '" + managedBy + "' and can't be edited by hand");
        }
        if (role == null || role.isBlank()) {
            return new Verdict("01.242.040 System error, role is blank");
        }
        if (!adminUniverse.contains(role)) {
            return new Verdict("01.242.050 Role " + role + " can't be assigned within a company");
        }
        if (!assignableByAdmin.test(role)) {
            return new Verdict("01.242.060 Role " + role + " is granted by a mechanism and can't be assigned by hand");
        }
        return Verdict.ALLOWED;
    }

    /**
     * The role set after toggling exactly one role, leaving every other role untouched.
     *
     * <p>Deliberately NOT the strip-everything-unlisted loop that
     * {@code AccountTxService#storeRolesForUserById} runs for a MAIN_ADMIN. An ADMIN
     * sees a narrower universe than the account may legitimately hold — a management-company
     * account carries {@code ROLE_MAIN_*} roles that are absent from the ADMIN's
     * universe — so stripping the unlisted ones would turn a toggle of
     * {@code ROLE_OPERATOR} into a silent demotion of everything the ADMIN cannot see.
     * Touch the named role, nothing else.
     *
     * @return the resulting roles, in their original order, with the toggled role
     *   appended when it is being granted
     */
    public static List<String> toggle(List<String> currentRoles, String role, boolean checkbox) {
        boolean present = currentRoles.contains(role);
        if (checkbox == present) {
            return List.copyOf(currentRoles);
        }
        if (checkbox) {
            return java.util.stream.Stream.concat(currentRoles.stream(), java.util.stream.Stream.of(role)).toList();
        }
        return currentRoles.stream().filter(r -> !r.equals(role)).toList();
    }
}
