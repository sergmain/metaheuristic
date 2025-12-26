/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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
}
