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
import ai.metaheuristic.api.data.task.TaskParamsYamlV1;
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
public class TaskParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<TaskParamsYamlV1, TaskParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(TaskParamsYamlV1.class);
    }

    @Override
    public TaskParamsYaml upgradeTo(TaskParamsYamlV1 v5, Long ... vars) {
        v5.checkIntegrity();
        TaskParamsYaml t = new TaskParamsYaml();
        t.taskYaml = new TaskParamsYaml.TaskYaml();
        BeanUtils.copyProperties(v5.taskYaml, t.taskYaml, "snippet", "preSnippet", "postSnippet");
        t.taskYaml.snippet = toUp(v5.taskYaml.snippet);
        if (v5.taskYaml.preSnippets!=null) {
            t.taskYaml.preSnippets = v5.taskYaml.preSnippets.stream().map(TaskParamsYamlUtilsV1::toUp).collect(Collectors.toList());;
        }
        if (v5.taskYaml.postSnippets!=null) {
            t.taskYaml.postSnippets = v5.taskYaml.postSnippets.stream().map(TaskParamsYamlUtilsV1::toUp).collect(Collectors.toList());;
        }
        if (v5.taskYaml.taskMl!=null) {
            t.taskYaml.taskMl = new TaskParamsYaml.TaskMachineLearning(v5.taskYaml.taskMl.hyperParams);
        }

        t.checkIntegrity();

        return t;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    private static TaskParamsYaml.SnippetConfig toUp(TaskParamsYamlV1.SnippetConfigV1 src) {
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

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(TaskParamsYamlV1 params) {
        return getYaml().dump(params);
    }

    @Override
    public TaskParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final TaskParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
