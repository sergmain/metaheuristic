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

import ai.metaheuristic.commons.account.RoleProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    public RoleService(@Autowired(required = false) List<RoleProvider> roleProviders) {
        List<String> additionalRoles = new ArrayList<>();
        if (roleProviders != null) {
            for (RoleProvider provider : roleProviders) {
                List<String> roles = provider.getAdditionalRoles();
                if (roles != null) {
                    additionalRoles.addAll(roles);
                    log.info("Registered additional roles from {}: {}", provider.getClass().getSimpleName(), roles);
                }
            }
        }

        // Build immutable lists combining base roles with additional roles
        List<String> allPossibleRoles = new ArrayList<>(SecConsts.POSSIBLE_ROLES);
        allPossibleRoles.addAll(additionalRoles);
        this.possibleRoles = List.copyOf(allPossibleRoles);

        List<String> allCompany1Roles = new ArrayList<>(SecConsts.COMPANY_1_POSSIBLE_ROLES);
        allCompany1Roles.addAll(additionalRoles);
        this.company1PossibleRoles = List.copyOf(allCompany1Roles);

        log.info("Total possible roles: {}", this.possibleRoles);
        log.info("Total company-1 possible roles: {}", this.company1PossibleRoles);
    }

    /**
     * Returns all possible roles for regular companies.
     */
    public List<String> getPossibleRoles() {
        return possibleRoles;
    }

    /**
     * Returns all possible roles for company with ID 1 (master company).
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
