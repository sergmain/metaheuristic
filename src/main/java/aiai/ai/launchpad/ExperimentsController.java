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
 * Time: 20:22
 */
@Controller
@RequestMapping("/launchpad")
public class ExperimentsController {

    @Value("${aiai.table.rows.limit}")
    private int limit;

    private ExperimentRepository repository;

    public ExperimentsController(ExperimentRepository repository) {
        this.repository = repository;
    }

    @Data
    public static class Result {
        public Slice<Experiment> items;
    }

    @GetMapping("/experiments")
    public String init(@ModelAttribute Result result, @PageableDefault(size=5) Pageable pageable)  {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/launchpad/experiments";
    }

    // for AJAX
    @PostMapping("/experiments-part")
    public String getExperiments(@ModelAttribute Result result, @PageableDefault(size=5) Pageable pageable )  {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/launchpad/experiments :: table";
    }

    @GetMapping(value = "/experiment-add")
    public String add(Model model) {
        model.addAttribute("experiment", new Experiment());
        return "/launchpad/experiment-form";
    }

    @GetMapping(value = "/experiment-edit/{id}")
    public String edit(@PathVariable Long id, Model model){
        model.addAttribute("experiment", repository.findById(id));
        return "/launchpad/experiment-form";
    }

    @PostMapping("/experiment-form-commit")
    public String formCommit(Experiment experiment) {
        repository.save( experiment );
        return "redirect:/launchpad/experiments";
    }

    @GetMapping("/experiment-delete/{id}")
    public String delete(@PathVariable Long id, Model model){
        final Optional<Experiment> value = repository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/experiments";
        }
        model.addAttribute("experiment", value.get());
        return "/launchpad/experiment-delete";
    }

    @PostMapping("/experiment-delete-commit")
    public String deleteCommit(Long id) {
        repository.deleteById(id);
        return "redirect:/launchpad/experiments";
    }

    private Pageable fixPageSize(Pageable pageable) {
        if (pageable.getPageSize()!=limit) {
            pageable = PageRequest.of(pageable.getPageNumber(), limit);
        }
        return pageable;
    }
}
