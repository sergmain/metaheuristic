/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.account;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.LaunchpadContext;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class AccountTopLevelService {

    private final AccountService accountService;
    private final Globals globals;

    public AccountData.AccountsResult getAccounts(Pageable pageable, LaunchpadContext context) {
        pageable = ControllerUtils.fixPageSize(globals.accountRowsLimit, pageable);
        return accountService.getAccounts(pageable, context.getCompanyId());
    }

    public OperationStatusRest addAccount(Account account, LaunchpadContext context) {
        // company's admin can create only operator via AccountController
        // a fine-grained access is setting via CompanyController
        account.setRoles("ROLE_OPERATOR");
        return accountService.addAccount(account, context.getCompanyId());
    }

    public AccountData.AccountResult getAccount(Long id, LaunchpadContext context) {
        return accountService.getAccount(id, context.getCompanyId());
    }

    public OperationStatusRest editFormCommit(Long accountId, String publicName, boolean enabled, LaunchpadContext context) {
        return accountService.editFormCommit(accountId, publicName, enabled, context.getCompanyId());
    }

    public OperationStatusRest passwordEditFormCommit(Long accountId, String password, String password2, LaunchpadContext context) {
        return accountService.passwordEditFormCommit(accountId, password, password2, context.getCompanyId());
    }

    public OperationStatusRest roleFormCommit(Long accountId, String roles, LaunchpadContext context) {
        return accountService.roleFormCommit(accountId, roles, context.getCompanyId());
    }
}
