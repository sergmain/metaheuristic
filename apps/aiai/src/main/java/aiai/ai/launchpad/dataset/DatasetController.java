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
import aiai.ai.core.ArtifactStatus;
import aiai.ai.core.ProcessService;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.env.EnvService;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.snippet.SnippetCode;
import aiai.ai.snippet.SnippetUtils;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.utils.SimpleSelectOption;
import aiai.ai.utils.StrUtils;
import aiai.ai.yaml.config.DatasetPreparingConfig;
import aiai.ai.yaml.config.DatasetPreparingConfigUtils;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.yaml.snippet.SnippetType;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
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
    public static class ExtendedDefinitionResult {
        public boolean isStoreToDisk;
    }

    @Data
    public static class Result {
        public Slice<Dataset> items;
    }

    @Data
    @ToString(exclude = {"dataset"})
    public static class DatasetDefinition {
        public Dataset dataset;
        public List<DatasetPath> paths = new ArrayList<>();
        public String launchpadDirAsString;
        public String datasetDirAsString;
        public List<SimpleSelectOption> assemblyOptions;
        public List<SimpleSelectOption> datasetOptions;
        public Map<String, Env> envs = new HashMap<>();

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
    private final DatasetPathRepository pathRepository;
    private final ProcessService processService;
    private final SnippetService snippetService;
    private final SnippetBaseRepository snippetBaseRepository;
    private final EnvService envService;
    private final DatasetCache datasetCache;
    private final BinaryDataService binaryDataService;
    private BinaryDataRepository binaryDataRepository;

    public DatasetController(Globals globals, DatasetRepository datasetRepository, DatasetGroupsRepository groupsRepository, DatasetPathRepository pathRepository, ProcessService processService, SnippetService snippetService, SnippetBaseRepository snippetBaseRepository, EnvService envService, DatasetCache datasetCache, BinaryDataService binaryDataService, BinaryDataRepository binaryDataRepository) {
        this.globals = globals;
        this.datasetRepository = datasetRepository;
        this.groupsRepository = groupsRepository;
        this.pathRepository = pathRepository;
        this.processService = processService;
        this.snippetService = snippetService;
        this.snippetBaseRepository = snippetBaseRepository;
        this.envService = envService;
        this.datasetCache = datasetCache;
        this.binaryDataService = binaryDataService;
        this.binaryDataRepository = binaryDataRepository;
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
        Dataset dataset = datasetCache.findById(id);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#173.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/datasets";
        }
        model.addAttribute("dataset", dataset);
        return "launchpad/dataset-form";
    }

    @PostMapping("/dataset-form-commit")
    public String datasetFormCommit(Dataset dataset, final RedirectAttributes redirectAttributes) {
        Dataset ds;
        if (dataset.getId()==null) {
            // we've just created new dataset
            ds = new Dataset();
        }
        else {
            ds = datasetCache.findById(dataset.getId());
            if (ds == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "#173.02 dataset wasn't found, datasetId: " + dataset.getId());
                return "redirect:/launchpad/datasets";
            }
        }
        ds.setName(dataset.getName());
        ds.setDescription(dataset.getDescription());
        ds.setEditable(true);
        datasetCache.save(ds);
        return "redirect:/launchpad/datasets";
    }

    @GetMapping(value = "/dataset-definition/{id}")
    public String toDatasetDefinition(@PathVariable(name = "id") Long datasetId, Model model, @ModelAttribute("errorMessage") final String errorMessage, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetCache.findById(datasetId);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#174.01 dataset wasn't found, datasetId: " + datasetId);
            return "redirect:/launchpad/datasets";
        }

        // path variable is for informing user about directory structure
        final String path = String.format("<Launchpad directory>%c%s%c%06d", File.separatorChar, Consts.DATASET_DIR, File.separatorChar, dataset.getId());

        final DatasetDefinition definition = new DatasetDefinition(dataset, globals.launchpadDir.getPath(), path);
        definition.paths = pathRepository.findByDataset_OrderByPathNumber(dataset);

        final Iterable<SnippetBase> snippets = snippetBaseRepository.findAll();

        final List<SnippetCode> featureCodes = new ArrayList<>();

        // fix conditions for UI
        final int groupSize = dataset.getDatasetGroups().size();
        for (int i = 0; i < groupSize; i++) {
            DatasetGroup group = dataset.getDatasetGroups().get(i);
            group.setAddColumn(true);
            if (group.getSnippet()!=null) {
                featureCodes.add(new SnippetCode(group.getSnippet().getId(), group.getSnippet().getSnippetCode()));
            }
        }

        // ugly but it works
        for (DatasetGroup group : dataset.getDatasetGroups()) {
            group.featureOptions = snippetService.getSelectOptions(snippets, featureCodes, (s) -> SnippetType.feature!=(SnippetType.valueOf(s.type)));
        }

        final List<SnippetCode> assemblyCodes = dataset.getAssemblySnippet() == null ? new ArrayList<>() : Collections.singletonList(new SnippetCode(dataset.getAssemblySnippet().getId(), dataset.getAssemblySnippet().getSnippetCode()));
        definition.assemblyOptions = snippetService.getSelectOptions(snippets, assemblyCodes, (s) -> SnippetType.assembly!=(SnippetType.valueOf(s.type)));

        final List<SnippetCode> datasetCodes = dataset.getDatasetSnippet() == null ? new ArrayList<>() : Collections.singletonList(new SnippetCode(dataset.getDatasetSnippet().getId(), dataset.getDatasetSnippet().getSnippetCode()));
        definition.datasetOptions = snippetService.getSelectOptions(snippets, datasetCodes, (s) -> SnippetType.dataset!=(SnippetType.valueOf(s.type)));

        definition.envs.putAll( envService.envsAsMap() );

        ExtendedDefinitionResult extendedDefinitionResult = new ExtendedDefinitionResult();
        extendedDefinitionResult.setStoreToDisk(globals.isStoreDataToDisk());

        model.addAttribute("result", definition);
        model.addAttribute("extendedResult", extendedDefinitionResult);
        return "launchpad/dataset-definition";
    }

    @PostMapping("/dataset-snippet-assembly-commit/{id}")
    public String snippetAssemblyCommit(@PathVariable Long id, String code, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetCache.findById(id);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#175.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/datasets";
        }
        SnippetVersion snippetVersion = SnippetVersion.from(code);
        SnippetBase snippet = snippetBaseRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        if (snippet==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#176.01 snippet "+code+" wasn't found");
            return "redirect:/launchpad/dataset-definition/" + dataset.getId();
        }
        dataset.setAssemblySnippet(snippet);
        datasetCache.save(dataset);
        return "redirect:/launchpad/dataset-definition/"+id;
    }

    @PostMapping("/dataset-snippet-dataset-commit/{id}")
    public String snippetDatasetCommit(@PathVariable Long id, String code, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetCache.findById(id);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#177.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/datasets";
        }
        SnippetVersion snippetVersion = SnippetVersion.from(code);
        SnippetBase snippet = snippetBaseRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        if (snippet==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#178.01 snippet "+code+" wasn't found");
            return "redirect:/launchpad/dataset-definition/" + dataset.getId();
        }
        dataset.setDatasetSnippet(snippet);
        datasetCache.save(dataset);
        return "redirect:/launchpad/dataset-definition/"+id;
    }

    @PostMapping("/dataset-group-snippet-commit/{id}")
    public String snippetGroupCommit(@PathVariable Long id, String code, final RedirectAttributes redirectAttributes) {
        final DatasetGroup group = groupsRepository.findById(id).orElse(null);
        if (group==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#180.01 dataset group wasn't found, datasetGroupId: " + id);
            return "redirect:/launchpad/datasets";
        }
        SnippetVersion snippetVersion = SnippetVersion.from(code);
        SnippetBase snippet = snippetBaseRepository.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
        if (snippet==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#182.01 snippet "+code+" wasn't found");
            return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
        }
        group.setSnippet(snippet);
        datasetCache.saveGroup(group);
        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @PostMapping(value = "/dataset-clone-commit")
    public String cloneDataset(Long id, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetCache.findById(id);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#150.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/datasets";
        }
        try {
            Dataset ds = new Dataset();
            ds.setName(StrUtils.incCopyNumber(dataset.getName()));
            ds.setDescription(dataset.getDescription());
            ds.setAssemblySnippet(dataset.getAssemblySnippet());
            ds.setDatasetSnippet(dataset.getDatasetSnippet());
            ds.setEditable(true);
            ds.setLocked(false);
            ds.setDatasetGroups(new ArrayList<>());
            ds.setLength(dataset.getLength());
            datasetCache.save(ds);
            binaryDataService.cloneBinaryData(dataset.getId(), ds.getId(), BinaryData.Type.DATASET);

            for (DatasetGroup datasetGroup : dataset.getDatasetGroups()) {
                DatasetGroup dg = new DatasetGroup();
                BeanUtils.copyProperties(datasetGroup, dg);
                dg.setId(null);
                dg.setVersion(null);
                dg.setFeatureStatus(ArtifactStatus.NONE.value);
                dg.setDataset(ds);
                datasetCache.saveGroup(dg);
            }

            for (DatasetPath path : pathRepository.findByDataset(dataset)) {
                File file = new File(globals.launchpadDir, path.getPath());
                try {
                    storeNewPartOfRawFile(new File(path.getPath()).getName(), ds, file, false);
                }
                catch (IOException e) {
                    log.error("Error while copying part of raw file: " + file.getPath(), e);
                    redirectAttributes.addFlashAttribute("errorMessage", "#150.02 Error while copying part of raw file: " + e.toString());
                    return "redirect:/launchpad/datasets";
                }
            }
        } catch (SQLException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "#150.05 Error cloning binaryData, error: " + e.toString());
            return "redirect:/launchpad/datasets";
        }

        return "redirect:/launchpad/datasets";
    }

    @GetMapping(value = "/dataset-delete-group/{id}")
    public String deleteGroup(@PathVariable Long id) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        long datasetId = group.getDataset().getId();

        datasetCache.delete(group);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-group-add-new/{id}")
    public String addNewGroup(@PathVariable(name = "id") Long datasetId) {
        Dataset dataset = datasetCache.findById(datasetId);
        if (dataset==null) {
            return "redirect:/launchpad/datasets";
        }

        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(datasetId);
        int groupNumber;
        //noinspection ConstantConditions
        groupNumber = groups.isEmpty() ? 1 : groups.stream().mapToInt(DatasetGroup::getGroupNumber).max().getAsInt() + 1;

        final DatasetGroup group = new DatasetGroup(groupNumber);
        group.setFeature(false);
        group.setDataset(dataset);

        dataset.getDatasetGroups().add(group);

        datasetCache.save(dataset);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-group-add-new-feature/{id}")
    public String addNewFeatureGroup(@PathVariable(name = "id") Long datasetId) {
        Dataset dataset = datasetCache.findById(datasetId);
        if (dataset==null) {
            return "redirect:/launchpad/datasets";
        }

        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(datasetId);
        int groupNumber;
        //noinspection ConstantConditions
        groupNumber = groups.isEmpty() ? 1 : groups.stream().mapToInt(DatasetGroup::getGroupNumber).max().getAsInt() + 1;

        final DatasetGroup group = new DatasetGroup(groupNumber);
        group.setFeature(true);
        group.setDataset(dataset);

        dataset.getDatasetGroups().add(group);

        datasetCache.save(dataset);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-produce-feature/{id}")
    public String produceFeatureForGroup(@PathVariable(name = "id") Long groupId, final RedirectAttributes redirectAttributes) {
        final DatasetGroup group = groupsRepository.findById(groupId).orElse(null);
        if (group == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#150.01 datasetGroup wasn't found, groupId: " + groupId);
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = group.getDataset();
        Long datasetId = dataset.getId();
        try {
            Env env = envService.envsAsMap().get(group.getSnippet().getEnv());
            if (env == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "#153.01 Environment definition wasn't found for feature snippet. Requested environment: " + group.getSnippet().getEnv());
                return "redirect:/launchpad/dataset-definition/" + datasetId;
            }

            final SnippetBase snippet = group.getSnippet();
            final File snippetDir = new File(globals.launchpadDir, Consts.SNIPPET_DIR);
            final ConfigForFeature configForFeature = createYamlForFeature(group);
            if (!globals.isStoreDataToDisk()) {
                snippetService.persistSnippet(snippet.getSnippetCode());
                File datasetFile = new File(globals.launchpadDir, dataset.asDatasetFilePath());
                if (datasetFile.exists()) {
                    datasetFile.delete();
                }
                binaryDataService.storeToFile(datasetId, BinaryData.Type.DATASET, datasetFile);

                File rawFile = new File(globals.launchpadDir, dataset.asRawFilePath());
                if (rawFile.exists()) {
                    rawFile.delete();
                }
                binaryDataService.storeToFile(datasetId, BinaryData.Type.ASSEMBLED_RAW, rawFile);
            }
            final SnippetUtils.SnippetFile snippetFile = SnippetUtils.getSnippetFile(snippetDir, snippet.getSnippetCode(), snippet.filename);
            if (!snippetFile.file.exists()) {
                redirectAttributes.addFlashAttribute("errorMessage", "#155.01 Dataset's feature producer isn't specified");
                return "redirect:/launchpad/dataset-definition/" + datasetId;
            }
            String cmdLine = env.value+' '+ snippetFile.file.getAbsolutePath()+' '+ snippet.params;

            File yaml = configForFeature.yamlFile;
            System.out.println("yaml file: " + yaml.getPath());
            final ProcessService.Result result = runCommand(yaml, cmdLine, LogData.Type.FEATURE, group.getId());
            boolean isOk = result.isOk();
            updateInfoWithDatasetGroup(configForFeature, group, isOk);
            if (!isOk) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "#155.02 Error executing of feature producer. See logs for more info");
            }
        }
        catch (Exception e) {
            log.error("Error producing feature", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#155.03 Error producing feature: " + e.toString());
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    private void updateInfoWithDatasetGroup(ConfigForFeature configForFeature, DatasetGroup group, boolean isOk) throws IOException {
        int status = isOk ? ArtifactStatus.OK.value : ArtifactStatus.ERROR.value;
        if (!configForFeature.featureFile.exists()) {
            log.error("Feature file doesn't exist: {}", configForFeature.featureFile.getPath());
            status = ArtifactStatus.ERROR.value;
        }
        else {
            group.setLength(configForFeature.featureFile.length());
        }
        group.setFeatureStatus(status);
        datasetCache.saveGroup(group);

        if (group.getFeatureStatus()==ArtifactStatus.OK.value && globals.isStoreDataToDb()) {
            try (InputStream is = new FileInputStream(configForFeature.featureFile)) {
                binaryDataService.save(is, configForFeature.featureFile.length(), group.getId(), BinaryData.Type.FEATURE);
            }
        }
    }

    @Data
    @AllArgsConstructor
    private static class ConfigForFeature {
        String rawFilePath;
        File rawFile;
        String featureFilePath;
        File featureFile;
        File yamlFile;
    }

    private ConfigForFeature createYamlForFeature(DatasetGroup group) {

        long datasetId = group.getDataset().getId();

        final String definitionPath = String.format("%s%c%06d", Consts.DATASET_DIR, File.separatorChar, datasetId);
        final File definitionDir = new File(globals.launchpadDir, definitionPath);
        if (!definitionDir.exists()) {
            boolean status = definitionDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + definitionDir.getAbsolutePath());
            }
        }

        final String rawFilePath = group.getDataset().asRawFilePath();
        final File rawFile = new File(globals.launchpadDir, rawFilePath);
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
        s += "rawFile: " + rawFilePath + '\n';
        s += "featureFile: " + featureFilename + '\n';

        try {
            FileUtils.write(yamlFile, s, Charsets.UTF_8, false);
        }
        catch (IOException e) {
            throw new RuntimeException("error", e);
        }

        return new ConfigForFeature(rawFilePath, rawFile, featureFilename, featureFile, new File(featurePath, PRODUCE_FEATURE_YAML));
    }

    @PostMapping(value = "/dataset-group-id-group-commit")
    public String setIdGrouppForGroup(Long id, @RequestParam(name = "id_group", required = false, defaultValue = "false") boolean isIdGroup) {
        final Optional<DatasetGroup> value = groupsRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        DatasetGroup group = value.get();
        group.setIdGroup(isIdGroup);
//        groupsRepository.save(group);
        datasetCache.saveGroup(group);

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
//        groupsRepository.save(group);
        datasetCache.saveGroup(group);

        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @PostMapping(value = "/dataset-is-editable-commit")
    public String isEditable(Long id, boolean editable) {
        Dataset dataset = datasetCache.findById(id);
        if (dataset == null) {
            return "redirect:/launchpad/datasets";
        }
        dataset.setEditable(editable);
        datasetCache.save(dataset);

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
        datasetCache.saveGroup(group);

        return "redirect:/launchpad/dataset-definition/" + group.getDataset().getId();
    }

    @PostMapping(value = "/dataset-run-assembling-commit")
    public String runAssemblingOfRawFile(Long id, final RedirectAttributes redirectAttributes) throws IOException {
        Dataset dataset = datasetCache.findById(id);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#101.01 Dataset wasn't found, id: " + id);
            return "redirect:/launchpad/datasets";
        }
        if (dataset.getAssemblySnippet() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#102.01 Assembly snippet isn't specified");
            return "redirect:/launchpad/dataset-definition/" + dataset.getId();
        }
        Env env = envService.envsAsMap().get(dataset.getAssemblySnippet().getEnv());
        if (env == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#103.01 Environment definition wasn't found for assembly snippet. Requested environment: " + dataset.getAssemblySnippet().getEnv());
            return "redirect:/launchpad/dataset-definition/" + dataset.getId();
        }

        File snippetDir = new File(globals.launchpadDir, Consts.SNIPPET_DIR);
        if (!globals.isStoreDataToDisk()) {
            snippetService.persistSnippet(dataset.getAssemblySnippet().getSnippetCode());
            List<DatasetPath> paths = pathRepository.findByDataset(dataset);
            for (DatasetPath path : paths) {
                File rawPartFile = new File(globals.launchpadDir, path.getPath());
                binaryDataService.storeToFile(path.getId(), BinaryData.Type.RAW_PART, rawPartFile);
            }
        }
        SnippetUtils.SnippetFile snippetFile = SnippetUtils.getSnippetFile(snippetDir, dataset.getAssemblySnippet().getSnippetCode(), dataset.getAssemblySnippet().filename);
        if (!snippetFile.file.exists()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#104.01 Assembly snippet wasn't found");
            return "redirect:/launchpad/dataset-definition/" + dataset.getId();
        }
        String cmdLine = env.value+' '+ snippetFile.file.getAbsolutePath()+' '+dataset.getAssemblySnippet().params;

        final File yaml = createConfigYaml(dataset);
        final ProcessService.Result result = runCommand(yaml, cmdLine, LogData.Type.ASSEMBLING, dataset.getId());
        boolean isOk = result.isOk();
        updateInfoWithRaw(dataset, isOk);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    private void updateInfoWithRaw(Dataset dataset, boolean isOk) throws IOException {
        final String path = dataset.asRawFilePath();
        final File rawFile = new File(globals.launchpadDir, path);
        if (!rawFile.exists()) {
            isOk = false;
        }
        dataset.setDatasetProducingStatus(ArtifactStatus.OBSOLETE.value);
        dataset.setRawAssemblingStatus(isOk ? ArtifactStatus.OK.value : ArtifactStatus.ERROR.value);
        datasetCache.save(dataset);

        if (dataset.getRawAssemblingStatus()==ArtifactStatus.OK.value && globals.isStoreDataToDb()) {
            try (InputStream is = new FileInputStream(rawFile)) {
                binaryDataService.save(is, rawFile.length(), dataset.getId(), BinaryData.Type.ASSEMBLED_RAW);
            }
        }

        obsoleteDatasetGroups(dataset);
    }

    @PostMapping(value = "/dataset-run-producing-commit")
    public String runProducingOfDatasetFile(Long id, final RedirectAttributes redirectAttributes) throws IOException {
        Dataset dataset = datasetCache.findById(id);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#190.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/datasets";
        }

        Env env = envService.envsAsMap().get(dataset.getAssemblySnippet().getEnv());
        if (env == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#103.01 Environment definition wasn't found for assembly snippet. Requested environment: " + dataset.getAssemblySnippet().getEnv());
            return "redirect:/launchpad/dataset-definition/" + dataset.getId();
        }

        final String es = "#191.01 Dataset producing snippet isn't specified";
        final SnippetBase snippet = dataset.getDatasetSnippet();
        final File snippetDir = new File(globals.launchpadDir, Consts.SNIPPET_DIR);
        if (!globals.isStoreDataToDisk()) {
            snippetService.persistSnippet(dataset.getDatasetSnippet().getSnippetCode());
            File rawFile = new File(globals.launchpadDir, dataset.asRawFilePath());
            if (rawFile.exists()) {
                rawFile.delete();
            }
            binaryDataService.storeToFile(dataset.getId(), BinaryData.Type.ASSEMBLED_RAW, rawFile);
        }
        final SnippetUtils.SnippetFile snippetFile = SnippetUtils.getSnippetFile(snippetDir, snippet.getSnippetCode(), snippet.filename);
        if (!snippetFile.file.exists()) {
            redirectAttributes.addFlashAttribute("errorMessage", es);
            return "redirect:/launchpad/dataset-definition/" + dataset.getId();
        }
        String cmdLine;
        cmdLine = env.value+' '+ snippetFile.file.getAbsolutePath()+' '+ snippet.params;

        File yaml = createConfigYaml(dataset);
        final ProcessService.Result result = runCommand(yaml, cmdLine, LogData.Type.PRODUCING, dataset.getId());
        boolean isOk = result.isOk();
        updateInfoWithDataset(dataset, isOk);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    private void obsoleteDatasetGroups(Dataset dataset) {
        List<DatasetGroup> groups = groupsRepository.findByDataset_Id(dataset.getId());
        for (DatasetGroup group : groups) {
            group.setFeatureStatus(ArtifactStatus.OBSOLETE.value);
        }
        datasetCache.saveAllGroups(groups, dataset.getId());
    }

    private void updateInfoWithDataset(Dataset dataset, boolean isOk) throws IOException {
        final String path = dataset.asDatasetFilePath();
        File datasetFile = new File(globals.launchpadDir, path);
        int status = isOk ? ArtifactStatus.OK.value : ArtifactStatus.ERROR.value;
        if (!datasetFile.exists()) {
            log.error("Dataset file doesn't exist: {}", datasetFile.getPath());
            status = ArtifactStatus.ERROR.value;
        }
        else {
            dataset.setLength(datasetFile.length());
        }
        dataset.setDatasetProducingStatus(status);
        datasetCache.save(dataset);

        if (dataset.getRawAssemblingStatus()==ArtifactStatus.OK.value && globals.isStoreDataToDb()) {
            try (InputStream is = new FileInputStream(datasetFile)) {
                binaryDataService.save(is, datasetFile.length(), dataset.getId(), BinaryData.Type.DATASET);
            }
        }

        obsoleteDatasetGroups(dataset);
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

    private static final Random r = new Random();
    @PostMapping(value = "/dataset-upload-part-raw-from-file")
    public String createDefinitionFromFile(MultipartFile file, @RequestParam(name = "id") long datasetId, final RedirectAttributes redirectAttributes) {
        File tempFile = globals.createTempFileForLaunchpad("temp-raw-file-");
        if (tempFile.exists()) {
            tempFile.delete();
        }
        try {
            FileUtils.copyInputStreamToFile(file.getInputStream(), tempFile);
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#173.06 can't persist uploaded file as " +
                            tempFile.getAbsolutePath()+", error: " + e.toString());
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#172.01 name of uploaded file is null");
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }
        if (!checkExtension(originFilename)) {
            redirectAttributes.addFlashAttribute("errorMessage", "#172.03 not supported extension, filename: " + originFilename);
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }


        Dataset dataset = datasetCache.findById(datasetId);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#172.02 dataset wasn't found for id " + datasetId);
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }

        try {
            storeNewPartOfRawFile(originFilename, dataset, tempFile, true);
        } catch (IOException e) {
            log.error("Error", e);
            redirectAttributes.addFlashAttribute("errorMessage", "#172.04 An error while saving data to file, " + e.toString());
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    private void storeNewPartOfRawFile(String originFilename, Dataset dataset, File tempFile, boolean isUsePrefix) throws IOException {
        List<DatasetPath> paths = pathRepository.findByDataset(dataset);

        //noinspection ConstantConditions
        final String path = String.format("%s%c%06d%craws", Consts.DATASET_DIR, File.separatorChar, dataset.getId(), File.separatorChar);

        File datasetDir = new File(globals.launchpadDir, path);
        if (!datasetDir.exists()) {
            boolean status = datasetDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + datasetDir.getAbsolutePath());
            }
        }

        String checksumAsJson = DatasetChecksum.getChecksumAsJson(tempFile);

        int pathNumber = paths.isEmpty() ? 1 : paths.stream().mapToInt(DatasetPath::getPathNumber).max().getAsInt() + 1;
        File datasetFile;
        if (isUsePrefix) {
            datasetFile = new File(datasetDir, String.format("raw-%d-%s", pathNumber, originFilename));
        } else {
            datasetFile = new File(datasetDir, originFilename);
        }

        DatasetPath dp = new DatasetPath();
        String pathToDataset = path + File.separatorChar + datasetFile.getName();
        dp.setPath(pathToDataset);
        dp.setChecksum(checksumAsJson);
        dp.setDataset(dataset);
        dp.setFile(true);
        dp.setPathNumber(pathNumber);
        dp.setValid(true);
        dp.setRegisterTs(new Timestamp(System.currentTimeMillis()));

        pathRepository.save(dp);

        if (globals.isStoreDataToDb()) {
            try (InputStream is = new FileInputStream(tempFile)) {
                binaryDataService.save(is, tempFile.length(), dp.getId(), BinaryData.Type.RAW_PART);
            }
        }
        if (globals.isStoreDataToDisk()) {
            FileUtils.moveFile(tempFile, datasetFile);
        }


    }

    @PostMapping("/dataset-definition-form-commit")
    public String datasetDefinitionFormCommit(long datasetId, String name, String description, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetCache.findById(datasetId);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#180.01 dataset wasn't found, datasetId: " + datasetId);
            return "redirect:/launchpad/experiments";
        }
        dataset.setName(name);
        dataset.setDescription(description);
        datasetCache.save(dataset);
        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
    }

    @GetMapping("/dataset-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetCache.findById(id);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#181.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/experiments";
        }
        model.addAttribute("dataset", dataset);
        return "launchpad/dataset-delete";
    }

    @PostMapping("/dataset-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetCache.findById(id);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#182.01 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/experiments";
        }
        pathRepository.deleteByDataset(dataset);
        datasetCache.delete(id);
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
