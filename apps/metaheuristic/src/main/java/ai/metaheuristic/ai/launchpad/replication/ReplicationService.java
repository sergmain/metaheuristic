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
import ai.metaheuristic.ai.launchpad.data.ReplicationData;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 1/9/2020
 * Time: 12:16 AM
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReplicationService {

    public final Globals globals;

    public void sync() {
        if (globals.assetMode!= EnumsApi.LaunchpadAssetMode.replicated) {
            return;
        }
        ReplicationData.AssetState assetState = getAssetState();
        syncCompanies(assetState);
        syncAccounts(assetState);
        syncSnippets(assetState);
        syncPLans(assetState);
    }

    private void syncPLans(ReplicationData.AssetState assetState) {
        if (plansInSyncState(assetState)) {
            return;
        }
    }

    private boolean plansInSyncState(ReplicationData.AssetState assetState) {
        return false;
    }

    private void syncSnippets(ReplicationData.AssetState assetState) {
        if (snippetsInSyncState(assetState)) {
            return;
        }
    }

    private boolean snippetsInSyncState(ReplicationData.AssetState assetState) {
        return false;
    }

    private void syncAccounts(ReplicationData.AssetState assetState) {
        if (accountsInSyncState(assetState)) {
            return;
        }
    }

    private boolean accountsInSyncState(ReplicationData.AssetState assetState) {
        return false;
    }

    private void syncCompanies(ReplicationData.AssetState assetState) {
        if (companiesInSyncState(assetState)) {
            return;
        }
    }

    private boolean companiesInSyncState(ReplicationData.AssetState assetState) {
        return false;
    }

    private ReplicationData.AssetState getAssetState() {
        return null;
    }

}
