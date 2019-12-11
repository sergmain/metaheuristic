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

import ai.metaheuristic.api.data.task.TaskParamsYamlV1;
import ai.metaheuristic.api.data.task.TaskParamsYamlV2;
import ai.metaheuristic.api.data.task.TaskParamsYamlV3;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class TaskParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<TaskParamsYamlV2, TaskParamsYamlV3, TaskParamsYamlUtilsV3, TaskParamsYamlV1, TaskParamsYamlUtilsV1, TaskParamsYamlV2> {

    @Override
    public int getVersion() {
        return 2;
    }

    public Yaml getYaml() {
        return YamlUtils.init(TaskParamsYamlV2.class);
    }

    @Override
    public TaskParamsYamlV3 upgradeTo(TaskParamsYamlV2 yaml, Long ... vars) {
        TaskParamsYamlV3 t = new TaskParamsYamlV3();
        t.taskYaml = new TaskParamsYamlV3.TaskYamlV3();
        BeanUtils.copyProperties(yaml.taskYaml, t.taskYaml, "snippet", "preSnippet", "postSnippet");
        t.taskYaml.snippet = toUp(yaml.taskYaml.snippet);
        if (yaml.taskYaml.preSnippets!=null) {
            t.taskYaml.preSnippets = yaml.taskYaml.preSnippets.stream().map(TaskParamsYamlUtilsV2::toUp).collect(Collectors.toList());;
        }
        if (yaml.taskYaml.postSnippets!=null) {
            t.taskYaml.postSnippets = yaml.taskYaml.postSnippets.stream().map(TaskParamsYamlUtilsV2::toUp).collect(Collectors.toList());;
        }
        t.checkIntegrity();

        return t;
    }

    private static TaskParamsYamlV3.SnippetConfigYamlV3 toUp(TaskParamsYamlV2.SnippetConfigYamlV2 src) {
        if (src==null) {
            return null;
        }
        TaskParamsYamlV3.SnippetConfigYamlV3 trg = new TaskParamsYamlV3.SnippetConfigYamlV3();
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
        trg.metrics = src.metrics;
        trg.params = src.params;
        trg.skipParams = src.skipParams;
        trg.sourcing = src.sourcing;
        trg.type = src.type;
        return trg;
    }

    private static TaskParamsYamlV1.SnippetConfigYamlV1 toDown(TaskParamsYamlV2.SnippetConfigYamlV2 src) {
        if (src==null) {
            return null;
        }
        TaskParamsYamlV1.SnippetConfigYamlV1 trg = new TaskParamsYamlV1.SnippetConfigYamlV1();
        trg.checksum = src.checksum;
        trg.checksumMap = src.checksumMap;
        trg.code = src.code;
        trg.env = src.env;
        trg.file = src.file;
        trg.git = src.git;
        if (src.info!=null) {
            trg.info = new TaskParamsYamlV1.SnippetInfoV1(src.info.signed, src.info.length);
        }
        trg.metas = src.metas;
        trg.metrics = src.metrics;
        trg.params = src.params;
        trg.skipParams = src.skipParams;
        trg.sourcing = src.sourcing;
        trg.type = src.type;
        return trg;
    }

    @Override
    public TaskParamsYamlV1 downgradeTo(TaskParamsYamlV2 yaml) {
        TaskParamsYamlV1 t = new TaskParamsYamlV1();
        BeanUtils.copyProperties(yaml.taskYaml, t, "snippet", "preSnippet", "postSnippet");
        if (yaml.taskYaml.preSnippets!=null && yaml.taskYaml.preSnippets.size()>0) {
            if (yaml.taskYaml.preSnippets.size()>1) {
                throw new DowngradeNotSupportedException("Too many preSnippets");
            }
            t.preSnippet = toDown(yaml.taskYaml.preSnippets.get(0));
        }
        if (yaml.taskYaml.postSnippets!=null && yaml.taskYaml.postSnippets.size()>0) {
            if (yaml.taskYaml.postSnippets.size()>1) {
                throw new DowngradeNotSupportedException("Too many postSnippets");
            }
            t.postSnippet = toDown(yaml.taskYaml.postSnippets.get(0));
        }
        t.snippet = toDown(yaml.taskYaml.snippet);
        return t;
    }

    @Override
    public TaskParamsYamlUtilsV3 nextUtil() {
        return (TaskParamsYamlUtilsV3) TaskParamsYamlUtils.BASE_YAML_UTILS.getForVersion(3);
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
