package aiai.ai.yaml.signature;

import aiai.apps.commons.yaml.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

@Slf4j
public class SignaturesConfigUtils {

    private static Yaml yaml;

    static {
        yaml = YamlUtils.init(SignaturesConfig.class);
    }

    public static String toString(SignaturesConfig config) {
        return YamlUtils.toString(config, yaml);
    }

    public static SignaturesConfig to(String s) {
        return (SignaturesConfig) YamlUtils.to(s, yaml);
    }

    public static SignaturesConfig to(InputStream is) {
        return (SignaturesConfig) YamlUtils.to(is, yaml);
    }

    public static SignaturesConfig to(File file) {
        return (SignaturesConfig) YamlUtils.to(file, yaml);
    }
}
