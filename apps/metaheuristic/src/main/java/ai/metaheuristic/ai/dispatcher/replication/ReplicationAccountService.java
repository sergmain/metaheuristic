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

package ai.metaheuristic.ai.dispatcher.replication;

import ai.metaheuristic.ai.dispatcher.account.AccountCache;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Serge
 * Date: 1/13/2020
 * Time: 7:10 PM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("dispatcher")
public class ReplicationAccountService {

    public final ReplicationCoreService replicationCoreService;
    public final AccountRepository accountRepository;
    public final AccountCache accountCache;

    @Data
    @AllArgsConstructor
    private static class AccountLoopEntry {
        public ReplicationData.AccountShortAsset accountShort;
        public Account account;
    }

    @Transactional
    public void syncAccounts(List<ReplicationData.AccountShortAsset> actualAccounts) {
        List<AccountLoopEntry> forUpdating = new ArrayList<>(actualAccounts.size());
        LinkedList<ReplicationData.AccountShortAsset> forCreating = new LinkedList<>(actualAccounts);

        List<String> usernames = accountRepository.findAllUsernames();
        for (String username : usernames) {
            Account a = accountCache.findByUsername(username);
            if (a==null) {
                continue;
            }

            boolean isDeleted = true;
            for (ReplicationData.AccountShortAsset actualAccount : actualAccounts) {
                if (actualAccount.username.equals(a.username)) {
                    isDeleted = false;
                    if (actualAccount.updateOn != a.updatedOn) {
                        AccountLoopEntry accountLoopEntry = new AccountLoopEntry(actualAccount, a);
                        forUpdating.add(accountLoopEntry);
                    }
                    break;
                }
            }

            if (isDeleted) {
                log.warn("!!! Strange situation - account wasn't found, username: {}", username);
            }
            forCreating.removeIf(accountShortAsset -> accountShortAsset.username.equals(a.username));
        }

        forUpdating.forEach(this::updateAccount);
        forCreating.forEach(this::createAccount);
    }

    private void updateAccount(AccountLoopEntry accountLoopEntry) {
        ReplicationData.AccountAsset accountAsset = getAccountAsset(accountLoopEntry.account.username);
        if (accountAsset == null) {
            return;
        }

        accountLoopEntry.account.companyId = accountAsset.account.companyId;
        accountLoopEntry.account.username = accountAsset.account.username;
        accountLoopEntry.account.password = accountAsset.account.password;
        accountLoopEntry.account.accountNonExpired = accountAsset.account.accountNonExpired;
        accountLoopEntry.account.accountNonLocked = accountAsset.account.accountNonLocked;
        accountLoopEntry.account.credentialsNonExpired = accountAsset.account.credentialsNonExpired;
        accountLoopEntry.account.enabled = accountAsset.account.enabled;
        accountLoopEntry.account.publicName = accountAsset.account.publicName;
        accountLoopEntry.account.mailAddress = accountAsset.account.mailAddress;
        accountLoopEntry.account.phone = accountAsset.account.phone;
        accountLoopEntry.account.createdOn = accountAsset.account.createdOn;
        accountLoopEntry.account.updatedOn = accountAsset.account.updatedOn;
        accountLoopEntry.account.roles = accountAsset.account.roles;
        accountLoopEntry.account.secretKey = accountAsset.account.secretKey;
        accountLoopEntry.account.twoFA = accountAsset.account.twoFA;

        accountCache.save(accountLoopEntry.account);
    }

    private void createAccount(ReplicationData.AccountShortAsset accountShortAsset) {
        ReplicationData.AccountAsset accountAsset = getAccountAsset(accountShortAsset.username);
        if (accountAsset == null) {
            return;
        }

        Account a = accountRepository.findByUsername(accountShortAsset.username);
        if (a!=null) {
            return;
        }

        //noinspection ConstantConditions
        accountAsset.account.id=null;
        //noinspection ConstantConditions
        accountAsset.account.version=null;
        accountCache.save(accountAsset.account);
    }

    @Nullable
    private ReplicationData.AccountAsset getAccountAsset(String username) {
        ReplicationData.AccountAsset accountAsset = requestAccountAsset(username);
        if (accountAsset.isErrorMessages()) {
            log.error("#308.020 Error while getting account for username "+ username +", error: " + accountAsset.getErrorMessagesAsStr());
            return null;
        }
        return accountAsset;
    }

    private ReplicationData.AccountAsset requestAccountAsset(String username) {
        Object data = replicationCoreService.getData(
                "/rest/v1/replication/account", ReplicationData.AccountAsset.class,
                (uri) -> Request.Post(uri)
                        .bodyForm(Form.form().add("username", username).build(), StandardCharsets.UTF_8)
                        .connectTimeout(5000)
                        .socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.AccountAsset(((ReplicationData.AssetAcquiringError) data).getErrorMessagesAsList());
        }
        ReplicationData.AccountAsset response = (ReplicationData.AccountAsset) data;
        return response;
    }

}