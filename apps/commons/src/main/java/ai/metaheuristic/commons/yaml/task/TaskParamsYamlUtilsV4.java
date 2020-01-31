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
import ai.metaheuristic.api.data.task.TaskParamsYamlV3;
import ai.metaheuristic.api.data.task.TaskParamsYamlV4;
import ai.metaheuristic.api.data.task.TaskParamsYamlV5;
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
public class TaskParamsYamlUtilsV4
        extends AbstractParamsYamlUtils<TaskParamsYamlV4, TaskParamsYamlV5, TaskParamsYamlUtilsV5, TaskParamsYamlV3, TaskParamsYamlUtilsV3, TaskParamsYaml> {

    @Override
    public int getVersion() {
        return 4;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(TaskParamsYamlV4.class);
    }

    @Override
    public TaskParamsYamlV5 upgradeTo(TaskParamsYamlV4 v4, Long ... vars) {
        v4.checkIntegrity();
        TaskParamsYamlV5 t = new TaskParamsYamlV5();
        t.taskYaml = new TaskParamsYamlV5.TaskYamlV5();
        BeanUtils.copyProperties(v4.taskYaml, t.taskYaml, "snippet", "preSnippet", "postSnippet");
        t.taskYaml.snippet = toUp(v4.taskYaml.snippet);
        if (v4.taskYaml.preSnippets!=null) {
            t.taskYaml.preSnippets = v4.taskYaml.preSnippets.stream().map(TaskParamsYamlUtilsV4::toUp).collect(Collectors.toList());;
        }
        if (v4.taskYaml.postSnippets!=null) {
            t.taskYaml.postSnippets = v4.taskYaml.postSnippets.stream().map(TaskParamsYamlUtilsV4::toUp).collect(Collectors.toList());;
        }
        if (v4.taskYaml.hyperParams!=null && !v4.taskYaml.hyperParams.isEmpty()) {
            t.taskYaml.taskMl = new TaskParamsYamlV5.TaskMachineLearningV5();
            t.taskYaml.taskMl.hyperParams = v4.taskYaml.hyperParams;
        }
        v4.taskYaml.inputResourceCodes.forEach((key, value) -> t.taskYaml.inputResourceIds.put(key, value));
        t.checkIntegrity();

        return t;
    }

    @Override
    public TaskParamsYamlV3 downgradeTo(TaskParamsYaml yaml) {
        yaml.checkIntegrity();
        TaskParamsYamlV3 t = new TaskParamsYamlV3();
        BeanUtils.copyProperties(yaml.taskYaml, t.taskYaml, "snippet", "preSnippet", "postSnippet");
        if (yaml.taskYaml.preSnippets!=null && yaml.taskYaml.preSnippets.size()>0) {
            t.taskYaml.preSnippets = yaml.taskYaml.preSnippets.stream().map(TaskParamsYamlUtilsV4::toDown).collect(Collectors.toList());;
        }
        if (yaml.taskYaml.postSnippets!=null && yaml.taskYaml.postSnippets.size()>0) {
            t.taskYaml.postSnippets = yaml.taskYaml.postSnippets.stream().map(TaskParamsYamlUtilsV4::toDown).collect(Collectors.toList());;
        }
        t.taskYaml.snippet = toDown(yaml.taskYaml.snippet);

        t.checkIntegrity();
        return t;
    }


    private static TaskParamsYamlV5.SnippetConfigV5 toUp(TaskParamsYamlV4.SnippetConfigV4 src) {
        if (src==null) {
            return null;
        }
        TaskParamsYamlV5.SnippetConfigV5 trg = new TaskParamsYamlV5.SnippetConfigV5();
        trg.checksum = src.checksum;
        trg.checksumMap = src.checksumMap;
        trg.code = src.code;
        trg.env = src.env;
        trg.file = src.file;
        trg.git = src.git;
        if (src.info!=null) {
            trg.info = new TaskParamsYamlV5.SnippetInfoV5(src.info.signed, src.info.length);
        }
        trg.metas = src.metas;
        if (src.ml!=null) {
            trg.ml = new TaskParamsYamlV5.MachineLearningV5(src.ml.metrics, src.ml.fitting);
        }
        trg.params = src.params;
        trg.skipParams = src.skipParams;
        trg.sourcing = src.sourcing;
        trg.type = src.type;
        return trg;
    }

    private static TaskParamsYamlV3.SnippetConfigV3 toDown(TaskParamsYaml.SnippetConfig src) {
        if (src==null) {
            return null;
        }
        TaskParamsYamlV3.SnippetConfigV3 trg = new TaskParamsYamlV3.SnippetConfigV3();
        trg.checksum = src.checksum;
        trg.checksumMap = src.checksumMap;
        trg.code = src.code;
        trg.env = src.env;
        trg.file = src.file;
        trg.git = src.git;
        if (src.info!=null) {
            trg.info = new TaskParamsYamlV3.SnippetInfoV3(src.info.signed, src.info.length);
        }
        trg.metas = src.metas;
        if (src.ml!=null) {
            if (src.ml.fitting) {
                throw new DowngradeNotSupportedException("ml.fitting is true");
            }
            trg.metrics = src.ml.metrics;
        }
        trg.params = src.params;
        trg.skipParams = src.skipParams;
        trg.sourcing = src.sourcing;
        trg.type = src.type;
        return trg;
    }

    @Override
    public TaskParamsYamlUtilsV5 nextUtil() {
        return (TaskParamsYamlUtilsV5) TaskParamsYamlUtils.BASE_YAML_UTILS.getForVersion(5);
    }

    @Override
    public TaskParamsYamlUtilsV3 prevUtil() {
        return (TaskParamsYamlUtilsV3) TaskParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
    }

    @Override
    public String toString(TaskParamsYamlV4 params) {
        return getYaml().dump(params);
    }

    @Override
    public TaskParamsYamlV4 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final TaskParamsYamlV4 p = getYaml().load(s);
        return p;
    }

}
