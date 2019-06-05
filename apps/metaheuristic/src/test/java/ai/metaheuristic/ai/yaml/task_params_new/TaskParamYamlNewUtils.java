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
package ai.metaheuristic.ai.yaml.task_params_new;

import ai.metaheuristic.api.v1.data.TaskApiData;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class TaskParamYamlNewUtils {


    public static class NonAnchorRepresenter extends Representer {

        public NonAnchorRepresenter() {
            this.multiRepresenters.put(Map.class, new RepresentMap() {
                public Node representData(Object data) {
                    return representWithoutRecordingDescendents(data, super::representData);
                }
            });
        }

        protected Node representWithoutRecordingDescendents(Object data, Function<Object, Node> worker) {
            Map<Object, Node> representedObjectsOnEntry = new LinkedHashMap<>(representedObjects);
            try {
                return worker.apply(data);
            } finally {
                representedObjects.clear();
                representedObjects.putAll(representedObjectsOnEntry);
            }
        }

        @Override
        protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue, Tag customTag) {
            // if value of property is null, ignore it.
            if (propertyValue == null) {
                return null;
            }
            else {
                return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
            }
        }

    }


    private static Yaml getYaml() {

        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Representer representer = new NonAnchorRepresenter();
        representer.addClassTag(TaskApiData.TaskParamYaml.class, Tag.MAP);

        Constructor constructor = new Constructor(TaskApiData.TaskParamYaml.class);

        //noinspection UnnecessaryLocalVariable
        Yaml yaml = new Yaml(constructor, representer, options);
        return yaml;
    }

    public static String toString(TaskApiData.TaskParamYaml taskParamYaml) {
        return getYaml().dump(taskParamYaml);
    }

    public static TaskApiData.TaskParamYaml toTaskYamlNew(String s) {
        return TaskParamYamlNewUtils.getYaml().load(s);
    }

    public static TaskApiData.TaskParamYaml to(InputStream is) {
        return (TaskApiData.TaskParamYaml) YamlUtils.to(is, getYaml());
    }

}
