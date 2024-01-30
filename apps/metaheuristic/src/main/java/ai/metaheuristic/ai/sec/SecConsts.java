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

package ai.metaheuristic.ai.sec;

import java.util.List;

/**
 * @author Serge
 * Date: 10/30/2019
 * Time: 5:18 PM
 */
public class SecConsts {
    public static final String ROLE_SERVER_REST_ACCESS = "ROLE_SERVER_REST_ACCESS";
    public static final String ROLE_ASSET_REST_ACCESS = "ROLE_ASSET_REST_ACCESS";
    public static final String ROLE_BILLING = "ROLE_BILLING";

    public static final String ROLE_MAIN_ADMIN = "ROLE_MAIN_ADMIN";
    public static final String ROLE_MAIN_OPERATOR = "ROLE_MAIN_OPERATOR";
    public static final String ROLE_MAIN_SUPPORT = "ROLE_MAIN_SUPPORT";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    public static final List<String> POSSIBLE_ROLES = List.of(ROLE_ADMIN,"ROLE_MANAGER","ROLE_OPERATOR", "ROLE_DATA");
    public static final List<String> COMPANY_1_POSSIBLE_ROLES =
            List.of(ROLE_SERVER_REST_ACCESS, ROLE_ASSET_REST_ACCESS, ROLE_MAIN_OPERATOR,
                    ROLE_MAIN_SUPPORT, ROLE_BILLING, "ROLE_MAIN_ASSET_MANAGER");
}
