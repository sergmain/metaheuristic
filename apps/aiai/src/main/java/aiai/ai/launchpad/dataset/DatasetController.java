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
import aiai.ai.core.ProcessService;
import aiai.ai.exceptions.BinaryDataNotFoundException;
import aiai.ai.exceptions.StoreNewPartOfRawFileException;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.env.EnvService;
import aiai.ai.launchpad.repositories.FeatureRepository;
import aiai.ai.launchpad.repositories.DatasetPathRepository;
import aiai.ai.launchpad.repositories.DatasetRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.snippet.SnippetUtils;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.utils.SimpleSelectOption;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:21
 */
@Controller
@RequestMapping("/launchpad")
@Slf4j
@Profile("launchpad")
public class DatasetController {

    private static final Set<String> exts;

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
        public boolean isStoreToDisk;
        public boolean isAllPathsValid;

        public DatasetDefinition(Dataset dataset, String launchpadDirAsString, String datasetDirAsString) {
            this.dataset = dataset;
            this.launchpadDirAsString = launchpadDirAsString;
            this.datasetDirAsString = datasetDirAsString;
        }
        public boolean canBeLocked() {
            boolean state = dataset.isLocked() || dataset.getAssemblySnippet()==null ||
                    dataset.getDatasetSnippet()==null || dataset.getFeatures().isEmpty() ||
                    paths.isEmpty();
            if (!state) {
                return false;
            }

            for (Feature feature : dataset.getFeatures()) {
                if (feature.getSnippet()==null) {
                    return false;
                }
            }
            return true;
        }
    }

    static {
        exts = new HashSet<>();
        Collections.addAll(exts, ".json", ".csv", ".txt", ".xml", ".yaml");
    }

    private final Globals globals;
    private final DatasetService datasetService;
    private final DatasetRepository datasetRepository;
    private final FeatureRepository featureRepository;
    private final DatasetPathRepository pathRepository;
    private final SnippetService snippetService;
    private final SnippetCache snippetCache;
    private final EnvService envService;
    private final DatasetCache datasetCache;
    private final BinaryDataService binaryDataService;

    public DatasetController(Globals globals, DatasetRepository datasetRepository, FeatureRepository featureRepository, DatasetPathRepository pathRepository, ProcessService processService, SnippetService snippetService, SnippetCache snippetCache, EnvService envService, DatasetCache datasetCache, BinaryDataService binaryDataService, DatasetService datasetService) {
        this.globals = globals;
        this.datasetRepository = datasetRepository;
        this.featureRepository = featureRepository;
        this.pathRepository = pathRepository;
        this.snippetService = snippetService;
        this.snippetCache = snippetCache;
        this.envService = envService;
        this.datasetCache = datasetCache;
        this.binaryDataService = binaryDataService;
        this.datasetService = datasetService;
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

    @PostMapping("/dataset-lock-and-process-commit/{id}")
    public String datasetLockAndProcessCommit(@PathVariable Long id, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetCache.findById(id);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#173.05 dataset wasn't found, datasetId: " + id);
            return "redirect:/launchpad/datasets";
        }

        dataset.setLocked(true);
        dataset.setEditable(false);
        datasetCache.save(dataset);



        return "redirect:/launchpad/dataset-definition/"+id;
    }

    @GetMapping(value = "/dataset-definition/{id}")
    public String toDatasetDefinition(@PathVariable(name = "id") Long datasetId, Model model, @ModelAttribute("errorMessage") final String errorMessage, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetCache.findById(datasetId);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#174.01 dataset wasn't found, datasetId: " + datasetId);
            return "redirect:/launchpad/datasets";
        }
        final DatasetDefinition definition = datasetService.prepareDatasetDefinition(dataset);

        model.addAttribute("result", definition);
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
        Snippet snippet = snippetCache.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
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
        Snippet snippet = snippetCache.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
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
        final Feature group = featureRepository.findById(id).orElse(null);
        if (group==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#180.01 dataset group wasn't found, featureId: " + id);
            return "redirect:/launchpad/datasets";
        }
        SnippetVersion snippetVersion = SnippetVersion.from(code);
        Snippet snippet = snippetCache.findByNameAndSnippetVersion(snippetVersion.name, snippetVersion.version);
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
            datasetService.cloneDataset(dataset);
        } catch (StoreNewPartOfRawFileException e) {
            log.error("Error while copying part of raw file, src: "+e.srcPath+", trg: "+e.trgPath, e);
            redirectAttributes.addFlashAttribute("errorMessage", "#150.02 Error while copying part of raw file: " + e.toString());
            return "redirect:/launchpad/datasets";
        } catch (SQLException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "#150.05 Error cloning binaryData, error: " + e.toString());
            return "redirect:/launchpad/datasets";
        }

        return "redirect:/launchpad/datasets";
    }

    @GetMapping(value = "/dataset-delete-group/{id}")
    public String deleteGroup(@PathVariable Long id) {
        final Optional<Feature> value = featureRepository.findById(id);
        if (!value.isPresent()) {
            return "redirect:/launchpad/datasets";
        }
        Feature group = value.get();
        long datasetId = group.getDataset().getId();

        datasetCache.delete(group);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-group-add-new-feature/{id}")
    public String addNewFeature(@PathVariable(name = "id") Long datasetId, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetCache.findById(datasetId);
        if (dataset==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#150.10 Dataset wasn't found, datasetId: " + datasetId);
            return "redirect:/launchpad/datasets";
        }

        datasetService.addEmptyFeature(dataset);
        return "redirect:/launchpad/dataset-definition/" + datasetId;
    }

    @GetMapping(value = "/dataset-produce-feature/{id}")
    public String produceFeatureForGroup(@PathVariable(name = "id") Long groupId, final RedirectAttributes redirectAttributes) {
        final Feature feature = featureRepository.findById(groupId).orElse(null);
        if (feature == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#150.01 Feature wasn't found, featureId: " + groupId);
            return "redirect:/launchpad/datasets";
        }
        Dataset dataset = feature.getDataset();
        Long datasetId = dataset.getId();
        try {
            Env env = envService.envsAsMap().get(feature.getSnippet().getEnv());
            if (env == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "#153.01 Environment definition wasn't found for feature snippet. Requested environment: " + feature.getSnippet().getEnv());
                return "redirect:/launchpad/dataset-definition/" + datasetId;
            }

            final Snippet snippet = feature.getSnippet();
            final File snippetDir = new File(globals.launchpadDir, Consts.SNIPPET_DIR);
            final ConfigForFeature configForFeature = datasetService.createYamlForFeature(feature);
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

            final File yaml = configForFeature.yamlFile;
            log.info("yaml file: {}", yaml.getPath());
            final ProcessService.Result result = datasetService.runCommand(yaml, cmdLine, LogData.Type.FEATURE, feature.getId());
            boolean isOk = result.isOk();
            datasetService.updateInfoWithFeature(configForFeature, feature, isOk);
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
        Feature group = featureRepository.findById(id).orElse(null);
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
            boolean isAllValid = true;
            for (DatasetPath path : paths) {
                File rawPartFile = new File(globals.launchpadDir, path.getPath());
                if (rawPartFile.isFile() && rawPartFile.exists() && rawPartFile.length()==path.getLength()) {
                    continue;
                }
                rawPartFile.delete();
                try {
                    binaryDataService.storeToFile(path.getId(), BinaryData.Type.RAW_PART, rawPartFile);
                } catch (BinaryDataNotFoundException e) {
                    isAllValid=false;
                    path.setValid(false);
                    pathRepository.save(path);
                }
            }
            if (!isAllValid) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "#105.01 Some of raw parts aren't valid. You need to recreated them.");
                return "redirect:/launchpad/dataset-definition/" + dataset.getId();
            }
        }
        SnippetUtils.SnippetFile snippetFile = SnippetUtils.getSnippetFile(snippetDir, dataset.getAssemblySnippet().getSnippetCode(), dataset.getAssemblySnippet().filename);
        if (!snippetFile.file.exists()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#104.01 Assembly snippet wasn't found");
            return "redirect:/launchpad/dataset-definition/" + dataset.getId();
        }
        String cmdLine = env.value+' '+ snippetFile.file.getAbsolutePath()+' '+dataset.getAssemblySnippet().params;

        final File yaml = datasetService.createConfigYaml(dataset);
        final ProcessService.Result result = datasetService.runCommand(yaml, cmdLine, LogData.Type.ASSEMBLING, dataset.getId());
        boolean isOk = result.isOk();
        datasetService.updateInfoWithRaw(dataset, isOk);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
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
        final Snippet snippet = dataset.getDatasetSnippet();
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

        File yaml = datasetService.createConfigYaml(dataset);
        final ProcessService.Result result = datasetService.runCommand(yaml, cmdLine, LogData.Type.PRODUCING, dataset.getId());
        boolean isOk = result.isOk();
        datasetService.updateInfoWithDataset(dataset, isOk);

        return "redirect:/launchpad/dataset-definition/" + dataset.getId();
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
            datasetService.storeNewPartOfRawFile(originFilename, dataset, tempFile, true);
        } catch (StoreNewPartOfRawFileException e) {
            log.error("Error", e);
            redirectAttributes.addFlashAttribute("errorMessage", "#172.04 An error while saving data to file, " + e.toString());
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }
        return "redirect:/launchpad/dataset-definition/" + datasetId;
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

    @GetMapping("/dataset-path-delete-all-not-valid/{id}")
    public String deleteAllNotValidPath(@PathVariable("id") Long datasetId, final RedirectAttributes redirectAttributes) {
        Dataset dataset = datasetCache.findById(datasetId);
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#183.01 dataset wasn't found, datasetId: " + datasetId);
            return "redirect:/launchpad/dataset-definition/" + datasetId;
        }
        final List<DatasetPath> paths = pathRepository.findByDataset(dataset);
        for (DatasetPath path : paths) {
            if (!path.isValid()) {
                pathRepository.delete(path);
            }
        }
        return "redirect:/launchpad/dataset-definition/" + datasetId;
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
