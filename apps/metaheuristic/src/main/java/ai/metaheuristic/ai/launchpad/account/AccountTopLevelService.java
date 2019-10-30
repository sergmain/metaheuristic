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
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.data.AccountData;
import ai.metaheuristic.ai.launchpad.repositories.AccountRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class AccountTopLevelService {

    private static final Set<String> POSSIBLE_ROLES = Set.of("ROLE_SERVER_REST_ACCESS", "ROLE_ADMIN","ROLE_MANAGER","ROLE_OPERATOR","ROLE_BILLING","ROLE_DATA");

    private final AccountRepository accountRepository;
    private final AccountCache accountCache;
    private final PasswordEncoder passwordEncoder;
    private final Globals globals;

    public AccountData.AccountsResult getAccounts(Pageable pageable, LaunchpadContext context)  {
        pageable = ControllerUtils.fixPageSize(globals.accountRowsLimit, pageable);
        AccountData.AccountsResult result = new AccountData.AccountsResult();
        result.accounts = accountRepository.findAll(pageable, context.getCompanyId());
        return result;
    }

    public OperationStatusRest addAccount(Account account, LaunchpadContext context) {
        if (StringUtils.isBlank(account.getUsername()) || StringUtils.isBlank(account.getPassword()) || StringUtils.isBlank(account.getPassword2()) || StringUtils.isBlank(account.getPublicName())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#237.010 Username, password, and public name must be not null");
        }
        if (account.getUsername().indexOf('=')!=-1 ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#237.020 Username can't contain '='");
        }
        if (!account.getPassword().equals(account.getPassword2())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#237.030 Both passwords must be equal");
        }

        final Account byUsername = accountRepository.findByUsername(account.getUsername());
        if (byUsername !=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    String.format("#237.040 Username '%s' was already used", account.getUsername()));
        }

        account.setCompanyId(context.getCompanyId());
        account.setPassword(passwordEncoder.encode(account.getPassword()));
        account.setCreatedOn(System.currentTimeMillis());
        account.setRoles("ROLE_OPERATOR");
        account.setAccountNonExpired(true);
        account.setAccountNonLocked(true);
        account.setCredentialsNonExpired(true);
        account.setEnabled(true);

        accountCache.save(account);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public AccountData.AccountResult getAccount(Long id, LaunchpadContext context){
        Account account = accountRepository.findById(id).orElse(null);
        if (account == null || !Objects.equals(account.companyId, context.getCompanyId())) {
            return new AccountData.AccountResult("#237.050 account wasn't found, accountId: " + id);
        }
        account.setPassword(null);
        return new AccountData.AccountResult(account);
    }

    public OperationStatusRest editFormCommit(Long accountId, String publicName, boolean enabled, LaunchpadContext context) {
        Account a = accountRepository.findByIdForUpdate(accountId);
        if (a == null || !Objects.equals(a.companyId, context.getCompanyId())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#237.060 account wasn't found, accountId: " + accountId);
        }
        a.setEnabled(enabled);
        a.setPublicName(publicName);
        accountCache.save(a);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The data of account was changed successfully", null);
    }

    public OperationStatusRest passwordEditFormCommit(Long accountId, String password, String password2, LaunchpadContext context) {
        if (StringUtils.isBlank(password) || StringUtils.isBlank(password2)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#237.080 Both passwords must be not null");
        }

        if (!password.equals(password2)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#237.090 Both passwords must be equal");
        }
        Account a = accountRepository.findByIdForUpdate(accountId);
        if (a == null || !Objects.equals(a.companyId, context.getCompanyId())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#237.100 account wasn't found, accountId: " + accountId);
        }
        a.setPassword(passwordEncoder.encode(password));
        accountCache.save(a);

        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The password was changed successfully", null);
    }


    public OperationStatusRest roleFormCommit(Long accountId, String roles, LaunchpadContext context) {
        Account account = accountRepository.findByIdForUpdate(accountId);
        if (account == null || !Objects.equals(account.companyId, context.getCompanyId())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#237.110 account wasn't found, accountId: " + accountId);
        }
        String str = Arrays.stream(StringUtils.split(roles, ','))
                .map(String::strip)
                .filter(POSSIBLE_ROLES::contains)
                .collect(Collectors.joining(", "));

        account.setRoles(str);
        accountRepository.save(account);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK,"The data of account was changed successfully", null);
    }
}
