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
import aiai.ai.beans.*;
import aiai.ai.core.ArtifactStatus;
import aiai.ai.core.ProcessService;
import aiai.ai.repositories.*;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.utils.DirUtils;
import aiai.ai.utils.StrUtils;
import aiai.ai.yaml.config.DatasetPreparingConfig;
import aiai.ai.yaml.config.DatasetPreparingConfigUtils;
import lombok.Data;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
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
public class DatasetController {

    private static final String CONFIG_YAML = "config.yaml";
    private static final String PRODUCE_FEATURE_YAML = "produce-feature.yaml";
    public static final String RAW_FILE_NAME = "raw-file.";

    @Value("#{ T(aiai.ai.utils.EnvProperty).minMax( environment.getProperty('aiai.table.rows.limit'), 5, 30, 5) }")
    private int limit;

    private final Globals globals;

    private final DatasetRepository datasetRepository;
    private final DatasetGroupsRepository groupsRepository;
    private final DatasetColumnRepository columnRepository;
    private final DatasetPathRepository pathRepository;
    private final ProcessService processService;

    public DatasetController(Globals globals, DatasetRepository datasetRepository, DatasetGroupsRepository groupsRepository, DatasetColumnRepository columnRepository, DatasetPathRepository pathRepository, ProcessService processService) {
        this.globals = globals;
        this.datasetRepository = datasetRepository;
        this.groupsRepository = groupsRepository;
        this.columnRepository = columnRepository;
        this.pathRepository = pathRepository;
        this.processService = processService;
    }

    @GetMapping("/datasets")
    public String init(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable, @ModelAttribute("errorMessage") final String errorMessage) {
        pageable = ControllerUtils.fixPageSize(limit, pageable);
        result.items = datasetRepository.findAll(pageable);
        return "launchpad/datasets";
    }

    // for AJAX
    @PostMapping("/datasets-part")
    public String getDatasets(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(limit, pageable);
        result.items = datasetRepository.findAll(pageable);
        return "launchpad/datasets :: table";
    }

    @GetMapping(value = "/dataset-add")
    public String add(Model model) {
        model.addAttribute("dataset", new Dataset());
        return "launchpad/dataset-form";
    }

    @GetMapping(value = "/dataset-edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("dataset", datasetRepository.findById(id));
        return "launchpad/dataset-form";
    }

    @GetMapping(value = "/dataset-definition/{id}")
    public String toDatasetDefinition(@PathVariable(name = "id") Long datasetId, Model model) {
        final Optional<Dataset> datasetOptional = datasetRepository.findById(datasetId);
        if (!datasetOptional.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        final Dataset dataset = datasetOptional.get();

        // path variable is for informing user about directory structure
        final String path = String.format("<Launchpad directory>%c%s%c%03d", File.separatorChar, Consts.DEFINITIONS_DIR, File.separatorChar, dataset.getId());

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

        model.addAttribute("result", definition);
        return "launchpad/dataset-definition";
    }

    @PostMapping(value = "/dataset-clone-commit")
    public String cloneDataset(Long id, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#50.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/datasets";
        }
        Dataset ds = new Dataset();
        ds.setName(StrUtils.incCopyNumber(dataset.getName()));
        ds.setDescription(StrUtils.incCopyNumber(dataset.getDescription()));
        ds.setAssemblingCommand(dataset.getAssemblingCommand());
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

        // TODO !!! 2018.09.08 add here process of copying DatasetPath from source dataset to target

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

    @GetMapping(value = "/dataset-produce-features/{id}")
    public String produceFeaturesForDataset(@PathVariable(name = "id") Long datasetId) {
        final Dataset dataset = datasetRepository.findById(datasetId).orElse(null);
        if (dataset==null) {
            return "redirect:/launchpad/datasets";
        }

        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(datasetId);
        for (DatasetGroup group : groups) {
            produceFeature(dataset, group);
        }

        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-produce-feature/{id}")
    public String produceFeatureForGroup(@PathVariable(name = "id") Long groupId) {
        final DatasetGroup group = groupsRepository.findById(groupId).orElse(null);
        if (group==null) {
            return "redirect:/launchpad/datasets";
        }
        produceFeature(group.getDataset(), group);
        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    private void produceFeature(Dataset dataset, DatasetGroup group) {
        try {
            File yaml = createYamlForFeature(dataset.getId(), group);
            System.out.println("yaml file: " + yaml.getPath());
            boolean isOk = runCommand(yaml, group.getCommand(), LogData.Type.FEATURE, group.getId());
            group.setFeatureStatus(isOk ? ArtifactStatus.OK.value: ArtifactStatus.ERROR.value );
            groupsRepository.save(group);
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private File createYamlForFeature(Long datasetId, DatasetGroup group) {

        final String definitionPath = String.format("%s%c%03d", Consts.DEFINITIONS_DIR, File.separatorChar, datasetId);
        final File definitionDir = new File(globals.launchpadDir, definitionPath);
        if (!definitionDir.exists()) {
            boolean status = definitionDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + definitionDir.getAbsolutePath());
            }
        }

        final String datasetPath = String.format("%s%cdataset%c%s", definitionPath, File.separatorChar, File.separatorChar, Consts.DATASET_TXT);
        final File datasetFile = new File(globals.launchpadDir, datasetPath);
        if (!datasetFile.exists()) {
            throw new IllegalStateException("Dataset file doesn't exist: " + datasetFile.getAbsolutePath());
        }

        final String featurePath = String.format("%s%c%s%c%03d", definitionPath, File.separatorChar, Consts.FEATURE_DIR, File.separatorChar, group.getId());
        final File featureDir = new File(globals.launchpadDir, featurePath);
        if (!featureDir.exists()) {
            boolean status = featureDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + featureDir.getAbsolutePath());
            }
        }

        File yamlFile = new File(featureDir, PRODUCE_FEATURE_YAML);
        File yamlFileBak = new File(featureDir, PRODUCE_FEATURE_YAML + ".bak");
        yamlFileBak.delete();
        if (yamlFile.exists()) {
            yamlFile.renameTo(yamlFileBak);
        }


        final String featureFilename = String.format("%s%cfeature-%03d.", featurePath, File.separatorChar, group.getId());
        File featureFile = new File(globals.launchpadDir, featureFilename);
        File featureFileBak = new File(globals.launchpadDir, featureFilename + ".bak");

        featureFileBak.delete();
        if (featureFile.exists()) {
            featureFile.renameTo(featureFileBak);
        }

/*
        dataset:
            file: definitions\002\dataset\dataset.txt
        feature
            file: definitions\002\features\003\feature-003.txt
*/

        String s = "";
        s += "dataset:\n    file: " +  datasetPath + '\n';
        s += "feature:\n    file: " + featureFilename + '\n';

        try {
            FileUtils.write(yamlFile, s, Charsets.UTF_8, false);
        } catch (IOException e) {
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
    public String setHeaderForDataset(Long id, boolean editable) {
        final Optional<Dataset> value = datasetRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = value.get();
        dataset.setEditable(editable);
        datasetRepository.save(dataset);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @PostMapping(value = "/dataset-cmd-assemble-commit")
    public String setHeaderForDataset(Long id, @RequestParam(name = "command_assemble") String assemblingCommand) {
        final Optional<Dataset> value = datasetRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = value.get();
        dataset.setAssemblingCommand(assemblingCommand);
        datasetRepository.save(dataset);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @PostMapping(value = "/dataset-run-assembling-commit")
    public String runAssemblingOfRawFile(Long id) throws IOException {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset==null) {
            return "redirect:/launchpad/datasets";
        }
        File yaml = createConfigYaml(dataset);
        runCommand(yaml, dataset.getAssemblingCommand(), LogData.Type.ASSEMBLING, dataset.getId());
        updateInfoWithRaw(dataset);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    private void updateInfoWithRaw(Dataset dataset) {
        final String path = String.format("%s%c%03d", Consts.DEFINITIONS_DIR, File.separatorChar, dataset.getId());

        final File datasetDefDir = new File(globals.launchpadDir, path);
        if (!datasetDefDir.exists()) {
            return;
        }

        String rawFilename = String.format("%s%c%s", path, File.separatorChar, RAW_FILE_NAME);

        File datasetFile = new File(globals.launchpadDir, rawFilename);
        if (!datasetFile.exists()) {
            return;
        }
        dataset.setRawFile(rawFilename);
        dataset.setDatasetProducingStatus(ArtifactStatus.OBSOLETE.value);
        datasetRepository.save(dataset);

        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(dataset.getId());
        for (DatasetGroup group : groups) {
            group.setFeatureStatus(ArtifactStatus.OBSOLETE.value );
        }
        groupsRepository.saveAll(groups);
    }

    private void updateInfoWithDataset(Dataset dataset) {
        final String path = String.format("%s%c%03d", Consts.DEFINITIONS_DIR, File.separatorChar, dataset.getId());

        final File datasetDefDir = new File(globals.launchpadDir, path);
        if (!datasetDefDir.exists()) {
            return;
        }

        String datasetFilename = String.format("%s%cdataset%cdataset.", path, File.separatorChar, File.separatorChar);

        File datasetFile = new File(globals.launchpadDir, datasetFilename);
        if (!datasetFile.exists()) {
            return;
        }
        dataset.setDatasetFile(datasetFilename);
        datasetRepository.save(dataset);

        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(dataset.getId());
        for (DatasetGroup group : groups) {
            group.setFeatureStatus(ArtifactStatus.OBSOLETE.value );
        }
        groupsRepository.saveAll(groups);
    }

    private boolean runCommand(File yaml, String command, LogData.Type type, Long refId) {

        // https://examples.javacodegeeks.com/core-java/lang/processbuilder/java-lang-processbuilder-example/
        //
        // java -jar bin\app-assembly-dataset-1.0-SNAPSHOT.jar 6
        try {
            List<String> cmd = Arrays.stream(command.split("\\s+")).collect(Collectors.toList());
            cmd.add(yaml.getPath());
            final File execDir = globals.launchpadDir.getCanonicalFile();

            return processService.execCommand(type, refId, cmd, execDir).isOk();

        } catch (Exception err) {
            err.printStackTrace();
            return false;
        }
    }

    private File createConfigYaml(Dataset dataset) throws IOException {
        final String path = String.format("%s%c%03d", Consts.DEFINITIONS_DIR, File.separatorChar, dataset.getId());
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
        if (datasetDir==null) {
            throw new IllegalStateException("Can't create target dir");
        }
        if (datasetDir.isDirectory()) {
            throw new IllegalStateException("Not a directory: " + datasetDir.getCanonicalPath());
        }

        File datatsetFile = new File(datasetDir, "dataset.");
        File datatsetFileBak = new File(datasetDir, "dataset.bak");

        datatsetFileBak.delete();
        if (datatsetFile.exists()) {
            datatsetFile.renameTo(datatsetFileBak);
        }

        List<DatasetPath> paths = pathRepository.findByDataset_OrderByPathNumber(dataset);

        DatasetPreparingConfig config =  new DatasetPreparingConfig();
        for (DatasetPath datasetPath : paths) {
            config.parts.add(datasetPath.getPath());
        }
        config.datasetFile = String.format("%s%cdataset%cdataset", path, File.separatorChar, File.separatorChar);
        config.rawFile = String.format("%s%c%s", path, File.separatorChar, RAW_FILE_NAME);

        try {
            FileUtils.write(yamlFile, DatasetPreparingConfigUtils.toString(config), Charsets.UTF_8, false);
        } catch (IOException e) {
            throw new RuntimeException("error", e);
        }

        return new File(path, CONFIG_YAML);
    }

    private static final Set<String> exts;
    static {
        exts = new HashSet<>();
        Collections.addAll(exts, ".json", ".csv", ".txt", ".xml", ".yaml");
    }

    private static boolean checkExtension(String filename) {
        int idx;
        if ((idx = filename.lastIndexOf('.')) == -1) {
            throw new IllegalStateException("'.' wasn't found, bad filename: " + filename);
        }
        String ext = filename.substring(idx).toLowerCase();
        return exts.contains(ext);
    }

    @PostMapping(value = "/dataset-upload-part-raw-from-file")
    public String createDefinitionFromFile(MultipartFile file, @RequestParam(name = "id") long datasetId) {

        String originFilename = file.getOriginalFilename();
        if (originFilename==null) {
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }
        if (!checkExtension(originFilename)) {
            throw new IllegalStateException("Not supported extension, filename: " + originFilename);
        }

        Optional<Dataset> optionalDataset = datasetRepository.findById(datasetId);
        if (!optionalDataset.isPresent()) {
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }
        Dataset dataset = optionalDataset.get();

        List<DatasetPath> paths = pathRepository.findByDataset(dataset);
        //noinspection ConstantConditions
        int pathNumber = paths.isEmpty() ? 1 : paths.stream().mapToInt(DatasetPath::getPathNumber).max().getAsInt() + 1;
        final String path = String.format("%s%c%03d%craws", Consts.DEFINITIONS_DIR, File.separatorChar, dataset.getId(), File.separatorChar);

        File datasetDir = new File(globals.launchpadDir, path);
        if (!datasetDir.exists()) {
            boolean status = datasetDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + datasetDir.getAbsolutePath());
            }
        }

        File datasetFile;
        try (InputStream is = file.getInputStream()) {
            datasetFile = new File(datasetDir, String.format("raw-%d-%s", pathNumber, originFilename));
            FileUtils.copyInputStreamToFile(is, datasetFile);
        } catch (IOException e) {
            throw new RuntimeException("error", e);
        }

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

        return "redirect:/launchpad/dataset-definition/" + datasetId;
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

    @PostMapping("/dataset-form-commit")
    public String datasetFormCommit(Dataset dataset) {
        dataset.setEditable(true);
        datasetRepository.save(dataset);
        return "redirect:/launchpad/datasets";
    }

    @PostMapping("/dataset-definition-form-commit")
    public String datasetDefinitionFormCommit(DatasetDefinition datasetDefinition) {
        datasetRepository.save(datasetDefinition.dataset);
        return "redirect:/launchpad/dataset-definition/" + datasetDefinition.dataset.getId();
    }

    @GetMapping("/dataset-delete/{id}")
    public String delete(@PathVariable Long id, Model model) {
        final Optional<Dataset> value = datasetRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        model.addAttribute("dataset", value.get());
        return "launchpad/dataset-delete";
    }

    @PostMapping("/dataset-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetRepository.findById(id).orElse(null);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#82.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/experiments";
        }
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

        public DatasetDefinition(Dataset dataset, String launchpadDirAsString, String datasetDirAsString) {
            this.dataset = dataset;
            this.launchpadDirAsString = launchpadDirAsString;
            this.datasetDirAsString = datasetDirAsString;
        }
    }
}
