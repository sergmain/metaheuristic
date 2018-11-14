package aiai.ai.launchpad.experiment.resource;

import aiai.ai.Globals;
import aiai.ai.core.ExecProcessService;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.env.EnvService;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.launchpad.snippet.SnippetService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Profile("launchpad")
public class ResourceService {

    private static final String PRODUCE_FEATURE_YAML = "produce-feature.yaml";
    private static final String CONFIG_YAML = "config.yaml";

    private final Globals globals;
    private final BinaryDataService binaryDataService;
    private final SnippetRepository snippetRepository;
    private final SnippetService snippetService;
    private final EnvService envService;
    private final ExecProcessService execProcessService;

    public ResourceService(Globals globals, BinaryDataService binaryDataService, SnippetRepository snippetRepository, SnippetService snippetService, EnvService envService, ExecProcessService execProcessService) {
        this.globals = globals;
        this.binaryDataService = binaryDataService;
        this.snippetRepository = snippetRepository;
        this.snippetService = snippetService;
        this.envService = envService;
        this.execProcessService = execProcessService;
    }

    void storeNewPartOfRawFile(String originFilename, File tempFile, boolean isUsePrefix) {

        //noinspection ConstantConditions
        final String path = String.format("%s%c%06d%craws", Consts.DATASET_DIR, File.separatorChar, dataset.getId(), File.separatorChar);

        File datasetDir = new File(globals.launchpadDir, path);
        if (!datasetDir.exists()) {
            boolean status = datasetDir.mkdirs();
            if (!status) {
                throw new IllegalStateException("Error create directory: " + datasetDir.getAbsolutePath());
            }
        }

        String checksumAsJson = ResourceChecksum.getChecksumAsJson(tempFile);

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
                    binaryDataService.save(is, tempFile.length(), dp.getId(), Enums.BinaryDataType.DATA);
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

    ExecProcessService.Result runCommand(File yaml, String command) {

        // https://examples.javacodegeeks.com/core-java/lang/processbuilder/java-lang-processbuilder-example/
        //
        // java -jar bin\app-assembly-dataset-1.0-SNAPSHOT.jar 6
        try {
            List<String> cmd = Arrays.stream(command.split("\\s+")).collect(Collectors.toList());
            cmd.add(yaml.getPath());
            final File execDir = globals.launchpadDir.getCanonicalFile();

            return execProcessService.execCommand(cmd, execDir);

        }
        catch (Exception e) {
            log.error("Error", e);
            return new ExecProcessService.Result(false, -1, e.getMessage());
        }
    }
}
