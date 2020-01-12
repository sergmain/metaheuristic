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

package ai.metaheuristic.ai.launchpad.server;

import ai.metaheuristic.ai.launchpad.data.ReplicationData;
import ai.metaheuristic.ai.launchpad.replication.ReplicationService;
import ai.metaheuristic.ai.launchpad.replication.ReplicationSourceService;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * @author Serge
 * Date: 1/9/2020
 * Time: 11:30 PM
 */
@RestController
@Slf4j
@Profile("launchpad")
@RequestMapping("/rest/v1/replication")
@PreAuthorize("hasAnyRole('ASSET_REST_ACCESS')")
@RequiredArgsConstructor
public class ReplicationController {

    private final ReplicationSourceService replicationSourceService;

    @GetMapping(value="/current-assets")
    public @ResponseBody ReplicationData.AssetStateResponse currentAssets() {
        return replicationSourceService.currentAssets();
    }

    @PostMapping(value="/snippet")
    public @ResponseBody ReplicationData.SnippetAsset getSnippet(@RequestParam String snippetCode) {
        return replicationSourceService.getSnippet(snippetCode);
    }

    @PostMapping(value="/plan")
    public @ResponseBody ReplicationData.PlanAsset getPlan(@RequestParam String planCode) {
        return replicationSourceService.getPlan(planCode);
    }

}
