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

package ai.metaheuristic.ai.yaml.task;

import ai.metaheuristic.ai.yaml.versioning.AbstractParamsYamlUtils;
import ai.metaheuristic.api.v1.data.task.TaskParamsYamlV2;
import ai.metaheuristic.api.v1.data.task.TaskParamsYamlV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.List;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class TaskParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<TaskParamsYamlV1, TaskParamsYamlV2, TaskParamsYamlUtilsV2, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    public Yaml getYaml() {
        return YamlUtils.init(TaskParamsYamlV1.class);
    }

    @Override
    public TaskParamsYamlV2 upgradeTo(TaskParamsYamlV1 taskParams) {
        TaskParamsYamlV2 t = new TaskParamsYamlV2();
        BeanUtils.copyProperties(taskParams, t.taskYaml, "preSnippet", "postSnippet");
        if (taskParams.preSnippet!=null) {
            t.taskYaml.preSnippets = List.of(taskParams.preSnippet);
        }
        if (taskParams.postSnippet!=null) {
            t.taskYaml.postSnippets = List.of(taskParams.postSnippet);
        }
        return t;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        // there isn't any prev version
        return null;
    }

    @Override
    public TaskParamsYamlUtilsV2 nextUtil() {
        return (TaskParamsYamlUtilsV2)TaskParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public Void prevUtil() {
        // there isn't any prev version
        return null;
    }

    public String toString(TaskParamsYamlV1 planYaml) {
        return getYaml().dump(planYaml);
    }

    public TaskParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final TaskParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
