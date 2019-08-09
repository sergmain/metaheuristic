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

package ai.metaheuristic.commons.yaml.task;

import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYamlV2;
import ai.metaheuristic.api.data.task.TaskParamsYamlV3;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 8/08/2019
 * Time: 12:10 AM
 */
public class TaskParamsYamlUtilsV3
        extends AbstractParamsYamlUtils<TaskParamsYamlV3, TaskParamsYaml, Void, TaskParamsYamlV2, TaskParamsYamlUtilsV2, TaskParamsYaml> {

    @Override
    public int getVersion() {
        return 3;
    }

    public Yaml getYaml() {
        return YamlUtils.init(TaskParamsYamlV3.class);
    }

    @Override
    public TaskParamsYaml upgradeTo(TaskParamsYamlV3 yaml, Long ... vars) {
        yaml.checkIntegrity();
        TaskParamsYaml t = new TaskParamsYaml();
        t.taskYaml = new TaskParamsYaml.TaskYaml();
        BeanUtils.copyProperties(yaml.taskYaml, t.taskYaml);
        t.checkIntegrity();

        return t;
    }

    @Override
    public TaskParamsYamlV2 downgradeTo(TaskParamsYaml yaml) {
        yaml.checkIntegrity();
        TaskParamsYamlV2 t = new TaskParamsYamlV2();
        BeanUtils.copyProperties(yaml.taskYaml, t);
/*
        yaml.taskYaml.preSnippets = t.taskYaml.preSnippets;
        yaml.taskYaml.postSnippets = t.taskYaml.postSnippets;
        yaml.taskYaml.snippet = t.taskYaml.snippet;
*/
        t.checkIntegrity();
        return t;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public TaskParamsYamlUtilsV2 prevUtil() {
        return null;
    }

    public String toString(TaskParamsYamlV3 params) {
        return getYaml().dump(params);
    }

    public TaskParamsYamlV3 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final TaskParamsYamlV3 p = getYaml().load(s);
        return p;
    }

}
