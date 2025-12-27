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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static ai.metaheuristic.ai.dispatcher.replication.ReplicationAccountTopLevelService.AccountLoopEntry;

/**
 * @author Serge
 * Date: 1/13/2020
 * Time: 7:10 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ReplicationAccountService {

    private final AccountRepository accountRepository;
    private final AccountCache accountCache;

    @Transactional
    public void updateAccount(AccountLoopEntry accountLoopEntry, ReplicationData.AccountAsset accountAsset) {

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
        accountLoopEntry.account.roles = accountAsset.account.accountRoles.asString();
        accountLoopEntry.account.secretKey = accountAsset.account.secretKey;
        accountLoopEntry.account.twoFA = accountAsset.account.twoFA;

        accountCache.save(accountLoopEntry.account);
    }

    @Transactional
    public void createAccount(ReplicationData.AccountAsset accountAsset) {
        Account a = accountRepository.findByUsername(accountAsset.account.username);
        if (a!=null) {
            return;
        }

        //noinspection ConstantConditions
        accountAsset.account.id=null;
        //noinspection ConstantConditions
        accountAsset.account.version=null;
        accountCache.save(accountAsset.account);
    }
}