package aiai.ai.launchpad.experiment.resource;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.core.ExecProcessService;
import aiai.ai.exceptions.StoreNewPartOfRawFileException;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.env.EnvService;
import aiai.ai.launchpad.repositories.SnippetRepository;
import aiai.ai.launchpad.snippet.SnippetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    void storeNewPartOfRawFile(String originFilename, File tempFile,
                               boolean isUsePrefix, String code, String poolCode) {

        try {
            if (globals.isStoreDataToDb()) {
                try (InputStream is = new FileInputStream(tempFile)) {
                    binaryDataService.save(is, tempFile.length(), Enums.BinaryDataType.DATA, code, poolCode);
                }
            }
        } catch (IOException e) {
            throw new StoreNewPartOfRawFileException(tempFile.getPath(), originFilename);
        }
    }

    ResourceController.ResourceDefinition prepareResourceDefinition() {
        // path variable is for informing user about directory structure

        final ResourceController.ResourceDefinition definition = new ResourceController.ResourceDefinition(globals.launchpadDir.getPath());
        if (true) throw new IllegalStateException("Not implemented");
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
