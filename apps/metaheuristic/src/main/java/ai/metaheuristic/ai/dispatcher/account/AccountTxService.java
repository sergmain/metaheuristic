/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.sec.SecConsts;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.account.SimpleAccount;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 10/30/2019
 * Time: 1:21 AM
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor
public class AccountTxService {

    private final Globals globals;
    private final AccountRepository accountRepository;
    private final AccountCache accountCache;
    private final PasswordEncoder passwordEncoder;

    @Nullable
    @Transactional(readOnly = true)
    public Account findByUsername(String username) {
        return accountCache.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public AccountData.AccountsResult getAccounts(Pageable pageable, Long companyUniqueId)  {
        AccountData.AccountsResult result = new AccountData.AccountsResult();
        result.accounts = accountRepository.findAllByCompanyUniqueId(pageable, companyUniqueId);
        result.assetMode = globals.dispatcher.asset.mode;
        return result;
    }

    @Transactional
    public OperationStatusRest addAccount(AccountData.NewAccount acc, Long companyUniqueId, String roles) {

        if (StringUtils.isBlank(acc.getUsername()) ||
                StringUtils.isBlank(acc.getPassword()) ||
                StringUtils.isBlank(acc.getPassword2()) ||
                StringUtils.isBlank(acc.getPublicName())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#235.010 Username, roles, password, and public name must be not null");
        }
        if (acc.getUsername().indexOf('=')!=-1 ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#235.020 Username can't contain '='");
        }
        if (!acc.getPassword().equals(acc.getPassword2())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#235.030 Both passwords must be equal");
        }

        final Account byUsername = accountRepository.findByUsername(acc.getUsername());
        if (byUsername !=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    String.format("#235.040 Username '%s' was already registered", acc.getUsername()));
        }

        Account account = new Account();
        account.username = acc.username;
        account.password = acc.password;
        account.publicName = acc.publicName;
        account.roles = roles;

        account.setPassword(passwordEncoder.encode(account.getPassword()));
        account.setCreatedOn(System.currentTimeMillis());
        account.setUpdatedOn(account.createdOn);
        account.setAccountNonExpired(true);
        account.setAccountNonLocked(true);
        account.setCredentialsNonExpired(true);
        account.setEnabled(true);
        account.setCompanyId(companyUniqueId);

        accountCache.save(account);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional(readOnly = true)
    public AccountData.AccountResult getAccount(Long id, Long companyUniqueId){
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null || !Objects.equals(account.companyId, companyUniqueId)) {
            return new AccountData.AccountResult("#235.050 account wasn't found, accountId: " + id);
        }
        return new AccountData.AccountResult(toSimple(account));
    }

    private static SimpleAccount toSimple(Account acc) {
        return new SimpleAccount(acc.id, acc.companyId, acc.username, acc.publicName, acc.enabled, acc.createdOn, acc.updatedOn, acc.accountRoles.asString());
    }

    @Transactional
    public OperationStatusRest editFormCommit(@Nullable Long accountId, @Nullable String publicName, boolean enabled, @Nullable Long companyUniqueId) {
        if (accountId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#235.055 System error, accountId is null");
        }
        if (S.b(publicName)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#235.056 System error, publicName is blank");
        }
        if (companyUniqueId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#235.057 System error, companyUniqueId is null");
        }
        Account a = accountRepository.findByIdForUpdate(accountId);
        if (a == null || !Objects.equals(a.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#235.060 account wasn't found, accountId: " + accountId);
        }
        a.setEnabled(enabled);
        a.setPublicName(publicName);
        a.updatedOn = System.currentTimeMillis();
        accountCache.save(a);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The data of account was changed successfully", "");
    }

    @Transactional
    public OperationStatusRest passwordEditFormCommit(Long accountId, String password, String password2, Long companyUniqueId) {
        if (StringUtils.isBlank(password) || StringUtils.isBlank(password2)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#235.080 Both passwords must be not null");
        }

        if (!password.equals(password2)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#235.090 Both passwords must be equal");
        }
        Account a = accountRepository.findByIdForUpdate(accountId);
        if (a == null || !Objects.equals(a.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#235.100 account wasn't found, accountId: " + accountId);
        }
        a.setPassword(passwordEncoder.encode(password));
        a.updatedOn = System.currentTimeMillis();
        accountCache.save(a);

        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The password was changed successfully", "");
    }

    // this method is using with angular's rest
    @Transactional
    public OperationStatusRest roleFormCommit(Long accountId, String roles, Long companyUniqueId) {
        Account account = accountRepository.findByIdForUpdate(accountId);
        if (account == null || !Objects.equals(account.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#235.110 account wasn't found, accountId: " + accountId);
        }
        String str = Arrays.stream(StringUtils.split(roles, ','))
                .map(String::strip)
                .filter(SecConsts.POSSIBLE_ROLES::contains)
                .collect(Collectors.joining(", "));

        account.setRoles(str);
        account.updatedOn = System.currentTimeMillis();
        accountCache.save(account);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The data of account was changed successfully", "");
    }

    // this method is for using with company-accounts
    @Transactional
    public OperationStatusRest storeRolesForUserById(Long accountId, String role, boolean checkbox, Long companyUniqueId) {
        Account account = accountRepository.findByIdForUpdate(accountId);
        if (account == null || !Objects.equals(account.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#235.120 account wasn't found, accountId: " + accountId);
        }

        List<String> possibleRoles = Consts.ID_1.equals(companyUniqueId) ? SecConsts.COMPANY_1_POSSIBLE_ROLES : SecConsts.POSSIBLE_ROLES;
        if (!possibleRoles.contains(role)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#235.130 account wasn't found, accountId: " + accountId);
        }

        List<String> currRoles = account.accountRoles.getRolesAsList();
        for (String currRole : currRoles) {
            if (!possibleRoles.contains(currRole)) {
                account.accountRoles.removeRole(currRole);
            }
        }

        boolean isAccountContainsRole = account.accountRoles.hasRole(role);
        if (isAccountContainsRole && !checkbox){
            account.accountRoles.removeRole(role);
        } else if (!isAccountContainsRole && checkbox) {
            account.accountRoles.addRole(role);
        }

        if (!Consts.ID_1.equals(account.getCompanyId())) {
            account.accountRoles.removeRole(SecConsts.ROLE_SERVER_REST_ACCESS);
        }

        account.roles = account.accountRoles.asString();
        account.updatedOn = System.currentTimeMillis();
        accountCache.save(account);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Role "+role+" was changed successfully", "");
    }

}

