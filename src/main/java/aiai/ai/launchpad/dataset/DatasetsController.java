package aiai.ai.launchpad.dataset;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
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
    private DatasetGroupsRepository groupsRepository;

    public DatasetsController(DatasetsRepository repository, DatasetGroupsRepository groupsRepository) {
        this.repository = repository;
        this.groupsRepository = groupsRepository;
    }

    @Data
    public static class Result {
        public Slice<Dataset> items;
    }

    @Data
    public static class DatasetDefinition {
        public DatasetDefinition(Dataset dataset) {
            this.dataset = dataset;
        }

        public Dataset dataset;
    }

    @GetMapping("/datasets")
    public String init(@ModelAttribute Result result, @PageableDefault(size=5) Pageable pageable)  {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/launchpad/datasets";
    }

    // for AJAX
    @PostMapping("/datasets-part")
    public String getDatasets(@ModelAttribute Result result, @PageableDefault(size=5) Pageable pageable )  {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/launchpad/datasets :: table";
    }

    @GetMapping(value = "/dataset-add")
    public String add(Model model) {
        model.addAttribute("dataset", new Dataset());
        return "/launchpad/dataset-form";
    }

    @GetMapping(value = "/dataset-edit/{id}")
    public String edit(@PathVariable Long id, Model model){
        model.addAttribute("dataset", repository.findById(id));
        return "/launchpad/dataset-form";
    }

    @GetMapping(value = "/dataset-definition/{id}")
    public String toDatasetDefinition(@PathVariable(name="id") Long datasetId, Model model){
        final Optional<Dataset> dataset = repository.findById(datasetId);
        if (!dataset.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        
        model.addAttribute("result", new DatasetDefinition(dataset.get()) );
        return "/launchpad/dataset-definition";
    }

    @GetMapping(value = "/dataset-group-add-new/{id}")
    public String addNewGroup(@PathVariable(name = "id") Long datasetId, Model model){
        final Optional<Dataset> value = repository.findById(datasetId);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = value.get();

        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(datasetId);
        int groupNumber;
        if (groups.isEmpty()) {
            groupNumber = 1;
        }
        else {
            groupNumber = groups.stream().mapToInt(DatasetGroup::getGroupNumber).max().getAsInt() + 1;
        }

        final DatasetGroup group = new DatasetGroup(groupNumber);
        group.setDataset(dataset);

        dataset.getDatasetGroups().add(group);

        repository.save( dataset );
        return "redirect:/launchpad/dataset-definition/"+datasetId;
    }

    @PostMapping("/dataset-form-commit")
    public String formCommit(Dataset dataset) {
        repository.save( dataset );
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
