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

package ai.metaheuristic.ai.dispatcher.account;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class AccountTopLevelService {

    private final AccountTxService accountService;
    private final Globals globals;

    public AccountData.AccountsResult getAccounts(Pageable pageable, DispatcherContext context) {
        pageable = PageUtils.fixPageSize(globals.dispatcher.rowsLimit.account, pageable);
        return accountService.getAccounts(pageable, context.getCompanyId());
    }

    public OperationStatusRest addAccount(AccountData.NewAccount account, Long companyId) {
        // company's admin can create only operator via AccountController
        // a fine-grained access is setting via CompanyController
        return accountService.addAccount(account, companyId, "ROLE_OPERATOR");
    }

    public AccountData.AccountResult getAccount(Long id, DispatcherContext context) {
        return accountService.getAccount(id, context.getCompanyId());
    }

    public OperationStatusRest editFormCommit(@Nullable Long accountId, @Nullable String publicName, boolean enabled, DispatcherContext context) {
        return accountService.editFormCommit(accountId, publicName, enabled, context.getCompanyId());
    }

    public OperationStatusRest passwordEditFormCommit(Long accountId, String password, String password2, DispatcherContext context) {
        return accountService.passwordEditFormCommit(accountId, password, password2, context.getCompanyId());
    }

    public OperationStatusRest roleFormCommit(Long accountId, String roles, DispatcherContext context) {
        return accountService.roleFormCommit(accountId, roles, context.getCompanyId());
    }
}
