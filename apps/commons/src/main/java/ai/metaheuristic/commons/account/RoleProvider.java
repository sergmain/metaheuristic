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

package ai.metaheuristic.commons.account;

import ai.metaheuristic.api.EnumsApi;
import java.util.List;

/**
 * Interface for modules to contribute additional roles to the system.
 * Implementations should be registered as Spring beans and will be
 * automatically discovered by RoleService.
 *
 * @author Serge
 * Date: 12/26/2024
 */
public interface RoleProvider {
    
    /**
     * Returns list of additional roles that this module provides.
     * Roles should follow Spring Security naming convention (e.g., "ROLE_XXX").
     *
     * @return list of role names, never null
     */
    List<String> getAdditionalRoles();

    /**
     * One role plus the two things the installation needs to know about it:
     * who may grant it, and in which companies it means anything.
     *
     * @param role         Spring Security role name, e.g. {@code ROLE_XXX}
     * @param managedBy    who may GRANT this role; {@link RoleManager#admin} is
     *                     ordinary human administration
     * @param scope        which company universes offer it
     */
    record RoleDescriptor(String role, EnumsApi.RoleManager managedBy, EnumsApi.RoleScope scope) {}

    /**
     * Richer form of {@link #getAdditionalRoles()}.
     *
     * <p>A {@code default} on purpose: the implementation below reproduces the
     * behaviour every provider had before this method existed — admin-assignable,
     * offered in both company universes — so no existing provider has to change,
     * and one that does not care never learns this method exists.
     *
     * <p>Override only for a role that is NOT ordinary: one granted by a
     * mechanism rather than by a human, or one that is meaningless for the
     * management company.
     *
     * <p>❗ A role whose {@code managedBy} is not {@code admin} must still appear
     * in the installation's possible-roles list. Withholding it there to make it
     * unassignable would be caught by the wrong mechanism: the role-toggle path
     * silently strips any role an account holds that is not in that list, so the
     * managed role would vanish from managed accounts on the next unrelated
     * toggle. Membership and assignability are separate questions and are
     * answered separately.
     */
    default List<RoleDescriptor> getAdditionalRoleDescriptors() {
        return getAdditionalRoles().stream()
                .map(r -> new RoleDescriptor(r, EnumsApi.RoleManager.admin, EnumsApi.RoleScope.all))
                .toList();
    }
}
