package aiai.ai.launchpad.dataset;

import aiai.ai.launchpad.dataset.repo.DatasetColumnRepository;
import aiai.ai.launchpad.dataset.repo.DatasetGroupsRepository;
import aiai.ai.launchpad.dataset.repo.DatasetPathRepository;
import aiai.ai.launchpad.dataset.repo.DatasetsRepository;
import lombok.Data;
import org.apache.commons.io.FileUtils;
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

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Value("${aiai.launchpad.dir}")
    private String launchpadDirAsString;

    private DatasetsRepository repository;
    private DatasetGroupsRepository groupsRepository;
    private DatasetColumnRepository columnRepository;
    private DatasetPathRepository pathRepository;

    public DatasetsController(DatasetsRepository repository, DatasetGroupsRepository groupsRepository, DatasetColumnRepository columnRepository, DatasetPathRepository pathRepository) {
        this.repository = repository;
        this.groupsRepository = groupsRepository;
        this.columnRepository = columnRepository;
        this.pathRepository = pathRepository;
    }

    @Data
    public static class Result {
        public Slice<Dataset> items;
    }

    @Data
    public static class DatasetDefinition {
        public DatasetDefinition(Dataset dataset, String launchpadDirAsString) {
            this.dataset = dataset;
            this.launchpadDirAsString = launchpadDirAsString;
        }

        public Dataset dataset;
        public List<DatasetPath> paths = new ArrayList<>();
        public String launchpadDirAsString;
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

        final DatasetDefinition definition = new DatasetDefinition(datasetOptional.get(), launchpadDirAsString);
        final Dataset dataset = definition.dataset;
        definition.paths = pathRepository.findByDataset_OrderByPathNumber(dataset);


        // fix conditions for UI
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
                //noinspection UnnecessaryContinue
                continue;
            }
        }

        // ugly but it works
        boolean isAllEmpty = true;
        for (DatasetGroup group : dataset.getDatasetGroups()) {
            if (!group.getDatasetColumns().isEmpty()) {
                isAllEmpty = false;
                break;
            }
        }

        // don't invert the condition, because ...
        //noinspection StatementWithEmptyBody
        if (isAllEmpty) {
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
        final Optional<DatasetGroup> value = groupsRepository.findById(datasetGroupId);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        final DatasetColumn column = new DatasetColumn();
        column.setDatasetGroup(group);
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


    @GetMapping(value = "/dataset-column-edit/{id}")
    public String editDatasetColumn(@PathVariable Long id, Model model) {
        final Optional<DatasetColumn> optionalColumn = columnRepository.findById(id);
        if (!optionalColumn.isPresent()) {
            return "redirect:/launchpad/datasets";
        }

        model.addAttribute("column", optionalColumn.get());
        return "/launchpad/dataset-column-form";
    }

    @PostMapping("/dataset-column-form-commit")
    public String datasetColumnFormCommit(DatasetColumn column) {
        columnRepository.save(column);
        return "redirect:/launchpad/dataset-definition/" + column.getDatasetGroup().getDataset().getId();
    }

    @GetMapping("/dataset-column-delete/{id}")
    public String deleteColumn(@PathVariable Long id, Model model) {
        final Optional<DatasetColumn> value = columnRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        model.addAttribute("column", value.get());
        return "/launchpad/dataset-column-delete";
    }

    @PostMapping("/dataset-column-delete-commit")
    public String deleteColumnCommit(Long id) {
        final Optional<DatasetColumn> value = columnRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetColumn column = value.get();
        final long datasetId = column.getDatasetGroup().getDataset().getId();
        columnRepository.deleteById(id);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-column-move-prev-group/{id}")
    public String moveColumnToPrevGroup(@PathVariable Long id) {
        final Optional<DatasetColumn> value = columnRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetColumn column = value.get();
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
        //noinspection ConstantConditions
        groupNumber = groups.isEmpty() ? 1 : groups.stream().mapToInt(DatasetGroup::getGroupNumber).max().getAsInt() + 1;

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

    @PostMapping(value = "/dataset-group-feature-commit")
    public String setFeatureForGroup(Long id, boolean feature) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        group.setFeature(feature);
        group.setSkip(false);
        groupsRepository.save(group);

        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @PostMapping(value = "/dataset-header-commit")
    public String setHeaderForDataset(Long id, boolean header) {
        final Optional<Dataset> value = repository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = value.get();
        dataset.setHeader(header);
        repository.save(dataset);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }


    @PostMapping(value = "/dataset-group-from-file")
    public String createDefinitionFromFile(MultipartFile file,
                                           @RequestParam(name = "id") long datasetId,
                                           @RequestParam(required = false, defaultValue = "false", name = "is_definition_only") boolean isDefinitionOnly,
                                           @RequestParam(required = false, defaultValue = "false", name = "is_header") boolean isHeader
    ) {
        Optional<Dataset> optionalDataset = repository.findById(datasetId);
        if (!optionalDataset.isPresent()) {
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }
        Dataset dataset = optionalDataset.get();
        dataset.setHeader(isHeader);
        groupsRepository.deleteByDataset(dataset);

        if (isDefinitionOnly) {
            try (InputStream is = file.getInputStream()) {
                createColumnsDefinition(dataset, is);
            } catch (IOException e) {
                throw new RuntimeException("error", e);
            }
        }
        else {

            List<DatasetPath> paths = pathRepository.findByDataset(dataset);
            //noinspection ConstantConditions
            int pathNumber = paths.isEmpty() ? 1 : paths.stream().mapToInt(DatasetPath::getPathNumber).max().getAsInt() + 1;
            final String path = String.format("datasets%c%03d%c%d", File.separatorChar, dataset.getId(), File.separatorChar, pathNumber);

            final File launchpadDir = toFile(launchpadDirAsString);

            File datasetDir = new File(launchpadDir, path);
            if (!datasetDir.exists()) {
                boolean status = datasetDir.mkdirs();
                if (!status) {
                    throw new IllegalStateException("Error create directory: " + datasetDir.getAbsolutePath());
                }
            }

            File datasetFile;
            try (InputStream is = file.getInputStream()) {
                datasetFile = File.createTempFile("dataset-"+pathNumber, ".csv", datasetDir );
                FileUtils.copyInputStreamToFile(is, datasetFile);
            } catch (IOException e) {
                throw new RuntimeException("error", e);
            }

            DatasetPath dp = new DatasetPath();
            String pathToDataset = path + File.separatorChar + datasetFile.getName();
            dp.setPath( pathToDataset );
            dp.setChecksum(DatasetChecksum.getChecksumAsJson(datasetFile));
            dp.setDataset(dataset);
            dp.setFile(true);
            dp.setPathNumber(pathNumber);
            dp.setValid(true);
            pathRepository.save(dp);

            try (InputStream is = new FileInputStream(datasetFile)) {
                createColumnsDefinition(dataset, is);
            } catch (IOException e) {
                throw new RuntimeException("error", e);
            }
        }

        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    private static File toFile(String launchpadDirAsString) {
        if (launchpadDirAsString.charAt(0)=='.' && (launchpadDirAsString.charAt(1)=='\\' || launchpadDirAsString.charAt(1)=='/')) {
            return new File(launchpadDirAsString.substring(2));
        }
        return new File(launchpadDirAsString);
    }

    private void createColumnsDefinition(Dataset dataset, InputStream is) throws IOException {
        try (final InputStreamReader isr = new InputStreamReader(is, "UTF-8")) {
            try (BufferedReader br = new BufferedReader(isr)) {
                String line = br.readLine();
                DatasetGroup group = new DatasetGroup();
                group.setDescription("Group #1");
                group.setDataset(dataset);
                List<DatasetGroup> groups = new ArrayList<>();
                groups.add(group);
                dataset.setDatasetGroups(groups);

                final AtomicInteger i = new AtomicInteger(1);
                List<DatasetColumn> columns = new ArrayList<>();
                Arrays.stream(line.split("[,]")).filter(s -> s != null && s.length() > 0).map(String::trim).forEach(name -> {
                            final DatasetColumn c = new DatasetColumn();
                            c.setDatasetGroup(group);
                            c.setName(StringUtils.substring(name, 0, 50));
                            c.setDescription(StringUtils.substring("Column #" + (i.getAndAdd(1)) + ", " + name, 0, 250));
                            columns.add(c);
                        }
                );
                columnRepository.saveAll(columns);
            }
        }
    }

    @PostMapping("/dataset-form-commit")
    public String datasetFormCommit(Dataset dataset) {
        repository.save(dataset);
        return "redirect:/launchpad/datasets";
    }

    @PostMapping("/dataset-definition-form-commit")
    public String datasetDefinitionFormCommit(DatasetDefinition datasetDefinition) {
        repository.save(datasetDefinition.dataset);
        return "redirect:/launchpad/dataset-definition/" + datasetDefinition.dataset.getId();
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

    @GetMapping("/dataset-path-delete/{id}")
    public String deletePath(@PathVariable Long id) {
        final Optional<DatasetPath> value = pathRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetPath path = value.get();
        Dataset dataset = path.getDataset();
        pathRepository.delete(path);
        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }



    private Pageable fixPageSize(Pageable pageable) {
        if (pageable.getPageSize() != limit) {
            pageable = PageRequest.of(pageable.getPageNumber(), limit);
        }
        return pageable;
    }
}
