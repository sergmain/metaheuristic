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

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.api.EnumsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * @author Serge
 * Date: 1/9/2020
 * Time: 12:16 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ReplicationService {

    private final Globals globals;
    private final ReplicationCoreService replicationCoreService;
    private final ReplicationCompanyTopLevelService replicationCompanyTopLevelService;
    private final ReplicationAccountTopLevelService replicationAccountTopLevelService;
    private final ReplicationFunctionTopLevelService replicationFunctionTopLevelService;
    private final ReplicationSourceCodeTopLevelService replicationSourceCodeTopLevelService;

    public void sync() {
        if (globals.dispatcher.asset.mode!= EnumsApi.DispatcherAssetMode.replicated) {
            return;
        }
        ReplicationData.AssetStateResponse assetStateResponse = replicationCoreService.getAssetStates();
        if (assetStateResponse.isErrorMessages()) {
            log.error("308.010 Error while getting actual assets: " + assetStateResponse.getErrorMessagesAsStr());
            return;
        }
        replicationFunctionTopLevelService.syncFunctions(assetStateResponse.functions);
        replicationSourceCodeTopLevelService.syncSourceCodes(assetStateResponse.sourceCodeUids);
        replicationCompanyTopLevelService.syncCompanies(assetStateResponse.companies);
        replicationAccountTopLevelService.syncAccounts(assetStateResponse.usernames);
    }

}
