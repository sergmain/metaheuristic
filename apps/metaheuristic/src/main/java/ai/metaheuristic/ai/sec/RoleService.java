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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service that aggregates possible roles from SecConsts and any RoleProvider beans.
 * This allows external modules to contribute additional roles without modifying core code.
 *
 * @author Serge
 * Date: 12/26/2024
 */
@Service
@Profile("dispatcher")
@Slf4j
public class RoleService {

    private final List<String> possibleRoles;
    private final List<String> company1PossibleRoles;

    /**
     * Who may grant each role. A role absent from this map is
     * {@link EnumsApi.RoleManager#admin}-managed, which is every base role and every
     * provider role that did not say otherwise.
     */
    private final Map<String, EnumsApi.RoleManager> roleManagers;

    public RoleService(@Autowired(required = false) List<RoleProvider> roleProviders) {
        List<RoleProvider.RoleDescriptor> descriptors = new ArrayList<>();
        if (roleProviders != null) {
            for (RoleProvider provider : roleProviders) {
                List<RoleProvider.RoleDescriptor> ds = provider.getAdditionalRoleDescriptors();
                if (ds != null) {
                    descriptors.addAll(ds);
                    log.info("Registered additional roles from {}: {}", provider.getClass().getSimpleName(), ds);
                }
            }
        }

        // Base roles are admin-managed and keep their existing universes; a provider
        // role now chooses its universe rather than landing in both unconditionally.
        List<String> allPossibleRoles = new ArrayList<>(SecConsts.POSSIBLE_ROLES);
        List<String> allCompany1Roles = new ArrayList<>(SecConsts.COMPANY_1_POSSIBLE_ROLES);
        Map<String, EnumsApi.RoleManager> managers = new HashMap<>();

        for (RoleProvider.RoleDescriptor d : descriptors) {
            if (d.scope().regularUniverse) {
                allPossibleRoles.add(d.role());
            }
            if (d.scope().company1Universe) {
                allCompany1Roles.add(d.role());
            }
            if (d.managedBy()!=EnumsApi.RoleManager.admin) {
                managers.put(d.role(), d.managedBy());
            }
        }

        this.possibleRoles = List.copyOf(allPossibleRoles);
        this.company1PossibleRoles = List.copyOf(allCompany1Roles);
        this.roleManagers = Map.copyOf(managers);

        log.info("Total possible roles: {}", this.possibleRoles);
        log.info("Total company-1 possible roles: {}", this.company1PossibleRoles);
        log.info("Mechanism-managed roles: {}", this.roleManagers);
    }

    /** Who may grant this role. Never null — an unlisted role is admin-managed. */
    public EnumsApi.RoleManager getRoleManager(String role) {
        return roleManagers.getOrDefault(role, EnumsApi.RoleManager.admin);
    }

    /**
     * Whether a human administrator may grant or revoke this role.
     *
     * <p>Deliberately SEPARATE from {@link #isValidRole(String)}. A managed role
     * is still a valid, listed role — it must be, or the role-toggle path would
     * silently strip it from every account that legitimately holds it. What
     * changes is who may hand it out.
     */
    public boolean isAssignableByAdmin(String role) {
        return getRoleManager(role)==EnumsApi.RoleManager.admin;
    }

    /**
     * Returns all possible roles for regular companies.
     */
    public List<String> getPossibleRoles() {
        return possibleRoles;
    }

    /**
     * The subset of {@link #getPossibleRoles()} a human administrator may actually grant.
     *
     * <p>Narrower than the universe on purpose, and used only where roles are OFFERED for
     * editing — never where a held role is validated. A mechanism-managed role such as
     * {@code ROLE_RG_ENSEMBLE} remains a fully valid member of the universe, because the
     * toggle path treats an unlisted role as junk; what it must not be is a checkbox, since
     * ticking it can only ever end in a refusal from the manager gate.
     *
     * <p>This is an offer, not a guard. The manager gate in
     * {@code AccountRoleEditUtils#validateToggle} still runs on every commit, so a request
     * that names a managed role directly is still refused rather than merely un-offered.
     */
    public List<String> getAdminAssignableRoles() {
        return possibleRoles.stream().filter(this::isAssignableByAdmin).toList();
    }

    /**
     * Returns all possible roles for company with ID 1 (the management company).
     */
    public List<String> getCompany1PossibleRoles() {
        return company1PossibleRoles;
    }

    /**
     * Checks if a role is valid for regular companies.
     */
    public boolean isValidRole(String role) {
        return possibleRoles.contains(role);
    }

    /**
     * Checks if a role is valid for company 1.
     */
    public boolean isValidCompany1Role(String role) {
        return company1PossibleRoles.contains(role);
    }
}
