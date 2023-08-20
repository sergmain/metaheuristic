/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.company;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.account.AccountTxService;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.sec.SecConsts;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class CompanyAccountTopLevelService {

    private final AccountTxService accountService;

    public AccountData.AccountsResult getAccounts(Pageable pageable, Long companyUniqueId)  {
        pageable = PageUtils.fixPageSize(50, pageable);
        return accountService.getAccounts(pageable, companyUniqueId);
    }

    public OperationStatusRest addAccount(AccountData.NewAccount account, Long companyUniqueId) {
        // don't set any role when account is created
        return accountService.addAccount(account, companyUniqueId, "");
    }

    public AccountData.AccountResult getAccount(Long id, Long companyUniqueId){
        return accountService.getAccount(id, companyUniqueId);
    }

    public AccountData.AccountWithRoleResult getAccountWithRole(Long id, Long companyUniqueId){
        AccountData.AccountResult account = accountService.getAccount(id, companyUniqueId);
        return new AccountData.AccountWithRoleResult(
                account.account,
                Consts.ID_1.equals(companyUniqueId) ? SecConsts.COMPANY_1_POSSIBLE_ROLES : SecConsts.POSSIBLE_ROLES, account.getErrorMessages());
    }

    public OperationStatusRest editFormCommit(@Nullable Long accountId, @Nullable String publicName, boolean enabled, @Nullable Long companyUniqueId) {
        return accountService.editFormCommit(accountId, publicName, enabled, companyUniqueId);
    }

    public OperationStatusRest passwordEditFormCommit(Long accountId, String password, String password2, Long companyUniqueId) {
        return accountService.passwordEditFormCommit(accountId, password, password2, companyUniqueId);
    }

    public OperationStatusRest storeRolesForUserById(Long accountId, String role, boolean checkbox, Long companyUniqueId) {
        return accountService.storeRolesForUserById(accountId, role, checkbox, companyUniqueId);
    }


}
