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
        BeanUtils.copyProperties(v5.taskYaml, t.taskYaml, "function", "preFunctions", "postFunctions");
        t.taskYaml.function = toUp(v5.taskYaml.function);
        if (v5.taskYaml.preFunctions !=null) {
            t.taskYaml.preFunctions = v5.taskYaml.preFunctions.stream().map(TaskParamsYamlUtilsV1::toUp).collect(Collectors.toList());;
        }
        if (v5.taskYaml.postFunctions !=null) {
            t.taskYaml.postFunctions = v5.taskYaml.postFunctions.stream().map(TaskParamsYamlUtilsV1::toUp).collect(Collectors.toList());;
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

    private static TaskParamsYaml.FunctionConfig toUp(TaskParamsYamlV1.FunctionConfigV1 src) {
        if (src==null) {
            return null;
        }
        TaskParamsYaml.FunctionConfig trg = new TaskParamsYaml.FunctionConfig();
        trg.checksum = src.checksum;
        trg.checksumMap = src.checksumMap;
        trg.code = src.code;
        trg.env = src.env;
        trg.file = src.file;
        trg.git = src.git;
        if (src.info!=null) {
            trg.info = new TaskParamsYaml.FunctionInfo(src.info.signed, src.info.length);
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
