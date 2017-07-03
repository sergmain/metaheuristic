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

import java.util.List;
import java.util.Optional;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:21
 */
@Controller
@RequestMapping("/launchpad")
public class DatasetsController {

    @Value("${aiai.table.rows.limit}")
    private int limit;

    private DatasetsRepository repository;

    public DatasetsController(DatasetsRepository repository) {
        this.repository = repository;
    }

    @Data
    public static class Result {
        public Slice<Dataset> items;
    }

    @GetMapping("/datasets")
    public String init(@ModelAttribute Result result, @PageableDefault(size=5) Pageable pageable)  {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/launchpad/datasets";
    }

    // for AJAX
    @PostMapping("/datasets-part")
    public String getStations(@ModelAttribute Result result, @PageableDefault(size=5) Pageable pageable )  {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/launchpad/datasets :: table";
    }

    @GetMapping(value = "/dataset-add")
    public String add(Model model) {
        model.addAttribute("dataset", new Station());
        return "/launchpad/dataset-form";
    }

    @GetMapping(value = "/dataset-edit/{id}")
    public String edit(@PathVariable Long id, Model model){
        model.addAttribute("dataset", repository.findById(id));
        return "/launchpad/dataset-form";
    }

    @PostMapping("/dataset-form-commit")
    public String formCommit(Dataset station) {
        repository.save( station );
        return "redirect:/launchpad/datasets";
    }

    @GetMapping("/dataset-delete/{id}")
    public String delete(@PathVariable Long id, Model model){
        final Optional<Dataset> value = repository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        model.addAttribute("dataset", value.get());
        return "/launchpad/dataset-delete";
    }

    @PostMapping("/dataset-delete-commit")
    public String deleteCommit(Long id) {
        repository.deleteById(id);
        return "redirect:/launchpad/datasets";
    }

    private Pageable fixPageSize(Pageable pageable) {
        if (pageable.getPageSize()!=limit) {
            pageable = PageRequest.of(pageable.getPageNumber(), limit);
        }
        return pageable;
    }

}
