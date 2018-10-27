package aiai.ai.launchpad.dataset;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.File;

@Data
@AllArgsConstructor
public class ConfigForFeature {
    String rawFilePath;
    File rawFile;
    String featureFilePath;
    File featureFile;
    File yamlFile;
}
