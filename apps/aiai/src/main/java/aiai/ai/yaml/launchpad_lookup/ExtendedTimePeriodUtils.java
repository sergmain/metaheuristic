package aiai.ai.yaml.launchpad_lookup;

import aiai.ai.yaml.env.EnvYaml;
import aiai.apps.commons.yaml.YamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;

public class ExtendedTimePeriodUtils {
    private static Yaml yaml;

    static {
        yaml = YamlUtils.init(ExtendedTimePeriod.class);
    }

    public static String toString(ExtendedTimePeriod config) {
        return YamlUtils.toString(config, yaml);
    }

    public static ExtendedTimePeriod to(String s) {
        return (ExtendedTimePeriod) YamlUtils.to(s, yaml);
    }

    public static ExtendedTimePeriod to(InputStream is) {
        return (ExtendedTimePeriod) YamlUtils.to(is, yaml);
    }

    public static ExtendedTimePeriod to(File file) {
        return (ExtendedTimePeriod) YamlUtils.to(file, yaml);
    }

}
