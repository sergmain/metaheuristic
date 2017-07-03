package aiai.ai.launchpad;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${aiai.table.rows.limit}")
    private int limit;

    private final StationsRepository repository;

    public StationsController(StationsRepository repository) {
        this.repository = repository;
    }

    @Data
    public static class Result {
        public Slice<Station> items;
    }

    @GetMapping("/stations")
    public String init(@ModelAttribute Result result, @PageableDefault(size=5) Pageable pageable)  {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/launchpad/stations";
    }

    // for AJAX
    @PostMapping("/stations-part")
    public String getStations(@ModelAttribute Result result, @PageableDefault(size=5) Pageable pageable )  {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/launchpad/stations :: table";
    }

    @GetMapping(value = "/station-add")
    public String add(Model model) {
        model.addAttribute("station", new Station());
        return "/launchpad/station-form";
    }

    @GetMapping(value = "/station-edit/{id}")
    public String edit(@PathVariable Long id, Model model){
        model.addAttribute("station", repository.findById(id));
        return "/launchpad/station-form";
    }

    @PostMapping("/station-form-commit")
    public String formCommit(Station station) {
        repository.save( station );
        return "redirect:/launchpad/stations";
    }

    @GetMapping("/station-delete/{id}")
    public String delete(@PathVariable Long id, Model model){
        final Optional<Station> value = repository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/stations";
        }
        model.addAttribute("station", value.get());
//        model.addAttribute("station", repository.findById(id));
        return "/launchpad/station-delete";
    }

    @PostMapping("/station-delete-commit")
    public String deleteCommit(Long id) {
        repository.deleteById(id);
        return "redirect:/launchpad/stations";
    }

    private Pageable fixPageSize(Pageable pageable) {
        if (pageable.getPageSize()!=limit) {
            pageable = PageRequest.of(pageable.getPageNumber(), limit);
        }
        return pageable;
    }

}
