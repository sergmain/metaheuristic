/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

import aiai.ai.Globals;
import aiai.ai.launchpad.beans.Station;
import aiai.ai.launchpad.repositories.StationsRepository;
import aiai.ai.utils.ControllerUtils;
import lombok.Data;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:21
 */
@Controller
@RequestMapping("/launchpad")
public class StationsController {

    private final Globals globals;
    private final StationsRepository repository;

    public StationsController(Globals globals, StationsRepository repository) {
        this.globals = globals;
        this.repository = repository;
    }

    @GetMapping("/stations")
    public String init(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.stationRowsLimit, pageable);
        result.items = repository.findAll(pageable);
        return "launchpad/stations";
    }

    // for AJAX
    @PostMapping("/stations-part")
    public String getStations(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.stationRowsLimit, pageable);
        result.items = repository.findAll(pageable);
        return "launchpad/stations :: table";
    }

    @GetMapping(value = "/station-add")
    public String add(Model model) {
        model.addAttribute("station", new Station());
        return "launchpad/station-form";
    }

    @GetMapping(value = "/station-edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("station", repository.findById(id));
        return "launchpad/station-form";
    }

    @PostMapping("/station-form-commit")
    public String formCommit(Station station) {
        repository.save(station);
        return "redirect:/launchpad/stations";
    }

    @GetMapping("/station-delete/{id}")
    public String delete(@PathVariable Long id, Model model) {
        final Optional<Station> value = repository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/stations";
        }
        model.addAttribute("station", value.get());
//        model.addAttribute("station", repository.findById(id));
        return "launchpad/station-delete";
    }

    @PostMapping("/station-delete-commit")
    public String deleteCommit(Long id) {
        repository.deleteById(id);
        return "redirect:/launchpad/stations";
    }

    @Data
    public static class Result {
        public Slice<Station> items;
    }

}
