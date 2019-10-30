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

package ai.metaheuristic.ai.launchpad.company;

import ai.metaheuristic.ai.launchpad.account.AccountService;
import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.data.AccountData;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class CompanyAccountTopLevelService {

    private final AccountService accountService;

    public AccountData.AccountsResult getAccounts(Pageable pageable, Long companyId)  {
        return accountService.getAccounts(pageable, companyId);
    }

    public OperationStatusRest addAccount(Account account, Long companyId) {
        return accountService.addAccount(account, companyId);
    }

    public AccountData.AccountResult getAccount(Long id, Long companyId){
        return accountService.getAccount(id, companyId);
    }

    public OperationStatusRest editFormCommit(Long accountId, String publicName, boolean enabled, Long companyId) {
        return accountService.editFormCommit(accountId, publicName, enabled, companyId);
    }

    public OperationStatusRest passwordEditFormCommit(Long accountId, String password, String password2, Long companyId) {
        return accountService.passwordEditFormCommit(accountId, password, password2, companyId);
    }

    public OperationStatusRest roleFormCommit(Long accountId, String roles, Long companyId) {
        return accountService.roleFormCommit(accountId, roles, companyId);
    }
}
