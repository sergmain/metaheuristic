/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.southbridge;

import ai.metaheuristic.ai.dispatcher.data.ReplicationData;
import ai.metaheuristic.ai.dispatcher.replication.ReplicationSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * @author Serge
 * Date: 1/9/2020
 * Time: 11:30 PM
 */
@RestController
@Slf4j
@Profile("dispatcher")
@RequestMapping("/rest/v1/replication")
@PreAuthorize("hasAnyRole('ASSET_REST_ACCESS')")
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ReplicationController {

    private final ReplicationSourceService replicationSourceService;

    @ResponseBody
    @GetMapping(value="/current-assets")
    public ReplicationData.AssetStateResponse currentAssets() {
        return replicationSourceService.currentAssets();
    }

    @ResponseBody
    @GetMapping(value="/function")
    public ReplicationData.FunctionAsset getFunction(@RequestParam String functionCode) {
        return replicationSourceService.getFunction(functionCode);
    }

    @ResponseBody
    @GetMapping(value="/source-code")
    public ReplicationData.SourceCodeAsset getSourceCode(@RequestParam String uid) {
        return replicationSourceService.getSourceCode(uid);
    }

    @ResponseBody
    @GetMapping(value="/company")
    public ReplicationData.CompanyAsset getCompany(@RequestParam long uniqueId) {
        return replicationSourceService.getCompany(uniqueId);
    }

    @ResponseBody
    @GetMapping(value="/account")
    public ReplicationData.AccountAsset getAccount(@RequestParam String username) {
        return replicationSourceService.getAccount(username);
    }

}
