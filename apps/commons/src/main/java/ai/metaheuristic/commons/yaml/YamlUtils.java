/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.commons.yaml;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class YamlUtils {

    // https://bitbucket.org/asomov/snakeyaml/wiki/Documentation#markdown-header-threading
    // The implementation of Yaml is not thread-safe.
    // Different threads may not call the same instance.
    // Threads must have separate Yaml instances.

    public static Yaml init(Class<?> clazz) {
        return initWithTags(clazz, new Class[]{clazz}, null);
    }

    public static Yaml initWithTags(Class<?> clazz, Class<?>[] clazzMap, @Nullable TypeDescription customTypeDescription) {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Representer representer = new Representer() {

            @Override
            @Nullable
            protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue, Tag customTag) {
                // if value of property is null, ignore it.
                if (propertyValue == null) {
                    return null;
                }
                else {
                    return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
                }
            }
        };
        if (clazzMap!=null) {
            for (Class<?> clazzTag : clazzMap) {
                representer.addClassTag(clazzTag, Tag.MAP);
            }
        }

        Constructor constructor = new Constructor(clazz);
        if (customTypeDescription!=null) {
            constructor.addTypeDescription(customTypeDescription);
        }

        Yaml yaml = new Yaml(constructor, representer, options);
        return yaml;
    }

    public static String toString(Object obj, Yaml yaml) {
        return yaml.dump(obj);
    }

    public static Object to(String s, Yaml yaml) {
        if (S.b(s)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        return yaml.load(s);
    }

    @Nullable
    public static Object toNullable(@Nullable String s, Yaml yaml) {
        if (S.b(s)) {
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
