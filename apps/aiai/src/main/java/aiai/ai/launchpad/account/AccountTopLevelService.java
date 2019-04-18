/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.account;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Account;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.data.AccountData;
import aiai.ai.launchpad.data.FlowData;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.repositories.AccountRepository;
import aiai.ai.utils.ControllerUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("Duplicates")
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
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#237.01 Username, password, and public name must be not null");
        }
        if (account.getUsername().indexOf('=')!=-1 ) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#237.04 Username can't contain '='");
        }
        if (!account.getPassword().equals(account.getPassword2())) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#237.07 Both passwords must be equal");
        }

        if (accountRepository.findByUsername(account.getUsername())!=null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
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

    public AccountData.AccountResult editFormCommit(Account acc) {
        Account account = accountRepository.findById(acc.id).orElse(null);
        if (account == null) {
            return new AccountData.AccountResult("#565.01 account wasn't found, accountId: " + acc.id);
        }
        account.setEnabled(account.isEnabled());
        account.setPublicName(account.getPublicName());
        accountRepository.save(account);
        return new AccountData.AccountResult(account);
    }

    public AccountData.AccountResult passwordEditFormCommit(Account account) {
        Account a = accountRepository.findById(account.id).orElse(null);
        if (a == null) {
            return new AccountData.AccountResult("#565.01 account wasn't found, accountId: " + account.id);
        }
        if (StringUtils.isBlank(account.getPassword()) || StringUtils.isBlank(account.getPassword2())) {
            return new AccountData.AccountResult("#237.11 Both passwords must be not null");
        }

        if (!account.getPassword().equals(account.getPassword2())) {
            return new AccountData.AccountResult("#237.14 Both passwords must be equal");
        }
        a.setPassword(passwordEncoder.encode(account.getPassword()));
        accountRepository.save(a);

        return new AccountData.AccountResult(a);
    }


}
