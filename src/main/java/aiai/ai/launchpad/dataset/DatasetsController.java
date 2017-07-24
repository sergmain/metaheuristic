package aiai.ai.launchpad.dataset;

import aiai.ai.launchpad.dataset.repo.DatasetColumnRepository;
import aiai.ai.launchpad.dataset.repo.DatasetGroupsRepository;
import aiai.ai.launchpad.dataset.repo.DatasetsRepository;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private DatasetColumnRepository columnRepository;

    public DatasetsController(DatasetsRepository repository, DatasetGroupsRepository groupsRepository, DatasetColumnRepository columnRepository) {
        this.repository = repository;
        this.groupsRepository = groupsRepository;
        this.columnRepository = columnRepository;
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
    public String init(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = fixPageSize(pageable);
        result.items = repository.findAll(pageable);
        return "/launchpad/datasets";
    }

    // for AJAX
    @PostMapping("/datasets-part")
    public String getDatasets(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
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
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("dataset", repository.findById(id));
        return "/launchpad/dataset-form";
    }

    @GetMapping(value = "/dataset-definition/{id}")
    public String toDatasetDefinition(@PathVariable(name = "id") Long datasetId, Model model) {
        final Optional<Dataset> datasetOptional = repository.findById(datasetId);
        if (!datasetOptional.isPresent()) {
            return "redirect:/launchpad/datasets";
        }

        final DatasetDefinition definition = new DatasetDefinition(datasetOptional.get());
        final Dataset dataset = definition.dataset;

        final int groupSize = dataset.getDatasetGroups().size();
        for (int i = 0; i < groupSize; i++) {
            DatasetGroup group = dataset.getDatasetGroups().get(i);
            group.setAddColumn(false);
            if (i > 0) {
                DatasetGroup groupPrev = dataset.getDatasetGroups().get(i - 1);
                if (groupPrev.getDatasetColumns().isEmpty()) {
                    break;
                }
            }

            if (groupSize == 1) {
                group.setAddColumn(true);
                continue;
            }
            if (i == groupSize - 1) {
                group.setAddColumn(true);
                continue;
            }
            DatasetGroup groupNext = dataset.getDatasetGroups().get(i + 1);
            if (groupNext.getDatasetColumns().isEmpty()) {
                group.setAddColumn(true);
                continue;
            }
        }

        // ugly but it works
        // dont invert the condition
        if (dataset.getDatasetGroups().size()==1 && dataset.getDatasetGroups().get(0).getDatasetColumns().isEmpty()) {
            // nothing to do with this
        }
        else {
            // last actual column in groups. there isn't any non-empty group after this one
            for (int i = 0; i < dataset.getDatasetGroups().size(); i++) {

                // case when last group isn't empty
                if (i + 1 == dataset.getDatasetGroups().size()) {
                    final List<DatasetColumn> columns = dataset.getDatasetGroups().get(i).getDatasetColumns();
                    columns.get(columns.size() - 1).setLastColumn(true);
                    break;
                }
                // case when there are some empty groups
                if (i < dataset.getDatasetGroups().size() - 1 && dataset.getDatasetGroups().get(i + 1).getDatasetColumns().size() == 0) {
                    final List<DatasetColumn> columns = dataset.getDatasetGroups().get(i).getDatasetColumns();
                    if (columns.isEmpty()) {
                        continue;
                    }
                    columns.get(columns.size() - 1).setLastColumn(true);
                    break;
                }
            }
        }

        model.addAttribute("result", definition);
        return "/launchpad/dataset-definition";
    }

    @GetMapping(value = "/dataset-column-add/{id}")
    public String addColumn(@PathVariable(name = "id") Long datasetGroupId, Model model) {
        final DatasetColumn column = new DatasetColumn();
        column.setDatasetGroup(groupsRepository.findById(datasetGroupId).get());
        model.addAttribute("column", column);
        return "/launchpad/dataset-column-form";
    }

    @PostMapping(value = "/dataset-column-add-commit")
    public String addNewColumnCommit(DatasetColumn column) {
        final Optional<DatasetGroup> value = groupsRepository.findById(column.getDatasetGroup().getId());
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();

        column.setDatasetGroup(group);
        columnRepository.save(column);

        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @GetMapping(value = "/dataset-column-move-prev-group/{id}")
    public String moveColumnToPrevGroup(@PathVariable Long id) {
        final Optional<DatasetColumn> value = columnRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetColumn column = value.get();

/*
        final long datasetId = column.getDataset().getId();
        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(datasetId);
*/
        final Dataset dataset = column.getDatasetGroup().getDataset();
        List<DatasetGroup> groups = dataset.getDatasetGroups();
        if (groups.size() < 2) {
            return "redirect:/launchpad/datasets";
        }

        DatasetGroup prevGroup = null;
        for (DatasetGroup group : groups) {
            if (column.getDatasetGroup().getId() == group.getId()) {
                if (prevGroup == null) {
                    return "redirect:/launchpad/datasets";
                }
                column.setDatasetGroup(prevGroup);
                break;
            }
            prevGroup = group;
        }

        columnRepository.save(column);
        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @GetMapping(value = "/dataset-column-move-next-group/{id}")
    public String moveColumnToNextGroup(@PathVariable Long id) {
        final Optional<DatasetColumn> value = columnRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetColumn column = value.get();

/*
        final long datasetId = column.getDataset().getId();
        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(datasetId);
*/
        final Dataset dataset = column.getDatasetGroup().getDataset();
        List<DatasetGroup> groups = dataset.getDatasetGroups();
        if (groups.size() < 2) {
            return "redirect:/launchpad/datasets";
        }

        for (int i = 0; i < groups.size() - 1; i++) {
            DatasetGroup group = groups.get(i);
            if (column.getDatasetGroup().getId() == group.getId()) {
                DatasetGroup nextGroup = groups.get(i + 1);
                column.setDatasetGroup(nextGroup);
                break;
            }
        }

        columnRepository.save(column);
        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @GetMapping(value = "/dataset-delete-group/{id}")
    public String deleteGroup(@PathVariable Long id) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        long datasetId = group.getDataset().getId();
        if (!group.getDatasetColumns().isEmpty()) {
            return "redirect:/launchpad/datasets";
        }

        groupsRepository.delete(group);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-group-add-new/{id}")
    public String addNewGroup(@PathVariable(name = "id") Long datasetId) {
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

        repository.save(dataset);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @PostMapping(value = "/dataset-group-skip-commit")
    public String setSkipForGroup(Long id, boolean skip) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        group.setSkip(skip);
        groupsRepository.save(group);

        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }


    @PostMapping(value = "/dataset-group-from-file")
    public String createDefinitionFromFile(MultipartFile file, @RequestParam(name = "id") long datasetId) {
        Optional<Dataset> optionalDataset = repository.findById(datasetId);
        if (!optionalDataset.isPresent()) {
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }
        Dataset dataset = optionalDataset.get();
        groupsRepository.deleteDatasetGroupByDataset_Id(datasetId);

        try (InputStream is = file.getInputStream(); final InputStreamReader isr = new InputStreamReader(is, "UTF-8"); BufferedReader br = new BufferedReader(isr)) {
            String line = br.readLine();
            List<String> names = Arrays.stream(line.split("[,]")).filter(s -> s != null && s.length() > 0).map(String::trim).collect(Collectors.toList());

            DatasetGroup group = new DatasetGroup();
            group.setDescription("Group #1");
            group.setDataset(dataset);
            List<DatasetGroup> groups = new ArrayList<>();
            groups.add(group);
            dataset.setDatasetGroups(groups);

            int i = 1;
            for (String name : names) {
                DatasetColumn c = new DatasetColumn();
                c.setDatasetGroup(group);
                c.setName(StringUtils.substring(name, 0, 50));
                c.setDescription(StringUtils.substring("Column #" + (i++) + ", " + name, 0, 250));
                columnRepository.save(c);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("error", e);
        }

        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @PostMapping("/dataset-form-commit")
    public String formCommit(Dataset dataset) {
        repository.save(dataset);
        return "redirect:/launchpad/datasets";
    }

    @GetMapping("/dataset-delete/{id}")
    public String delete(@PathVariable Long id, Model model) {
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
        if (pageable.getPageSize() != limit) {
            pageable = PageRequest.of(pageable.getPageNumber(), limit);
        }
        return pageable;
    }
}
