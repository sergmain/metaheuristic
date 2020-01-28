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
import ai.metaheuristic.api.data.task.TaskParamsYamlV4;
import ai.metaheuristic.api.data.task.TaskParamsYamlV5;
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
public class TaskParamsYamlUtilsV5
        extends AbstractParamsYamlUtils<TaskParamsYamlV5, TaskParamsYaml, Void, TaskParamsYamlV4, TaskParamsYamlUtilsV4, TaskParamsYaml> {

    @Override
    public int getVersion() {
        return 5;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(TaskParamsYamlV5.class);
    }

    @Override
    public TaskParamsYaml upgradeTo(TaskParamsYamlV5 v5, Long ... vars) {
        v5.checkIntegrity();
        TaskParamsYaml t = new TaskParamsYaml();
        t.taskYaml = new TaskParamsYaml.TaskYaml();
        BeanUtils.copyProperties(v5.taskYaml, t.taskYaml, "snippet", "preSnippet", "postSnippet");
        t.taskYaml.snippet = toUp(v5.taskYaml.snippet);
        if (v5.taskYaml.preSnippets!=null) {
            t.taskYaml.preSnippets = v5.taskYaml.preSnippets.stream().map(TaskParamsYamlUtilsV5::toUp).collect(Collectors.toList());;
        }
        if (v5.taskYaml.postSnippets!=null) {
            t.taskYaml.postSnippets = v5.taskYaml.postSnippets.stream().map(TaskParamsYamlUtilsV5::toUp).collect(Collectors.toList());;
        }
        if (v5.taskYaml.taskMl!=null) {
            t.taskYaml.taskMl = new TaskParamsYaml.TaskMachineLearning(v5.taskYaml.taskMl.hyperParams);
        }

        t.checkIntegrity();

        return t;
    }

    @Override
    public TaskParamsYamlV4 downgradeTo(TaskParamsYaml yaml) {
        yaml.checkIntegrity();
        TaskParamsYamlV4 t = new TaskParamsYamlV4();
        BeanUtils.copyProperties(yaml.taskYaml, t.taskYaml, "snippet", "preSnippet", "postSnippet");
        if (yaml.taskYaml.preSnippets!=null && yaml.taskYaml.preSnippets.size()>0) {
            t.taskYaml.preSnippets = yaml.taskYaml.preSnippets.stream().map(TaskParamsYamlUtilsV5::toDown).collect(Collectors.toList());;
        }
        if (yaml.taskYaml.postSnippets!=null && yaml.taskYaml.postSnippets.size()>0) {
            t.taskYaml.postSnippets = yaml.taskYaml.postSnippets.stream().map(TaskParamsYamlUtilsV5::toDown).collect(Collectors.toList());;
        }
        t.taskYaml.snippet = toDown(yaml.taskYaml.snippet);

        t.checkIntegrity();
        return t;
    }


    private static TaskParamsYaml.SnippetConfig toUp(TaskParamsYamlV5.SnippetConfigV5 src) {
        if (src==null) {
            return null;
        }
        TaskParamsYaml.SnippetConfig trg = new TaskParamsYaml.SnippetConfig();
        trg.checksum = src.checksum;
        trg.checksumMap = src.checksumMap;
        trg.code = src.code;
        trg.env = src.env;
        trg.file = src.file;
        trg.git = src.git;
        if (src.info!=null) {
            trg.info = new TaskParamsYaml.SnippetInfo(src.info.signed, src.info.length);
        }
        trg.metas = src.metas;
        if (src.ml!=null) {
            trg.ml = new TaskParamsYaml.MachineLearning(src.ml.metrics, src.ml.fitting);
        }
        trg.params = src.params;
        trg.skipParams = src.skipParams;
        trg.sourcing = src.sourcing;
        trg.type = src.type;
        return trg;
    }

    private static TaskParamsYamlV4.SnippetConfigV4 toDown(TaskParamsYaml.SnippetConfig src) {
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
        if (src.ml!=null) {
            trg.ml = new TaskParamsYamlV4.MachineLearningV4(src.ml.metrics, src.ml.fitting);
        }
        trg.params = src.params;
        trg.skipParams = src.skipParams;
        trg.sourcing = src.sourcing;
        trg.type = src.type;
        return trg;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public TaskParamsYamlUtilsV4 prevUtil() {
        return (TaskParamsYamlUtilsV4) TaskParamsYamlUtils.BASE_YAML_UTILS.getForVersion(4);
    }

    @Override
    public String toString(TaskParamsYamlV5 params) {
        return getYaml().dump(params);
    }

    @Override
    public TaskParamsYamlV5 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final TaskParamsYamlV5 p = getYaml().load(s);
        return p;
    }

}
