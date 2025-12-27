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

/**
 * Interface representing user context with account and company information.
 * This is a common interface that can be implemented by dispatcher-specific context classes.
 *
 * @author Serge
 * Date: 12/26/2024
 */
public interface UserContext {
    
    /**
     * Returns the account ID of the current user.
     *
     * @return account ID
     */
    Long getAccountId();
    
    /**
     * Returns the company unique ID (not the database ID) of the current user's company.
     *
     * @return company unique ID
     */
    Long getCompanyId();
    
    /**
     * Returns the username of the current user.
     *
     * @return username
     */
    String getUsername();
}
