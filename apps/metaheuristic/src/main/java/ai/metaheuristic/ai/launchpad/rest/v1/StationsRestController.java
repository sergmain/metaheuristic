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

package ai.metaheuristic.ai.launchpad.rest.v1;

import ai.metaheuristic.ai.launchpad.beans.Station;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.ai.launchpad.data.StationData;
import ai.metaheuristic.ai.launchpad.station.StationTopLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest/v1/launchpad")
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'ACCESS_REST')")
public class StationsRestController {

    private final StationTopLevelService stationTopLevelService;

    @GetMapping("/stations")
    public StationData.StationsResult init(@PageableDefault(size = 5) Pageable pageable) {
        return stationTopLevelService.getStations(pageable);
    }

    @GetMapping(value = "/station/{id}")
    public StationData.StationResult getStation(@PathVariable Long id) {
        return stationTopLevelService.getStation(id);
    }

    @PostMapping("/station-form-commit")
    public StationData.StationResult formCommit(@RequestBody Station station) {
        return stationTopLevelService.saveStation(station);
    }

    @PostMapping("/station-delete-commit")
    public OperationStatusRest deleteStationCommit(Long id) {
        return stationTopLevelService.deleteStationById(id);
    }
}
