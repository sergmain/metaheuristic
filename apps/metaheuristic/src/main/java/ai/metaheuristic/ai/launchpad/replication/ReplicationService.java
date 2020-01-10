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

package ai.metaheuristic.ai.launchpad.replication;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.PlanImpl;
import ai.metaheuristic.ai.launchpad.data.ReplicationData;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.repositories.AccountRepository;
import ai.metaheuristic.ai.launchpad.repositories.CompanyRepository;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 1/9/2020
 * Time: 12:16 AM
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("launchpad")
public class ReplicationService {

    public final Globals globals;
    public final CompanyRepository companyRepository;
    public final AccountRepository accountRepository;
    public final PlanRepository planRepository;
    public final SnippetRepository snippetRepository;
    public final PlanCache planCache;

    public void sync() {
        if (globals.assetMode!= EnumsApi.LaunchpadAssetMode.replicated) {
            return;
        }
        ReplicationData.AssetStateResponse assetStateResponse = getAssetState();
        syncCompanies(assetStateResponse);
        syncAccounts(assetStateResponse);
        syncSnippets(assetStateResponse);
        syncPLans(assetStateResponse);
    }

    private void syncPLans(ReplicationData.AssetStateResponse assetStateResponse) {
        if (plansInSyncState(assetStateResponse)) {
            return;
        }
    }

    private boolean plansInSyncState(ReplicationData.AssetStateResponse assetStateResponse) {
        return false;
    }

    private void syncSnippets(ReplicationData.AssetStateResponse assetStateResponse) {
        if (snippetsInSyncState(assetStateResponse)) {
            return;
        }
    }

    private boolean snippetsInSyncState(ReplicationData.AssetStateResponse assetStateResponse) {
        return false;
    }

    private void syncAccounts(ReplicationData.AssetStateResponse assetStateResponse) {
        if (accountsInSyncState(assetStateResponse)) {
            return;
        }
    }

    private boolean accountsInSyncState(ReplicationData.AssetStateResponse assetStateResponse) {
        return false;
    }

    private void syncCompanies(ReplicationData.AssetStateResponse assetStateResponse) {
        if (companiesInSyncState(assetStateResponse)) {
            return;
        }
    }

    private boolean companiesInSyncState(ReplicationData.AssetStateResponse assetStateResponse) {
        return false;
    }

    private ReplicationData.AssetStateResponse getAssetState() {
        return null;
    }

    public ReplicationData.AssetStateResponse currentAssets() {
        ReplicationData.AssetStateResponse res = new ReplicationData.AssetStateResponse();
        res.companies.addAll(companyRepository.findAllUniqueIds());
        res.usernames.addAll(accountRepository.findAllUsernames());
        res.snippets.addAll(snippetRepository.findAllSnippetCodes());
        res.plans.addAll(planRepository.findAllAsIds().parallelStream()
                .map(id->{
                    PlanImpl plan = planCache.findById(id);
                    PlanParamsYaml params = plan.getPlanParamsYaml();
                    return (params.internalParams!=null && params.internalParams.archived) ? null : plan.code;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        return res;
    }
}
