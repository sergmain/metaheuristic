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

package aiai.ai.launchpad.dataset;

import aiai.ai.Consts;
import aiai.ai.Globals;
import aiai.ai.launchpad.beans.*;
import aiai.ai.core.ArtifactStatus;
import aiai.ai.core.ProcessService;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.snippet.SnippetCode;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.utils.SimpleSelectOption;
import aiai.ai.utils.StrUtils;
import aiai.ai.yaml.config.DatasetPreparingConfig;
import aiai.ai.yaml.config.DatasetPreparingConfigUtils;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.yaml.snippet.SnippetType;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:21
 */
@Controller
@RequestMapping("/launchpad")
@Slf4j
public class DatasetController {

    private static final String CONFIG_YAML = "config.yaml";
    private static final String PRODUCE_FEATURE_YAML = "produce-feature.yaml";
    private static final Set<String> exts;

    @Data
    public static class Result {
        public Slice<Dataset> items;
    }

    @Data
    public static class DatasetDefinition {
        public Dataset dataset;
        public List<DatasetPath> paths = new ArrayList<>();
        public String launchpadDirAsString;
        public String datasetDirAsString;
        public List<SimpleSelectOption> assemblyOptions;
        public List<SimpleSelectOption> datasetOptions;
        public List<SimpleSelectOption> featureOptions;

        public DatasetDefinition(Dataset dataset, String launchpadDirAsString, String datasetDirAsString) {
            this.dataset = dataset;
            this.launchpadDirAsString = launchpadDirAsString;
            this.datasetDirAsString = datasetDirAsString;
        }
    }

    static {
        exts = new HashSet<>();
        Collections.addAll(exts, ".json", ".csv", ".txt", ".xml", ".yaml");
    }

    private final Globals globals;
    private final DatasetRepository datasetRepository;
    private final DatasetGroupsRepository groupsRepository;
    private final DatasetColumnRepository columnRepository;
    private final DatasetPathRepository pathRepository;
    private final ProcessService processService;
    private final SnippetService snippetService;
    private final SnippetRepository snippetRepository;

    public DatasetController(Globals globals, DatasetRepository datasetRepository, DatasetGroupsRepository groupsRepository, DatasetColumnRepository columnRepository, DatasetPathRepository pathRepository, ProcessService processService, SnippetService snippetService, SnippetRepository snippetRepository) {
        this.globals = globals;
        this.datasetRepository = datasetRepository;
        this.groupsRepository = groupsRepository;
        this.columnRepository = columnRepository;
        this.pathRepository = pathRepository;
        this.processService = processService;
        this.snippetService = snippetService;
        this.snippetRepository = snippetRepository;
    }

    @GetMapping("/datasets")
    public String init(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable, @ModelAttribute("errorMessage") final String errorMessage) {
        pageable = ControllerUtils.fixPageSize(globals.datasetRowsLimit, pageable);
        result.items = datasetRepository.findAll(pageable);
        return "launchpad/datasets";
    }

    // for AJAX
    @PostMapping("/datasets-part")
    public String getDatasets(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.datasetRowsLimit, pageable);
        result.items = datasetRepository.findAll(pageable);
        return "launchpad/datasets :: table";
    }

    @GetMapping(value = "/dataset-add")
    public String add(Model model) {
        model.addAttribute("dataset", new Dataset());
        return "launchpad/dataset-form";
    }

    @GetMapping(value = "/dataset-edit/{id}")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        final Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#173.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/datasets";
        }
        model.addAttribute("dataset", dataset);
        return "launchpad/dataset-form";
    }

    @PostMapping("/dataset-form-commit")
    public String datasetFormCommit(Dataset dataset, final RedirectAttributes redirectAttributes) {
        final Dataset ds = datasetRepository.findById(dataset.getId()).orElse(null);
        if (ds == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#173.02 dataset wasn't found, datasetId: " + dataset.getId());
            return "redirect:/launchpad/datasets";
        }
        ds.setName(dataset.getName());
        ds.setDescription(dataset.getDescription());
        ds.setEditable(true);
        datasetRepository.save(ds);
        return "redirect:/launchpad/datasets";
    }

    @GetMapping(value = "/dataset-definition/{id}")
    public String toDatasetDefinition(@PathVariable(name = "id") Long datasetId, Model model, @ModelAttribute("errorMessage") final String errorMessage, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetRepository.findById(datasetId).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#174.01 dataset wasn't found, datasetId: " + datasetId);
            return "redirect:/launchpad/datasets";
        }

        // path variable is for informing user about directory structure
        final String path = String.format("<Launchpad directory>%c%s%c%06d", File.separatorChar, Consts.DATASET_DIR, File.separatorChar, dataset.getId());

        final DatasetDefinition definition = new DatasetDefinition(dataset, globals.launchpadDir.getPath(), path);
        definition.paths = pathRepository.findByDataset_OrderByPathNumber(dataset);

        // fix conditions for UI
        final int groupSize = dataset.getDatasetGroups().size();
        for (int i = 0; i < groupSize; i++) {
            DatasetGroup group = dataset.getDatasetGroups().get(i);
            group.setAddColumn(true);
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
        // TODO 2018-03-17 I forgot why
        //noinspection StatementWithEmptyBody
        if (isAllEmpty) {
            // nothing to do with this
        } else {
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

        final Iterable<Snippet> snippets = snippetRepository.findAll();
        final List<SnippetCode> assemblyCodes = dataset.getAssemblySnippet() == null ? new ArrayList<>() : Collections.singletonList(new SnippetCode(dataset.getAssemblySnippet().getId(), dataset.getAssemblySnippet().getSnippetCode()));
        definition.assemblyOptions = snippetService.getSelectOptions(snippets, assemblyCodes, (s) -> SnippetType.assembly!=(SnippetType.valueOf(s.type)));

        final List<SnippetCode> datasetCodes = dataset.getDatasetSnippet() == null ? new ArrayList<>() : Collections.singletonList(new SnippetCode(dataset.getDatasetSnippet().getId(), dataset.getDatasetSnippet().getSnippetCode()));
        definition.datasetOptions = snippetService.getSelectOptions(snippets, datasetCodes, (s) -> SnippetType.dataset!=(SnippetType.valueOf(s.type)));

        definition.featureOptions = snippetService.getSelectOptions(snippets, new ArrayList<>(), (s) -> SnippetType.fit!=(SnippetType.valueOf(s.type)));

        model.addAttribute("result", definition);
        return "launchpad/dataset-definition";
    }

    @PostMapping("/dataset-snippet-assembly-commit/{id}")
    public String snippetAssemblyCommit(@PathVariable Long id, String code, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#175.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/datasets";
        }
        SnippetVersion snippetVersion = SnippetVersion.from(code);

        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        if (snippet==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#176.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/dataset-definition/" + dataset.getId();
        }
        dataset.setAssemblySnippet(snippet);
        datasetRepository.save(dataset);
        return "redirect:/launchpad/dataset-definition/"+id;
    }

    @PostMapping("/dataset-snippet-dataset-commit/{id}")
    public String snippetDatasetCommit(@PathVariable Long id, String code, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#177.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/datasets";
        }
        SnippetVersion snippetVersion = SnippetVersion.from(code);

        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        if (snippet==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#178.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/dataset-definition/" + dataset.getId();
        }
        dataset.setDatasetSnippet(snippet);
        datasetRepository.save(dataset);
        return "redirect:/launchpad/dataset-definition/"+id;
    }

    @PostMapping(value = "/dataset-clone-commit")
    public String cloneDataset(Long id, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#150.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/datasets";
        }
        Dataset ds = new Dataset();
        ds.setName(StrUtils.incCopyNumber(dataset.getName()));
        ds.setDescription(dataset.getDescription());
        ds.setAssemblingCommand(dataset.getAssemblingCommand());
        ds.setProducingCommand(dataset.getProducingCommand());
        ds.setEditable(true);
        ds.setLocked(false);
        ds.setDatasetGroups(new ArrayList<>());
        datasetRepository.save(ds);

        for (DatasetGroup datasetGroup : dataset.getDatasetGroups()) {
            DatasetGroup dg = new DatasetGroup();
            BeanUtils.copyProperties(datasetGroup, dg);
            dg.setId(null);
            dg.setVersion(null);
            // 2018.09.08, right now, we don't use GroupColumn beans
            dg.setDatasetColumns(new ArrayList<>());
            dg.setFeatureStatus(ArtifactStatus.NONE.value);
            dg.setDataset(ds);
            groupsRepository.save(dg);
        }

        for (DatasetPath path : pathRepository.findByDataset(dataset)) {
            try (FileInputStream fis = new FileInputStream(new File(globals.launchpadDir, path.getPath()))) {
                storeNewPartOfRawFile(new File(path.getPath()).getName(), ds, fis, false);
            }
            catch (IOException e) {
                log.error("Error while copying part of raw file: " + path.getPath(), e);
                redirectAttributes.addFlashAttribute("errorMessage", "#150.02 Error while copying part of raw file: " + e.toString());
                return "redirect:/launchpad/datasets";
            }
        }

        return "redirect:/launchpad/datasets";
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
        return "launchpad/dataset-column-form";
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
        return "launchpad/dataset-column-form";
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
        return "launchpad/dataset-column-delete";
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
            if (column.getDatasetGroup().getId().equals(group.getId())) {
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
            if (column.getDatasetGroup().getId().equals(group.getId())) {
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
        final Optional<Dataset> value = datasetRepository.findById(datasetId);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = value.get();

        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(datasetId);
        int groupNumber;
        //noinspection ConstantConditions
        groupNumber = groups.isEmpty() ? 1 : groups.stream().mapToInt(DatasetGroup::getGroupNumber).max().getAsInt() + 1;

        final DatasetGroup group = new DatasetGroup(groupNumber);
        group.setFeature(false);
        group.setDataset(dataset);

        dataset.getDatasetGroups().add(group);

        datasetRepository.save(dataset);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-group-add-new-feature/{id}")
    public String addNewFeatureGroup(@PathVariable(name = "id") Long datasetId) {
        final Optional<Dataset> value = datasetRepository.findById(datasetId);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = value.get();

        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(datasetId);
        int groupNumber;
        //noinspection ConstantConditions
        groupNumber = groups.isEmpty() ? 1 : groups.stream().mapToInt(DatasetGroup::getGroupNumber).max().getAsInt() + 1;

        final DatasetGroup group = new DatasetGroup(groupNumber);
        group.setFeature(true);
        group.setDataset(dataset);

        dataset.getDatasetGroups().add(group);

        datasetRepository.save(dataset);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-produce-all-features/{id}")
    public String produceFeaturesForDataset(@PathVariable(name = "id") Long datasetId, final RedirectAttributes redirectAttributes) {
        final Dataset dataset = datasetRepository.findById(datasetId).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#150.11 dataset wasn't found, datasetId: " + datasetId);
            return "redirect:/launchpad/datasets";
        }

        for (DatasetGroup group : dataset.getDatasetGroups()) {
            produceFeature(group);
        }

        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-produce-feature/{id}")
    public String produceFeatureForGroup(@PathVariable(name = "id") Long groupId, final RedirectAttributes redirectAttributes) {
        final DatasetGroup group = groupsRepository.findById(groupId).orElse(null);
        if (group == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#150.12 datasetGroup wasn't found, groupId: " + groupId);
            return "redirect:/launchpad/datasets";
        }
        produceFeature(group);
        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    private void produceFeature(DatasetGroup group) {
        try {
            File yaml = createYamlForFeature(group);
            System.out.println("yaml file: " + yaml.getPath());
            final ProcessService.Result result = runCommand(yaml, group.getCommand(), LogData.Type.FEATURE, group.getId());
            boolean isOk = result.isOk();
            group.setFeatureStatus(isOk ? ArtifactStatus.OK.value : ArtifactStatus.ERROR.value);
            groupsRepository.save(group);
        }
        catch (Exception err) {
            err.printStackTrace();
        }
    }

    private File createYamlForFeature(DatasetGroup group) {

        long datasetId = group.getDataset().getId();

        final String definitionPath = String.format("%s%c%06d", Consts.DATASET_DIR, File.separatorChar, datasetId);
        final File definitionDir = new File(globals.launchpadDir, definitionPath);
        if (!definitionDir.exists()) {
            boolean status = definitionDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + definitionDir.getAbsolutePath());
            }
        }

        final String datasetPath = group.getDataset().asRawFilePath();
        final File rawFile = new File(globals.launchpadDir, datasetPath);
        if (!rawFile.exists()) {
            throw new IllegalStateException("Raw file doesn't exist: " + rawFile.getAbsolutePath());
        }

        final String featurePath = String.format("%s%c%s%c%06d", definitionPath, File.separatorChar, Consts.FEATURE_DIR, File.separatorChar, group.getId());
        final File featureDir = new File(globals.launchpadDir, featurePath);
        if (!featureDir.exists()) {
            boolean status = featureDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + featureDir.getAbsolutePath());
            }
        }

        File yamlFile = new File(featureDir, PRODUCE_FEATURE_YAML);
        File yamlFileBak = new File(featureDir, PRODUCE_FEATURE_YAML + ".bak");
        //noinspection ResultOfMethodCallIgnored
        yamlFileBak.delete();
        if (yamlFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            yamlFile.renameTo(yamlFileBak);
        }


        final String featureFilename = String.format("%s%c" + Consts.FEATURE_FILE_MASK, featurePath, File.separatorChar, group.getId());
        File featureFile = new File(globals.launchpadDir, featureFilename);
        File featureFileBak = new File(globals.launchpadDir, featureFilename + ".bak");

        //noinspection ResultOfMethodCallIgnored
        featureFileBak.delete();
        if (featureFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            featureFile.renameTo(featureFileBak);
        }

        String s = "";
        s += "rawFile: " + datasetPath + '\n';
        s += "featureFile: " + featureFilename + '\n';

        try {
            FileUtils.write(yamlFile, s, Charsets.UTF_8, false);
        }
        catch (IOException e) {
            throw new RuntimeException("error", e);
        }

        return new File(featurePath, PRODUCE_FEATURE_YAML);
    }

    @PostMapping("/dataset-group-cmd-commit")
    public String groupCommandFormCommit(Long id, String command) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        group.setCommand(command);
        groupsRepository.save(group);
        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @PostMapping(value = "/dataset-group-id-group-commit")
    public String setIdGrouppForGroup(Long id, @RequestParam(name = "id_group", required = false, defaultValue = "false") boolean isIdGroup) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        group.setIdGroup(isIdGroup);
        groupsRepository.save(group);

        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @PostMapping(value = "/dataset-group-label-commit")
    public String setLabelForGroup(Long id, boolean label) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        group.setLabel(label);
        groupsRepository.save(group);

        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @PostMapping(value = "/dataset-is-editable-commit")
    public String isEditable(Long id, boolean editable) {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            return "redirect:/launchpad/datasets";
        }
        dataset.setEditable(editable);
        datasetRepository.save(dataset);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @PostMapping(value = "/dataset-group-is-required-commit")
    public String isRequired(Long id, boolean required, final RedirectAttributes redirectAttributes) {
        DatasetGroup group = groupsRepository.findById(id).orElse(null);
        if (group == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#171.01 feature wasn't found, id: " + id);
            return "redirect:/launchpad/datasets";
        }
        group.setRequired(required);
        groupsRepository.save(group);

        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @PostMapping(value = "/dataset-cmd-assemble-commit")
    public String setCmdForRaw(Long id, @RequestParam(name = "command_assemble") String assemblingCommand) {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            return "redirect:/launchpad/datasets";
        }
        dataset.setAssemblingCommand(assemblingCommand);
        dataset.setRawAssemblingStatus(ArtifactStatus.OBSOLETE.value);
        datasetRepository.save(dataset);

        obsoleteDatasetGroups(dataset);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @PostMapping(value = "/dataset-cmd-produce-dataset-commit")
    public String setCmdForDataset(Long id, @RequestParam(name = "command_produce") String cmd) {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            return "redirect:/launchpad/datasets";
        }
        dataset.setProducingCommand(cmd);
        dataset.setDatasetProducingStatus(ArtifactStatus.OBSOLETE.value);
        datasetRepository.save(dataset);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @PostMapping(value = "/dataset-run-assembling-commit")
    public String runAssemblingOfRawFile(Long id) throws IOException {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            return "redirect:/launchpad/datasets";
        }
        File yaml = createConfigYaml(dataset);
        final ProcessService.Result result = runCommand(yaml, dataset.getAssemblingCommand(), LogData.Type.ASSEMBLING, dataset.getId());
        boolean isOk = result.isOk();
        updateInfoWithRaw(dataset, isOk);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    private void updateInfoWithRaw(Dataset dataset, boolean isOk) {
        final String path = dataset.asRawFilePath();
        final File datasetFile = new File(globals.launchpadDir, path);
        if (!datasetFile.exists()) {
            isOk = false;
        }
        dataset.setDatasetProducingStatus(ArtifactStatus.OBSOLETE.value);
        dataset.setRawAssemblingStatus(isOk ? ArtifactStatus.OK.value : ArtifactStatus.ERROR.value);
        datasetRepository.save(dataset);

        obsoleteDatasetGroups(dataset);
    }

    @PostMapping(value = "/dataset-run-producing-commit")
    public String runProducingOfDatasetFile(Long id) throws IOException {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            return "redirect:/launchpad/datasets";
        }
        File yaml = createConfigYaml(dataset);
        final ProcessService.Result result = runCommand(yaml, dataset.getProducingCommand(), LogData.Type.PRODUCING, dataset.getId());
        boolean isOk = result.isOk();
        updateInfoWithDataset(dataset, isOk);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    private void obsoleteDatasetGroups(Dataset dataset) {
        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(dataset.getId());
        for (DatasetGroup group : groups) {
            group.setFeatureStatus(ArtifactStatus.OBSOLETE.value);
        }
        groupsRepository.saveAll(groups);
    }

    private void updateInfoWithDataset(Dataset dataset, boolean isOk) {
        final String path = dataset.asDatasetFilePath();
        File datasetFile = new File(globals.launchpadDir, path);
        int status = isOk ? ArtifactStatus.OK.value : ArtifactStatus.ERROR.value;
        if (!datasetFile.exists()) {
            log.error("Dataset file doesn't exist: {}", datasetFile.getPath());
            status = ArtifactStatus.ERROR.value;
        }
        dataset.setDatasetProducingStatus(status);
        datasetRepository.save(dataset);
    }

    private ProcessService.Result runCommand(File yaml, String command, LogData.Type type, Long refId) {

        // https://examples.javacodegeeks.com/core-java/lang/processbuilder/java-lang-processbuilder-example/
        //
        // java -jar bin\app-assembly-dataset-1.0-SNAPSHOT.jar 6
        try {
            List<String> cmd = Arrays.stream(command.split("\\s+")).collect(Collectors.toList());
            cmd.add(yaml.getPath());
            final File execDir = globals.launchpadDir.getCanonicalFile();

            return processService.execCommand(type, refId, cmd, execDir);

        }
        catch (Exception e) {
            log.error("Error", e);
            return new ProcessService.Result(false, -1, e.getMessage());
        }
    }

    private File createConfigYaml(Dataset dataset) throws IOException {
        final String path = String.format("%s%c%06d", Consts.DATASET_DIR, File.separatorChar, dataset.getId());
        final File datasetDefDir = new File(globals.launchpadDir, path);
        if (!datasetDefDir.exists()) {
            boolean status = datasetDefDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + datasetDefDir.getAbsolutePath());
            }
        }

        File yamlFile = new File(datasetDefDir, CONFIG_YAML);
        File yamlFileBak = new File(datasetDefDir, CONFIG_YAML + ".bak");
        yamlFileBak.delete();
        if (yamlFile.exists()) {
            yamlFile.renameTo(yamlFileBak);
        }


        File datasetDir = DirUtils.createDir(datasetDefDir, "dataset");
        if (datasetDir == null) {
            throw new IllegalStateException("Can't create target dir");
        }
        if (!datasetDir.isDirectory()) {
            throw new IllegalStateException("Not a directory: " + datasetDir.getCanonicalPath());
        }

        File datatsetFile = new File(datasetDir, "dataset.");
        File datatsetFileBak = new File(datasetDir, "dataset.bak");

        datatsetFileBak.delete();
        if (datatsetFile.exists()) {
            datatsetFile.renameTo(datatsetFileBak);
        }

        List<DatasetPath> paths = pathRepository.findByDataset_OrderByPathNumber(dataset);

        DatasetPreparingConfig config = new DatasetPreparingConfig();
        for (DatasetPath datasetPath : paths) {
            config.parts.add(datasetPath.getPath());
        }
        config.datasetFile = String.format("%s%cdataset%c%s", path, File.separatorChar, File.separatorChar, Consts.DATASET_FILE_NAME);
        config.rawFile = String.format("%s%c%s", path, File.separatorChar, Consts.RAW_FILE_NAME);

        try {
            FileUtils.write(yamlFile, DatasetPreparingConfigUtils.toString(config), Charsets.UTF_8, false);
        }
        catch (IOException e) {
            throw new RuntimeException("error", e);
        }

        return new File(path, CONFIG_YAML);
    }

    @PostMapping(value = "/dataset-upload-part-raw-from-file")
    public String createDefinitionFromFile(MultipartFile file, @RequestParam(name = "id") long datasetId, final RedirectAttributes redirectAttributes) {

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#172.01 name of uploaded file is null");
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }
        if (!checkExtension(originFilename)) {
            redirectAttributes.addFlashAttribute("errorMessage", "#172.03 not supported extension, filename: " + originFilename);
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }

        Dataset dataset = datasetRepository.findById(datasetId).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#172.02 dataset wasn't found for id " + datasetId);
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }

        try (InputStream is = file.getInputStream()) {
            storeNewPartOfRawFile(originFilename, dataset, is, true);
        }
        catch (IOException e) {
            log.error("Error", e);
            redirectAttributes.addFlashAttribute("errorMessage", "#172.04 An error while saving data to file, " + e.toString());
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }

        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    private void storeNewPartOfRawFile(String originFilename, Dataset dataset, InputStream is, boolean isUsePrefix) throws IOException {
        List<DatasetPath> paths = pathRepository.findByDataset(dataset);
        //noinspection ConstantConditions
        int pathNumber = paths.isEmpty() ? 1 : paths.stream().mapToInt(DatasetPath::getPathNumber).max().getAsInt() + 1;
        final String path = String.format("%s%c%06d%craws", Consts.DATASET_DIR, File.separatorChar, dataset.getId(), File.separatorChar);

        File datasetDir = new File(globals.launchpadDir, path);
        if (!datasetDir.exists()) {
            boolean status = datasetDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + datasetDir.getAbsolutePath());
            }
        }

        File datasetFile;

        if (isUsePrefix) {
            datasetFile = new File(datasetDir, String.format("raw-%d-%s", pathNumber, originFilename));
        } else {
            datasetFile = new File(datasetDir, originFilename);
        }
        FileUtils.copyInputStreamToFile(is, datasetFile);

        DatasetPath dp = new DatasetPath();
        String pathToDataset = path + File.separatorChar + datasetFile.getName();
        dp.setPath(pathToDataset);
        dp.setChecksum(DatasetChecksum.getChecksumAsJson(datasetFile));
        dp.setDataset(dataset);
        dp.setFile(true);
        dp.setPathNumber(pathNumber);
        dp.setValid(true);
        dp.setRegisterTs(new Timestamp(System.currentTimeMillis()));

        pathRepository.save(dp);
    }

    private void createColumnsDefinition(Dataset dataset, InputStream is) throws IOException {
        try (final InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
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

    @PostMapping("/dataset-definition-form-commit")
    public String datasetDefinitionFormCommit(DatasetDefinition datasetDefinition, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetRepository.findById(datasetDefinition.dataset.getId()).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#180.01 dataset wasn't found, datasetId: " + datasetDefinition.dataset.getId());
            return "redirect:/launchpad/experiments";
        }
        dataset.setName(datasetDefinition.dataset.getName());
        dataset.setDescription(datasetDefinition.dataset.getDescription());
        datasetRepository.save(dataset);
        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @GetMapping("/dataset-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#181.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/experiments";
        }
        model.addAttribute("dataset", dataset);
        return "launchpad/dataset-delete";
    }

    @PostMapping("/dataset-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#182.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/experiments";
        }
        pathRepository.deleteByDataset(dataset);
        datasetRepository.deleteById(id);
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

    private static boolean checkExtension(String filename) {
        int idx;
        if ((idx = filename.lastIndexOf('.')) == -1) {
            return false;
        }
        String ext = filename.substring(idx).toLowerCase();
        return exts.contains(ext);
    }
}
