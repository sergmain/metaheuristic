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

package ai.metaheuristic.ai.launchpad.account;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.data.AccountData;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.repositories.AccountRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Profile("launchpad")
@Service
public class AccountTopLevelService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Globals globals;

    public AccountTopLevelService(AccountRepository accountRepository, PasswordEncoder passwordEncoder, Globals globals) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.globals = globals;
    }

    public AccountData.AccountsResult getAccounts(Pageable pageable)  {
        pageable = ControllerUtils.fixPageSize(globals.accountRowsLimit, pageable);
        AccountData.AccountsResult result = new AccountData.AccountsResult();
        result.accounts = accountRepository.findAll(pageable);
        return result;
    }

    public OperationStatusRest addAccount(Account account) {
        if (StringUtils.isBlank(account.getUsername()) || StringUtils.isBlank(account.getPassword()) || StringUtils.isBlank(account.getPassword2()) || StringUtils.isBlank(account.getPublicName())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#237.01 Username, password, and public name must be not null");
        }
        if (account.getUsername().indexOf('=')!=-1 ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#237.04 Username can't contain '='");
        }
        if (!account.getPassword().equals(account.getPassword2())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#237.07 Both passwords must be equal");
        }

        if (accountRepository.findByUsername(account.getUsername())!=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    String.format("#237.09 Username '%s' was already used", account.getUsername()));
        }

        account.setPassword(passwordEncoder.encode(account.getPassword()));
        account.setToken(UUID.randomUUID().toString());
        account.setCreatedOn(System.currentTimeMillis());
        account.setRoles("ROLE_USER");
        account.setAccountNonExpired(true);
        account.setAccountNonLocked(true);
        account.setCredentialsNonExpired(true);
        account.setEnabled(true);

        accountRepository.save(account);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public AccountData.AccountResult getAccount(Long id){
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null) {
            return new AccountData.AccountResult("#565.01 account wasn't found, accountId: " + id);
        }
        account.setPassword(null);
        return new AccountData.AccountResult(account);
    }

    public OperationStatusRest editFormCommit(Long accountId, String publicName, boolean enabled) {
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#565.01 account wasn't found, accountId: " + accountId);
        }
        account.setEnabled(enabled);
        account.setPublicName(publicName);
        accountRepository.save(account);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The data of account was changed successfully", null);
    }

    public OperationStatusRest passwordEditFormCommit(Long accountId, String password, String password2) {
        Account a = accountRepository.findById(accountId).orElse(null);
        if (a == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#565.01 account wasn't found, accountId: " + accountId);
        }
        a.setPassword(password);
        a.setPassword2(password2);
        if (StringUtils.isBlank(a.getPassword()) || StringUtils.isBlank(a.getPassword2())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#237.11 Both passwords must be not null");
        }

        if (!a.getPassword().equals(a.getPassword2())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#237.14 Both passwords must be equal");
        }
        a.setPassword(passwordEncoder.encode(a.getPassword()));
        accountRepository.save(a);

        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The password was changed successfully", null);
    }


}
