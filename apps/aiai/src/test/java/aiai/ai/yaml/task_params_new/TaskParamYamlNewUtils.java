/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
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
package aiai.ai.yaml.task_params_new;

import aiai.ai.yaml.env.EnvYaml;
import aiai.apps.commons.yaml.YamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

public class TaskParamYamlNewUtils {

    private static Yaml yaml;

    static {
        yaml = YamlUtils.init(TaskParamNewYaml.class);

/*
        TypeDescription customTypeDescription = new TypeDescription(TaskParamNewYaml.class);
        customTypeDescription.addPropertyParameters("inputResourceCodes", TaskResource.class);

        yaml = YamlUtils.initWithTags(TaskParamNewYaml.class, new Class[]{TaskParamNewYaml.class},
                customTypeDescription);
*/

/*
        yaml = YamlUtils.initWithTags(TaskParamNewYaml.class,
                new Class[]{TaskParamNewYaml.class},
                new Class[]{TaskResource.class});
*/
    }

    private static final Object syncObj = new Object();

    public static String toString(TaskParamNewYaml taskParamYaml) {
        synchronized (syncObj) {
            return yaml.dump(taskParamYaml);
        }
    }

    public static TaskParamNewYaml toTaskYamlNew(String s) {
        synchronized (syncObj) {
            return TaskParamYamlNewUtils.yaml.load(s);
        }
    }

    public static TaskParamNewYaml to(InputStream is) {
        return (TaskParamNewYaml) YamlUtils.to(is, yaml);
    }

}
