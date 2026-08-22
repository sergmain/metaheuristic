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
import ai.metaheuristic.ai.Consts;
import org.jspecify.annotations.Nullable;
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
    private final List<String> managementCompanyPossibleRoles;

    /**
     * Which mechanism MINTS each role. A role absent from this map is
     * {@link EnumsApi.RoleManager#admin}, which is every base role and every
     * provider role that did not say otherwise.
     *
     * <p>Provenance, not permission. This does not restrict what an administrator may
     * assign — it is read by {@code CommChannelServiceRegistry} so a service declaration
     * cannot name a role some other mechanism owns.
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
        List<String> allManagementCompanyRoles = new ArrayList<>(SecConsts.MANAGEMENT_COMPANY_POSSIBLE_ROLES);
        Map<String, EnumsApi.RoleManager> managers = new HashMap<>();

        for (RoleProvider.RoleDescriptor d : descriptors) {
            if (d.scope().regularUniverse) {
                allPossibleRoles.add(d.role());
            }
            if (d.scope().managementCompanyUniverse) {
                allManagementCompanyRoles.add(d.role());
            }
            if (d.managedBy()!=EnumsApi.RoleManager.admin) {
                managers.put(d.role(), d.managedBy());
            }
        }

        this.possibleRoles = List.copyOf(allPossibleRoles);
        this.managementCompanyPossibleRoles = List.copyOf(allManagementCompanyRoles);
        this.roleManagers = Map.copyOf(managers);

        log.info("Total possible roles: {}", this.possibleRoles);
        log.info("Total management-company possible roles: {}", this.managementCompanyPossibleRoles);
        log.info("Mechanism-managed roles: {}", this.roleManagers);
    }

    /** Who may grant this role. Never null — an unlisted role is admin-managed. */
    public EnumsApi.RoleManager getRoleManager(String role) {
        return roleManagers.getOrDefault(role, EnumsApi.RoleManager.admin);
    }

    /**
     * Returns all possible roles for regular companies.
     */
    public List<String> getPossibleRoles() {
        return possibleRoles;
    }

    /**
     * Returns all possible roles for company with ID 1 (the management company).
     */
    public List<String> getManagementCompanyPossibleRoles() {
        return managementCompanyPossibleRoles;
    }

    /**
     * Every role assignable in the given company — the one universe an administrator of
     * that company both sees and may act on.
     *
     * <p>The split is by {@code EnumsApi.RoleScope}, and that is the ONLY thing narrowing
     * what an administrator may hand out. {@code EnumsApi.RoleManager} is not consulted: it
     * records which mechanism MINTS a role, not who may assign one. So
     * {@code ROLE_RG_ENSEMBLE}, scoped {@code notManagementCompany}, is an ordinary
     * assignable role of every regular company.
     */
    public List<String> rolesOfCompany(@Nullable Long companyUniqueId) {
        return Consts.ID_1.equals(companyUniqueId) ? managementCompanyPossibleRoles : possibleRoles;
    }

    /**
     * Checks if a role is valid for regular companies.
     */
    public boolean isValidRole(String role) {
        return possibleRoles.contains(role);
    }

    /**
     * Checks if a role is valid for the management company.
     */
    public boolean isValidManagementCompanyRole(String role) {
        return managementCompanyPossibleRoles.contains(role);
    }
}
