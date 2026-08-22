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

/**
 * The decision half of an ADMIN-initiated role edit, kept free of Spring, JPA and
 * the account beans so the whole rule set is exercisable as plain functions.
 *
 * <p>An ADMIN edits roles WITHIN ONE COMPANY — their own. That company never arrives
 * from the request; the caller resolves it from the authentication principal and hands
 * it in here as {@code callerCompanyId}. Company scope is the whole of the restriction:
 * inside it, every account and every role of that company's universe is the
 * administrator's to change. This class only decides whether the edit is permitted and
 * what the resulting role set is; persistence stays in {@link AccountTxService}.
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
     * two parameters.
     *
     * <p>COMPANY SCOPE IS THE ONLY RESTRICTION ON WHO MAY BE EDITED. Within their own
     * company an administrator administers every account and every role their company's
     * universe contains — including an account minted by a mechanism, and including a
     * role a mechanism normally grants. {@code ROLE_RG_ENSEMBLE} is the live case: it is
     * an ordinary role of a regular company, so it is an ordinary admin action.
     *
     * <p>{@code EnumsApi.RoleManager} therefore describes PROVENANCE — which mechanism
     * mints a role when one is minted — and is not consulted here. The one place it still
     * decides anything is {@code CommChannelServiceRegistry}, where a service declaration
     * must name a role that mechanism owns; that is a statement about the declaration, not
     * about what a human may do afterwards.
     *
     * <p>Three refusals remain, in the order a reader needs them:
     * <ol>
     *   <li>the caller has no company — a system error, not a policy decision;</li>
     *   <li>the target account belongs to another company — the scope rule the whole
     *       feature exists to enforce;</li>
     *   <li>the role is outside the universe of the caller's company.</li>
     * </ol>
     *
     * @param companyUniverse roles assignable in the CALLER's own company — the
     *   management-company list for the management company, the regular list otherwise.
     *   Which company a role belongs in is a property of the role
     *   ({@code EnumsApi.RoleScope}), and it is the only thing that narrows the offer.
     */
    public static Verdict validateToggle(
            @Nullable Long targetAccountCompanyId, @Nullable Long callerCompanyId,
            @Nullable String role, List<String> companyUniverse) {

        if (callerCompanyId == null) {
            return new Verdict("01.242.010 System error, companyId of the current user is null");
        }
        if (targetAccountCompanyId == null || !targetAccountCompanyId.equals(callerCompanyId)) {
            return new Verdict("01.242.020 Account doesn't belong to the current company and can't be edited");
        }
        if (role == null || role.isBlank()) {
            return new Verdict("01.242.040 System error, role is blank");
        }
        if (!companyUniverse.contains(role)) {
            return new Verdict("01.242.050 Role " + role + " can't be assigned within a company");
        }
        return Verdict.ALLOWED;
    }

    /**
     * The role set after toggling exactly one role, leaving every other role untouched.
     *
     * <p>Deliberately NOT the strip-everything-unlisted loop that
     * {@code AccountTxService#storeRolesForUserById} runs for a MAIN_ADMIN. An account may
     * legitimately hold a role absent from the universe currently being offered, so
     * stripping the unlisted ones would turn a toggle of {@code ROLE_OPERATOR} into a
     * silent demotion of everything not on screen. Touch the named role, nothing else.
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
