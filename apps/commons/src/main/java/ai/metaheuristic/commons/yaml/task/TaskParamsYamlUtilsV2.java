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

import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYamlV1;
import ai.metaheuristic.api.data.task.TaskParamsYamlV2;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class TaskParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<TaskParamsYamlV2, TaskParamsYaml, Void, TaskParamsYamlV1, TaskParamsYamlUtilsV1, TaskParamsYaml> {

    @Override
    public int getVersion() {
        return 2;
    }

    public Yaml getYaml() {
        return YamlUtils.init(TaskParamsYamlV2.class);
    }

    @Override
    public TaskParamsYaml upgradeTo(TaskParamsYamlV2 yaml) {
        TaskParamsYaml t = new TaskParamsYaml();
        t.taskYaml = new TaskParamsYaml.TaskYaml();
        BeanUtils.copyProperties(yaml.taskYaml, t.taskYaml);
        t.checkIntegrity();

        return t;
    }

    @Override
    public TaskParamsYamlV1 downgradeTo(TaskParamsYaml yaml) {
        TaskParamsYamlV1 t = new TaskParamsYamlV1();
        BeanUtils.copyProperties(yaml.taskYaml, t);
        if (yaml.taskYaml.preSnippets!=null && yaml.taskYaml.preSnippets.size()>0) {
            if (yaml.taskYaml.preSnippets.size()>1) {
                throw new DowngradeNotSupportedException("Too many preSnippets");
            }
            t.preSnippet = yaml.taskYaml.preSnippets.get(0);
        }
        if (yaml.taskYaml.postSnippets!=null && yaml.taskYaml.postSnippets.size()>0) {
            if (yaml.taskYaml.postSnippets.size()>1) {
                throw new DowngradeNotSupportedException("Too many postSnippets");
            }
            t.postSnippet = yaml.taskYaml.postSnippets.get(0);
        }
        return t;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public TaskParamsYamlUtilsV1 prevUtil() {
        return null;
    }

    public String toString(TaskParamsYamlV2 planYaml) {
        return getYaml().dump(planYaml);
    }

    public TaskParamsYamlV2 to(String s) {
        final TaskParamsYamlV2 p = getYaml().load(s);
        return p;
    }

}
