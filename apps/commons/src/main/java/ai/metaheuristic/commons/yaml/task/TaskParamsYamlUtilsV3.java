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

import ai.metaheuristic.api.data.task.TaskParamsYamlV2;
import ai.metaheuristic.api.data.task.TaskParamsYamlV3;
import ai.metaheuristic.api.data.task.TaskParamsYamlV4;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 8/08/2019
 * Time: 12:10 AM
 */
public class TaskParamsYamlUtilsV3
        extends AbstractParamsYamlUtils<TaskParamsYamlV3, TaskParamsYamlV4, TaskParamsYamlUtilsV4, TaskParamsYamlV2, TaskParamsYamlUtilsV2, TaskParamsYamlV3> {

    @Override
    public int getVersion() {
        return 3;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(TaskParamsYamlV3.class);
    }

    @Override
    public TaskParamsYamlV4 upgradeTo(TaskParamsYamlV3 yaml, Long ... vars) {
        yaml.checkIntegrity();
        TaskParamsYamlV4 t = new TaskParamsYamlV4();
        t.taskYaml = new TaskParamsYamlV4.TaskYamlV4();
        BeanUtils.copyProperties(yaml.taskYaml, t.taskYaml, "snippet", "preSnippet", "postSnippet");
        t.taskYaml.snippet = toUp(yaml.taskYaml.snippet);
        if (yaml.taskYaml.preSnippets!=null) {
            t.taskYaml.preSnippets = yaml.taskYaml.preSnippets.stream().map(TaskParamsYamlUtilsV3::toUp).collect(Collectors.toList());;
        }
        if (yaml.taskYaml.postSnippets!=null) {
            t.taskYaml.postSnippets = yaml.taskYaml.postSnippets.stream().map(TaskParamsYamlUtilsV3::toUp).collect(Collectors.toList());;
        }

        t.checkIntegrity();

        return t;
    }

    @Override
    public TaskParamsYamlV2 downgradeTo(TaskParamsYamlV3 yaml) {
        yaml.checkIntegrity();
        TaskParamsYamlV2 t = new TaskParamsYamlV2();
        BeanUtils.copyProperties(yaml.taskYaml, t.taskYaml, "snippet", "preSnippet", "postSnippet");
        if (yaml.taskYaml.preSnippets!=null && yaml.taskYaml.preSnippets.size()>0) {
            if (yaml.taskYaml.preSnippets.size()>1) {
                throw new DowngradeNotSupportedException("Too many preSnippets");
            }
            t.taskYaml.preSnippets = yaml.taskYaml.preSnippets.stream().map(TaskParamsYamlUtilsV3::toDown).collect(Collectors.toList());;
        }
        if (yaml.taskYaml.postSnippets!=null && yaml.taskYaml.postSnippets.size()>0) {
            if (yaml.taskYaml.postSnippets.size()>1) {
                throw new DowngradeNotSupportedException("Too many postSnippets");
            }
            t.taskYaml.postSnippets = yaml.taskYaml.postSnippets.stream().map(TaskParamsYamlUtilsV3::toDown).collect(Collectors.toList());;
        }
        t.taskYaml.snippet = toDown(yaml.taskYaml.snippet);

        t.checkIntegrity();
        return t;
    }


    private static TaskParamsYamlV4.SnippetConfigV4 toUp(TaskParamsYamlV3.SnippetConfigV3 src) {
        if (src==null) {
            return null;
        }
        TaskParamsYamlV4.SnippetConfigV4 trg = new TaskParamsYamlV4.SnippetConfigV4();
        trg.checksum = src.checksum;
        trg.checksumMap = src.checksumMap;
        trg.code = src.code;
        trg.env = src.env;
        trg.file = src.file;
        trg.git = src.git;
        if (src.info!=null) {
            trg.info = new TaskParamsYamlV4.SnippetInfoV4(src.info.signed, src.info.length);
        }
        trg.metas = src.metas;
        if (src.metrics!=null && src.metrics) {
            trg.ml = new TaskParamsYamlV4.MachineLearningV4(true, false); ;
        }
        trg.params = src.params;
        trg.skipParams = src.skipParams;
        trg.sourcing = src.sourcing;
        trg.type = src.type;
        return trg;
    }

    private static TaskParamsYamlV2.SnippetConfigV2 toDown(TaskParamsYamlV3.SnippetConfigV3 src) {
        if (src==null) {
            return null;
        }
        TaskParamsYamlV2.SnippetConfigV2 trg = new TaskParamsYamlV2.SnippetConfigV2();
        trg.checksum = src.checksum;
        trg.checksumMap = src.checksumMap;
        trg.code = src.code;
        trg.env = src.env;
        trg.file = src.file;
        trg.git = src.git;
        if (src.info!=null) {
            trg.info = new TaskParamsYamlV2.SnippetInfoV2(src.info.signed, src.info.length);
        }
        trg.metas = src.metas;
        trg.metrics = src.metrics!=null ? src.metrics : false;
        trg.params = src.params;
        trg.skipParams = src.skipParams;
        trg.sourcing = src.sourcing;
        trg.type = src.type;
        return trg;
    }

    @Override
    public TaskParamsYamlUtilsV4 nextUtil() {
        return (TaskParamsYamlUtilsV4) TaskParamsYamlUtils.BASE_YAML_UTILS.getForVersion(4);
    }

    @Override
    public TaskParamsYamlUtilsV2 prevUtil() {
        return (TaskParamsYamlUtilsV2) TaskParamsYamlUtils.BASE_YAML_UTILS.getForVersion(2);
    }

    @Override
    public String toString(TaskParamsYamlV3 params) {
        return getYaml().dump(params);
    }

    @Override
    public TaskParamsYamlV3 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final TaskParamsYamlV3 p = getYaml().load(s);
        return p;
    }

}
