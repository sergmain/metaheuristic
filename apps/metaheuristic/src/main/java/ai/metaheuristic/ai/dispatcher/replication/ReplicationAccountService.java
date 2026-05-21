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
import ai.metaheuristic.ai.dispatcher.account.AccountRevisionWriter;
import ai.metaheuristic.ai.dispatcher.beans.Account;
import ai.metaheuristic.ai.dispatcher.beans.AccountRevision;
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
    private final AccountRevisionWriter accountRevisionWriter;

    @Transactional
    public void updateAccount(AccountLoopEntry accountLoopEntry, ReplicationData.AccountAsset accountAsset) {
        if (accountAsset.headRevision == null) {
            log.error("Asset for username={} is missing headRevision; cannot update",
                    accountLoopEntry.account.username);
            return;
        }

        // Mutate the envelope-resident Spring-Security primitives + ROLES directly.
        // USERNAME, COMPANY_ID, CREATED_ON are identity and intentionally never change.
        Account envelope = accountLoopEntry.account;
        envelope.password = accountAsset.account.password;
        envelope.accountNonExpired = accountAsset.account.accountNonExpired;
        envelope.accountNonLocked = accountAsset.account.accountNonLocked;
        envelope.credentialsNonExpired = accountAsset.account.credentialsNonExpired;
        envelope.enabled = accountAsset.account.enabled;
        envelope.roles = accountAsset.account.accountRoles.asString();
        accountCache.save(envelope);

        // Profile/audit scalars get a new satellite revision carrying the upstream values.
        AccountRevision incoming = accountAsset.headRevision;
        AccountRevisionWriter.ProfilePayload payload = new AccountRevisionWriter.ProfilePayload(
                incoming.publicName,
                incoming.mailAddress,
                incoming.phone,
                incoming.phoneAsStr,
                incoming.secretKey,
                incoming.twoFA,
                incoming.getParams()
        );
        accountRevisionWriter.writeNewRevision(envelope.id, payload);
    }

    @Transactional
    public void createAccount(ReplicationData.AccountAsset accountAsset) {
        Account a = accountRepository.findByUsername(accountAsset.account.username);
        if (a != null) {
            return;
        }
        if (accountAsset.headRevision == null) {
            log.error("Asset for username={} is missing headRevision; cannot replicate",
                    accountAsset.account.username);
            return;
        }

        // Envelope draft — copy identity + security primitives from the upstream snapshot.
        Account envelopeDraft = new Account();
        envelopeDraft.companyId = accountAsset.account.companyId;
        envelopeDraft.username = accountAsset.account.username;
        envelopeDraft.password = accountAsset.account.password;
        envelopeDraft.accountNonExpired = accountAsset.account.accountNonExpired;
        envelopeDraft.accountNonLocked = accountAsset.account.accountNonLocked;
        envelopeDraft.credentialsNonExpired = accountAsset.account.credentialsNonExpired;
        envelopeDraft.enabled = accountAsset.account.enabled;
        envelopeDraft.createdOn = accountAsset.account.createdOn;
        envelopeDraft.roles = accountAsset.account.accountRoles.asString();

        AccountRevision incoming = accountAsset.headRevision;
        AccountRevisionWriter.ProfilePayload profile = new AccountRevisionWriter.ProfilePayload(
                incoming.publicName,
                incoming.mailAddress,
                incoming.phone,
                incoming.phoneAsStr,
                incoming.secretKey,
                incoming.twoFA,
                incoming.getParams()
        );
        accountRevisionWriter.create(envelopeDraft, profile);
    }
}
