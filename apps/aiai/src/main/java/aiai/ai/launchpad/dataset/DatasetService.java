package aiai.ai.launchpad.dataset;

import aiai.ai.Consts;
import aiai.ai.Globals;
import aiai.ai.comm.Protocol;
import aiai.ai.core.ArtifactStatus;
import aiai.ai.core.ProcessService;
import aiai.ai.exceptions.StoreNewPartOfRawFileException;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.env.EnvService;
import aiai.ai.launchpad.repositories.FeatureRepository;
import aiai.ai.launchpad.repositories.DatasetPathRepository;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.snippet.SnippetCode;
import aiai.ai.utils.StrUtils;
import aiai.ai.yaml.config.DatasetPreparingConfig;
import aiai.ai.yaml.config.DatasetPreparingConfigUtils;
import aiai.apps.commons.utils.DirUtils;
import aiai.apps.commons.yaml.snippet.SnippetType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.Charsets;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("launchpad")
public class DatasetService {

    private static final String PRODUCE_FEATURE_YAML = "produce-feature.yaml";
    private static final String CONFIG_YAML = "config.yaml";

    private final DatasetPathRepository pathRepository;
    private final Globals globals;
    private final BinaryDataService binaryDataService;
    private final SnippetRepository snippetRepository;
    private final SnippetService snippetService;
    private final EnvService envService;
    private final DatasetCache datasetCache;
    private final FeatureRepository featureRepository;
    private final ProcessService processService;

    public DatasetService(DatasetPathRepository pathRepository, Globals globals, BinaryDataService binaryDataService, SnippetRepository snippetRepository, SnippetService snippetService, EnvService envService, DatasetCache datasetCache, FeatureRepository featureRepository, ProcessService processService) {
        this.pathRepository = pathRepository;
        this.globals = globals;
        this.binaryDataService = binaryDataService;
        this.snippetRepository = snippetRepository;
        this.snippetService = snippetService;
        this.envService = envService;
        this.datasetCache = datasetCache;
        this.featureRepository = featureRepository;
        this.processService = processService;
    }

    void cloneDataset(Dataset dataset) throws SQLException {
        Dataset ds = new Dataset();
        ds.setName(StrUtils.incCopyNumber(dataset.getName()));
        ds.setDescription(dataset.getDescription());
        ds.setAssemblySnippet(dataset.getAssemblySnippet());
        ds.setDatasetSnippet(dataset.getDatasetSnippet());
        ds.setEditable(true);
        ds.setLocked(false);
        ds.setFeatures(new ArrayList<>());
        ds.setLength(dataset.getLength());
        datasetCache.save(ds);
        binaryDataService.cloneBinaryData(dataset.getId(), ds.getId(), BinaryData.Type.DATASET);

        for (Feature feature : dataset.getFeatures()) {
            Feature dg = new Feature();
            BeanUtils.copyProperties(feature, dg);
            dg.setId(null);
            dg.setVersion(null);
            dg.setFeatureStatus(ArtifactStatus.NONE.value);
            dg.setDataset(ds);
            datasetCache.saveGroup(dg);
        }

        for (DatasetPath path : pathRepository.findByDataset(dataset)) {
            File file = new File(globals.launchpadDir, path.getPath());
            storeNewPartOfRawFile(new File(path.getPath()).getName(), ds, file, false);
        }
    }

    void storeNewPartOfRawFile(String originFilename, Dataset dataset, File tempFile, boolean isUsePrefix) {
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
        dp.setLength(tempFile.length());

        pathRepository.save(dp);

        try {
            if (globals.isStoreDataToDb()) {
                try (InputStream is = new FileInputStream(tempFile)) {
                    binaryDataService.save(is, tempFile.length(), dp.getId(), BinaryData.Type.RAW_PART);
                }
            }
            if (globals.isStoreDataToDisk()) {
                FileUtils.moveFile(tempFile, datasetFile);
            }
        } catch (IOException e) {
            throw new StoreNewPartOfRawFileException(tempFile.getPath(), datasetFile.getPath());
        }
    }

    DatasetController.DatasetDefinition prepareDatasetDefinition(Dataset dataset) {
        // path variable is for informing user about directory structure
        final String path = String.format("<Launchpad directory>%c%s%c%06d", File.separatorChar, Consts.DATASET_DIR, File.separatorChar, dataset.getId());

        final DatasetController.DatasetDefinition definition = new DatasetController.DatasetDefinition(dataset, globals.launchpadDir.getPath(), path);
        definition.paths = pathRepository.findByDataset_OrderByPathNumber(dataset);

        final Iterable<Snippet> snippets = snippetRepository.findAll();

        final List<SnippetCode> featureCodes = new ArrayList<>();

        // fix conditions for UI
        final int featureSize = dataset.getFeatures().size();
        for (int i = 0; i < featureSize; i++) {
            Feature feature = dataset.getFeatures().get(i);
            feature.setAddColumn(true);
            if (feature.getSnippet()!=null) {
                featureCodes.add(new SnippetCode(feature.getSnippet().getId(), feature.getSnippet().getSnippetCode()));
            }
        }

        // ugly but it works
        for (Feature group : dataset.getFeatures()) {
            group.featureOptions = snippetService.getSelectOptions(snippets, featureCodes, (s) -> SnippetType.feature!=(SnippetType.valueOf(s.type)));
        }

        final List<SnippetCode> assemblyCodes = dataset.getAssemblySnippet() == null ? new ArrayList<>() : Collections.singletonList(new SnippetCode(dataset.getAssemblySnippet().getId(), dataset.getAssemblySnippet().getSnippetCode()));
        definition.assemblyOptions = snippetService.getSelectOptions(snippets, assemblyCodes, (s) -> SnippetType.assembly!=(SnippetType.valueOf(s.type)));

        final List<SnippetCode> datasetCodes = dataset.getDatasetSnippet() == null ? new ArrayList<>() : Collections.singletonList(new SnippetCode(dataset.getDatasetSnippet().getId(), dataset.getDatasetSnippet().getSnippetCode()));
        definition.datasetOptions = snippetService.getSelectOptions(snippets, datasetCodes, (s) -> SnippetType.dataset!=(SnippetType.valueOf(s.type)));

        definition.envs.putAll( envService.envsAsMap() );

        definition.setStoreToDisk(globals.isStoreDataToDisk());
        definition.setAllPathsValid(true);
        for (DatasetPath datasetPath : definition.getPaths()) {
            if (!datasetPath.isValid()) {
                definition.setAllPathsValid(false);
                break;
            }
        }
        return definition;
    }

    void addEmptyFeature(Dataset dataset) {
//        List<Feature> features = featureRepository.findByDataset_Id(id);
        List<Feature> features = dataset.getFeatures();
        //noinspection ConstantConditions
        int featureOrder = features.isEmpty() ? 1 : features.stream().mapToInt(Feature::getFeatureOrder).max().getAsInt() + 1;

        final Feature feature = new Feature(featureOrder);
        feature.setDataset(dataset);

        dataset.getFeatures().add(feature);
        datasetCache.save(dataset);
    }

    void updateInfoWithFeature(ConfigForFeature configForFeature, Feature group, boolean isOk) throws IOException {
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

    ConfigForFeature createYamlForFeature(Feature group) {

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

    void updateInfoWithRaw(Dataset dataset, boolean isOk) throws IOException {
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

        obsoleteFeatures(dataset);
    }

    private void obsoleteFeatures(Dataset dataset) {
        List<Feature> features = featureRepository.findByDataset_Id(dataset.getId());
        for (Feature group : features) {
            group.setFeatureStatus(ArtifactStatus.OBSOLETE.value);
        }
        datasetCache.saveAllFeatures(features, dataset.getId());
    }

    void updateInfoWithDataset(Dataset dataset, boolean isOk) throws IOException {
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

        obsoleteFeatures(dataset);
    }

    ProcessService.Result runCommand(File yaml, String command, LogData.Type type, Long refId) {

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

    File createConfigYaml(Dataset dataset) throws IOException {
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

    public List<Protocol.AssignedTask.RawAssembling> getRawAssemgling() {
        List<Protocol.AssignedTask.RawAssembling> list = new ArrayList<>();



        return list;
    }

    public List<Protocol.AssignedTask.DatasetProducing> getDatasetProducing() {
        List<Protocol.AssignedTask.DatasetProducing> list = new ArrayList<>();



        return list;
    }
}
