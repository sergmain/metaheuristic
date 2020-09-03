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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.data.AccountData;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import ai.metaheuristic.ai.sec.SecConsts;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.account.SimpleAccount;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
public class AccountService {

    private final Globals globals;
    private final AccountRepository accountRepository;
    private final AccountCache accountCache;
    private final PasswordEncoder passwordEncoder;

    public AccountData.AccountsResult getAccounts(Pageable pageable, Long companyUniqueId)  {
        AccountData.AccountsResult result = new AccountData.AccountsResult();
        result.accounts = accountRepository.findAllByCompanyUniqueId(pageable, companyUniqueId);
        result.assetMode = globals.assetMode;
        return result;
    }

    public OperationStatusRest addAccount(AccountData.NewAccount acc, Long companyUniqueId, String roles) {

        if (StringUtils.isBlank(acc.getUsername()) ||
                StringUtils.isBlank(acc.getPassword()) ||
                StringUtils.isBlank(acc.getPassword2()) ||
                StringUtils.isBlank(acc.getPublicName())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#237.010 Username, roles, password, and public name must be not null");
        }
        if (acc.getUsername().indexOf('=')!=-1 ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#237.020 Username can't contain '='");
        }
        if (!acc.getPassword().equals(acc.getPassword2())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#237.030 Both passwords must be equal");
        }

        final Account byUsername = accountRepository.findByUsername(acc.getUsername());
        if (byUsername !=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    String.format("#237.040 Username '%s' was already used", acc.getUsername()));
        }

        Account account = new Account();
        account.setRoles(roles);
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

    public AccountData.AccountResult getAccount(Long id, Long companyUniqueId){
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null || !Objects.equals(account.companyId, companyUniqueId)) {
            return new AccountData.AccountResult("#237.050 account wasn't found, accountId: " + id);
        }
        return new AccountData.AccountResult(toSimple(account));
    }

    private static SimpleAccount toSimple(Account acc) {
        return new SimpleAccount(acc.id, acc.companyId, acc.username, acc.publicName, acc.enabled, acc.createdOn, acc.updatedOn, acc.roles);
    }

    public OperationStatusRest editFormCommit(Long accountId, String publicName, boolean enabled, Long companyUniqueId) {
        Account a = accountRepository.findByIdForUpdate(accountId);
        if (a == null || !Objects.equals(a.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#237.060 account wasn't found, accountId: " + accountId);
        }
        a.setEnabled(enabled);
        a.setPublicName(publicName);
        a.updatedOn = System.currentTimeMillis();
        accountCache.save(a);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The data of account was changed successfully", "");
    }

    public OperationStatusRest passwordEditFormCommit(Long accountId, String password, String password2, Long companyUniqueId) {
        if (StringUtils.isBlank(password) || StringUtils.isBlank(password2)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#237.080 Both passwords must be not null");
        }

        if (!password.equals(password2)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#237.090 Both passwords must be equal");
        }
        Account a = accountRepository.findByIdForUpdate(accountId);
        if (a == null || !Objects.equals(a.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#237.100 account wasn't found, accountId: " + accountId);
        }
        a.setPassword(passwordEncoder.encode(password));
        a.updatedOn = System.currentTimeMillis();
        accountCache.save(a);

        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The password was changed successfully", "");
    }

    // this method is using with angular's rest
    public OperationStatusRest roleFormCommit(Long accountId, String roles, Long companyUniqueId) {
        Account account = accountRepository.findByIdForUpdate(accountId);
        if (account == null || !Objects.equals(account.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#237.110 account wasn't found, accountId: " + accountId);
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

    // this method is using with company-accounts
    public OperationStatusRest storeRolesForUserById(Long accountId, String role, boolean checkbox, Long companyUniqueId) {
        Account account = accountRepository.findByIdForUpdate(accountId);
        if (account == null || !Objects.equals(account.companyId, companyUniqueId)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#237.120 account wasn't found, accountId: " + accountId);
        }

        List<String> possibleRoles = Consts.ID_1.equals(companyUniqueId) ? SecConsts.COMPANY_1_ROLES : SecConsts.POSSIBLE_ROLES;
        if (!possibleRoles.contains(role)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#237.130 account wasn't found, accountId: " + accountId);
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

        String roles = String.join(", ", account.accountRoles.getRolesAsList());
        account.setRoles(roles);
        account.updatedOn = System.currentTimeMillis();
        accountCache.save(account);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Roles was changed successfully", "");
    }

}

