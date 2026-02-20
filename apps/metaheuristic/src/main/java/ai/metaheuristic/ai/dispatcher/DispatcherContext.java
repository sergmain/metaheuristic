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

package ai.metaheuristic.ai.dispatcher;

import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.commons.account.UserContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 11:37 PM
 */
@Data
@AllArgsConstructor
public class DispatcherContext implements UserContext {

    @NonNull
    public final Account account;

    @NonNull
    private final Company company;

    @Override
    public String getUsername() {
        return account.username;
    }

    @Override
    public Long getAccountId() {
        return account.id;
    }

    @Override
    public Long getCompanyId() {
        return company.uniqueId;
    }

    public ExecContextApiData.UserExecContext asUserExecContext() {
        return new ExecContextApiData.UserExecContext(getAccountId(), getCompanyId());
    }
}
