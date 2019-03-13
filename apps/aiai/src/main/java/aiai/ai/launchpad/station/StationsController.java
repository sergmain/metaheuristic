/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.station;

import aiai.ai.launchpad.beans.Station;
import aiai.ai.launchpad.data.StationData;
import aiai.ai.utils.ControllerUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:21
 */
@Controller
@RequestMapping("/launchpad")
@Profile("launchpad")
public class StationsController {

    private final StationTopLevelService stationTopLevelService;

    public StationsController(StationTopLevelService stationTopLevelService) {
        this.stationTopLevelService = stationTopLevelService;
    }

    @GetMapping("/stations")
    public String getStations(Model model, @PageableDefault(size = 5) Pageable pageable) {
        StationData.StationsResultRest stationsResultRest = stationTopLevelService.getStations(pageable);
        ControllerUtils.addMessagesToModel(model, stationsResultRest);
        model.addAttribute("result", stationsResultRest);
        return "launchpad/stations";
    }

    // for AJAX
    @PostMapping("/stations-part")
    public String getStationsForAjax(Model model, @PageableDefault(size = 5) Pageable pageable) {
        StationData.StationsResultRest stationsResultRest = stationTopLevelService.getStations(pageable);
        model.addAttribute("result", stationsResultRest);
        return "launchpad/stations";
    }

    @GetMapping(value = "/station-add")
    public String add(Model model) {
        model.addAttribute("station", new Station());
        return "launchpad/station-form";
    }

    @GetMapping(value = "/station-edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        StationData.StationResultRest stationResultRest = stationTopLevelService.getStation(id);
        // TODO add handler of errorMessages
        model.addAttribute("station", stationResultRest.station);
        return "launchpad/station-form";
    }

    @PostMapping("/station-form-commit")
    public String saveStation(Station station) {
        stationTopLevelService.saveStation(station);
        return "redirect:/launchpad/stations";
    }

    @GetMapping("/station-delete/{id}")
    public String delete(@PathVariable Long id, Model model) {
        StationData.StationResultRest stationResultRest = stationTopLevelService.getStation(id);
        // TODO add handler of errorMessages
        model.addAttribute("station", stationResultRest.station);
        return "launchpad/station-delete";
    }

    @PostMapping("/station-delete-commit")
    public String deleteCommit(Long id) {
        stationTopLevelService.deleteStationById(id);
        return "redirect:/launchpad/stations";
    }

}
