/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 8/08/2019
 * Time: 12:10 AM
 */
@SuppressWarnings("DuplicatedCode")
public class TaskParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<TaskParamsYamlV1, TaskParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(TaskParamsYamlV1.class);
    }

    @NonNull
    @Override
    public TaskParamsYaml upgradeTo(@NonNull TaskParamsYamlV1 v1) {
        v1.checkIntegrity();
        TaskParamsYaml t = new TaskParamsYaml();
        t.task = new TaskParamsYaml.TaskYaml();
        BeanUtils.copyProperties(v1.task, t.task, "function", "preFunctions", "postFunctions", "inline", "inputs", "outputs", "metas", "cache");
        t.task.function = toUp(v1.task.function);
        v1.task.preFunctions.stream().map(TaskParamsYamlUtilsV1::toUp).collect(Collectors.toCollection(()->t.task.preFunctions));
        v1.task.postFunctions.stream().map(TaskParamsYamlUtilsV1::toUp).collect(Collectors.toCollection(()->t.task.postFunctions));

        t.task.inline = v1.task.inline;
        v1.task.inputs.stream().map(TaskParamsYamlUtilsV1::upInputVariable).collect(Collectors.toCollection(()->t.task.inputs));
        v1.task.outputs.stream().map(TaskParamsYamlUtilsV1::upOutputVariable).collect(Collectors.toCollection(()->t.task.outputs));
        t.task.metas.addAll(v1.task.metas);
        if (v1.task.cache!=null) {
            t.task.cache = new TaskParamsYaml.Cache(v1.task.cache.enabled, v1.task.cache.omitInline, v1.task.cache.cacheMeta);
        }

        t.checkIntegrity();

        return t;
    }

    private static TaskParamsYaml.InputVariable upInputVariable(TaskParamsYamlV1.InputVariableV1 v1) {
        TaskParamsYaml.InputVariable v = new TaskParamsYaml.InputVariable(
                v1.id, v1.context, v1.name, v1.sourcing, v1.git, v1.disk, v1.filename, v1.type,  v1.empty, v1.getNullable());
        return v;
    }

    private static TaskParamsYaml.OutputVariable upOutputVariable(TaskParamsYamlV1.OutputVariableV1 v1) {
        TaskParamsYaml.OutputVariable v = new TaskParamsYaml.OutputVariable(
                v1.id, v1.context, v1.name, v1.sourcing, v1.git, v1.disk, v1.filename, v1.uploaded, v1.type, v1.empty, v1.getNullable(), v1.ext);
        return v;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    private static TaskParamsYaml.FunctionConfig toUp(TaskParamsYamlV1.FunctionConfigV1 src) {
        TaskParamsYaml.FunctionConfig trg = new TaskParamsYaml.FunctionConfig(
                src.code, src.type, src.file, src.params, src.env, src.sourcing, src.checksumMap, src.git, src.skipParams, src.content);

        trg.metas.addAll(src.metas);
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
    public String toString(@NonNull TaskParamsYamlV1 params) {
        params.checkIntegrity();
        return getYaml().dump(params);
    }

    @NonNull
    @Override
    public TaskParamsYamlV1 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final TaskParamsYamlV1 p = getYaml().load(yaml);
        return p;
    }

}
