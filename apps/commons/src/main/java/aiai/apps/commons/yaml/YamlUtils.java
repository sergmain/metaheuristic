package aiai.apps.commons.yaml;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class YamlUtils {

    public static Yaml init(Class<?> clazz) {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Representer representer = new Representer();
        representer.addClassTag(clazz, Tag.MAP);

        //noinspection UnnecessaryLocalVariable
        Yaml yaml = new Yaml(new Constructor(clazz), representer, options);
        return yaml;
    }

    public static String toString(Object obj, Yaml yaml) {
        return yaml.dump(obj);
    }

    public static Object to(String s, Yaml yaml) {
        if (s==null) {
            return null;
        }
        return yaml.load(s);
    }

    public static Object to(InputStream is, Yaml yaml) {
        return yaml.load(is);
    }

    public static Object to(File file, Yaml yaml) {
        try(FileInputStream fis =  new FileInputStream(file)) {
            return yaml.load(fis);
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error while loading file: " + file.getPath(), e);
        }
    }}
