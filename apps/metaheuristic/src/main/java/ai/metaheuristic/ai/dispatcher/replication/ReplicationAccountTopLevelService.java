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

package ai.metaheuristic.ai.dispatcher.replication;

import ai.metaheuristic.ai.dispatcher.account.AccountCache;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.dispatcher.repositories.AccountRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author Serge
 * Date: 11/24/2020
 * Time: 6:57 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ReplicationAccountTopLevelService {

    private final ReplicationCoreService replicationCoreService;
    private final ReplicationAccountService replicationAccountService;
    private final AccountRepository accountRepository;
    private final AccountCache accountCache;

    @Data
    @AllArgsConstructor
    public static class AccountLoopEntry {
        public ReplicationData.AccountShortAsset accountShort;
        public Account account;
    }

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

        for (AccountLoopEntry accountLoopEntry : forUpdating) {
            ReplicationData.AccountAsset accountAsset = getAccountAsset(accountLoopEntry.accountShort.username);
            if (accountAsset==null) {
                continue;
            }
            replicationAccountService.updateAccount(accountLoopEntry, accountAsset);
        }
        forCreating.stream()
                .map(o-> getAccountAsset(o.username))
                .filter(Objects::nonNull)
                .forEach(replicationAccountService::createAccount);
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
                "/rest/v1/replication/account", ReplicationData.AccountAsset.class, List.of(new BasicNameValuePair("username", username)),
                (uri) -> Request.get(uri)
                        .connectTimeout(Timeout.ofSeconds(5))
                        //.socketTimeout(20000)
        );
        if (data instanceof ReplicationData.AssetAcquiringError) {
            return new ReplicationData.AccountAsset(((ReplicationData.AssetAcquiringError) data).getErrorMessagesAsList());
        }
        ReplicationData.AccountAsset response = (ReplicationData.AccountAsset) data;
        return response;
    }

}
