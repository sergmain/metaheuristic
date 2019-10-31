/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad;

import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.beans.Company;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.springframework.security.core.Authentication;

import java.util.UUID;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 11:37 PM
 */
@Data
@AllArgsConstructor
public class LaunchpadContext {
    public final String contextId = UUID.randomUUID().toString();

    @NonNull
    public final Account account;

    @NonNull
    public final Company company;

    public String getUsername() {
        return account.username;
    }
    public Long getAccountId() {
        return account.id;
    }
    public Long getCompanyId() {
        return account.companyId;
    }
}
