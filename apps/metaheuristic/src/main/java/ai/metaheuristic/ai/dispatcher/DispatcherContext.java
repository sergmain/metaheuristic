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

package ai.metaheuristic.ai.dispatcher;

import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

import java.util.UUID;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 11:37 PM
 */
@Data
@AllArgsConstructor
public class DispatcherContext {
    public final String contextId = UUID.randomUUID().toString();

    @NonNull
    public final Account account;

    @NonNull
    private final Company company;

    public String getUsername() {
        return account.username;
    }
    public Long getAccountId() {
        return account.id;
    }
    public Long getCompanyId() {
        return company.uniqueId;
    }

    public ExecContextData.UserExecContext asUserExecContext() {
        return new ExecContextData.UserExecContext(getAccountId(), getCompanyId());
    }
}
