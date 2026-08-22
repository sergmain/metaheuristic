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
import ai.metaheuristic.ai.sec.RoleService;
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
    private final RoleService roleService;
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
     * The account plus the roles an ADMIN of the CALLER's company may hand out.
     *
     * <p>The universe of the CALLER's own company — the management-company list when the
     * caller belongs to the management company, the regular list otherwise — and nothing
     * further removed from it. An administrator administers their company: every account in
     * it, and every role that company's universe contains, including a role a mechanism
     * would normally mint such as {@code ROLE_RG_ENSEMBLE}.
     *
     * <p>Same rule the MAIN_ADMIN page applies, differing only in WHICH company is resolved:
     * there it comes from the path, here from the authentication principal, which is what
     * confines an ADMIN to one company.
     */
    public AccountData.AccountWithRoleResult getAccountWithRole(Long accountId, UserContext context) {
        AccountData.AccountResult account = accountService.getAccount(accountId, context.getCompanyId());
        return new AccountData.AccountWithRoleResult(
                account.account, roleService.rolesOfCompany(context.getCompanyId()), account.getErrorMessages());
    }

    /**
     * Toggle one role on an account of the CALLER's own company. The company comes from the
     * authentication principal via {@code context}, never from the request.
     */
    public OperationStatusRest storeRoleForUserById(Long accountId, String role, boolean checkbox, UserContext context) {
        return accountService.storeRoleForUserByIdWithinCompany(accountId, role, checkbox, context.getCompanyId());
    }

    /**
     * Envelope + head AccountRevision joined into an AccountWithRevision.
     * Returns null if envelope not found.
     */
    public AccountData.@Nullable AccountWithRevision getCurrent(Long accountId) {
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
    public AccountData.@Nullable AccountWithRevision getCurrentByUsername(String username) {
        Account envelope = accountRepository.findByUsername(username);
        if (envelope == null) {
            return null;
        }
        return composeFromEnvelope(envelope);
    }

    private AccountData.@Nullable AccountWithRevision composeFromEnvelope(Account envelope) {
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

