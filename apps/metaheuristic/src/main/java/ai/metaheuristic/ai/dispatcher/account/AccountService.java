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

package ai.metaheuristic.ai.dispatcher.account;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.AccountRevision;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRevisionRepository;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.account.UserContext;
import ai.metaheuristic.commons.utils.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AccountService {

    private final AccountTxService accountService;
    private final AccountRepository accountRepository;
    private final AccountRevisionRepository accountRevisionRepository;
    private final Globals globals;

    public AccountData.AccountsResult getAccounts(Pageable pageable, UserContext context) {
        pageable = PageUtils.fixPageSize(globals.dispatcher.rowsLimit.account, pageable);
        return accountService.getAccounts(pageable, context.getCompanyId());
    }

    public OperationStatusRest addAccount(AccountData.NewAccount account, Long companyId) {
        // company's admin can create only operator via AccountController
        // a fine-grained access is setting via CompanyController
        return accountService.addAccount(account, companyId, "ROLE_OPERATOR");
    }

    public AccountData.AccountResult getAccount(Long id, UserContext context) {
        return accountService.getAccount(id, context.getCompanyId());
    }

    public OperationStatusRest editFormCommit(@Nullable Long accountId, @Nullable String publicName, boolean enabled, UserContext context) {
        return accountService.editFormCommit(accountId, publicName, enabled, context.getCompanyId());
    }

    public OperationStatusRest passwordEditFormCommit(Long accountId, String password, String password2, UserContext context) {
        return accountService.passwordEditFormCommit(accountId, password, password2, context.getCompanyId());
    }

    public OperationStatusRest roleFormCommit(Long accountId, String roles, UserContext context) {
        return accountService.roleFormCommit(accountId, roles, context.getCompanyId());
    }

    /**
     * Envelope + head AccountRevision joined into an AccountWithRevision.
     * Returns null if envelope not found.
     */
    @Nullable
    public AccountData.AccountWithRevision getCurrent(Long accountId) {
        Account envelope = accountRepository.findById(accountId).orElse(null);
        if (envelope == null) {
            return null;
        }
        return composeFromEnvelope(envelope);
    }

    /**
     * Envelope + head AccountRevision joined into an AccountWithRevision, found by USERNAME.
     * Returns null if envelope not found.
     */
    @Nullable
    public AccountData.AccountWithRevision getCurrentByUsername(String username) {
        Account envelope = accountRepository.findByUsername(username);
        if (envelope == null) {
            return null;
        }
        return composeFromEnvelope(envelope);
    }

    @Nullable
    private AccountData.AccountWithRevision composeFromEnvelope(Account envelope) {
        if (envelope.headRevisionId == null) {
            log.error("Account.id={} has no HEAD_REVISION_ID; envelope is missing its satellite", envelope.id);
            return null;
        }
        AccountRevision head = accountRevisionRepository.findById(envelope.headRevisionId).orElse(null);
        if (head == null) {
            log.error("Account.id={} HEAD_REVISION_ID={} points at a missing AccountRevision row",
                    envelope.id, envelope.headRevisionId);
            return null;
        }
        return new AccountData.AccountWithRevision(
                envelope.id,
                envelope.companyId,
                envelope.username,
                envelope.password,
                envelope.accountNonExpired,
                envelope.accountNonLocked,
                envelope.credentialsNonExpired,
                envelope.enabled,
                envelope.createdOn,
                envelope.roles,
                envelope.deleted,
                envelope.headRevisionId,
                head.publicName,
                head.mailAddress,
                head.phone,
                head.phoneAsStr,
                head.updatedOn,
                head.secretKey,
                head.twoFA,
                head.getParams(),
                head.getAccountParamsYaml()
        );
    }
}

