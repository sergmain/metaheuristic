/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.lang.Nullable;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 8/08/2019
 * Time: 12:10 AM
 */
@SuppressWarnings("DuplicatedCode")
public class TaskParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<TaskParamsYamlV2, TaskParamsYaml, Void, TaskParamsYamlV1, TaskParamsYamlUtilsV1, TaskParamsYaml> {

    @Override
    public int getVersion() {
        return 2;
    }

    @Nonnull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(TaskParamsYamlV2.class);
    }

    @Nonnull
    @Override
    public TaskParamsYaml upgradeTo(@Nonnull TaskParamsYamlV2 v1) {
        v1.checkIntegrity();
        TaskParamsYaml t = new TaskParamsYaml();
        t.task = new TaskParamsYaml.TaskYaml();
        BeanUtils.copyProperties(v1.task, t.task, "function", "preFunctions", "postFunctions", "inline", "inputs", "outputs", "metas", "cache");
        t.task.function = toUp(v1.task.function);
        v1.task.preFunctions.stream().map(TaskParamsYamlUtilsV2::toUp).collect(Collectors.toCollection(()->t.task.preFunctions));
        v1.task.postFunctions.stream().map(TaskParamsYamlUtilsV2::toUp).collect(Collectors.toCollection(()->t.task.postFunctions));

        t.task.inline = v1.task.inline;
        v1.task.inputs.stream().map(TaskParamsYamlUtilsV2::upInputVariable).collect(Collectors.toCollection(()->t.task.inputs));
        v1.task.outputs.stream().map(TaskParamsYamlUtilsV2::upOutputVariable).collect(Collectors.toCollection(()->t.task.outputs));
        t.task.metas.addAll(v1.task.metas);
        if (v1.task.cache!=null) {
            t.task.cache = new TaskParamsYaml.Cache(v1.task.cache.enabled, v1.task.cache.omitInline, v1.task.cache.cacheMeta);
        }
        if (v1.task.init!=null) {
            t.task.init = new TaskParamsYaml.Init(v1.task.init.parentTaskIds, v1.task.init.nextState);
        }

        t.checkIntegrity();

        return t;
    }

    private static TaskParamsYaml.InputVariable upInputVariable(TaskParamsYamlV2.InputVariableV2 v1) {
        TaskParamsYaml.InputVariable v = new TaskParamsYaml.InputVariable(
                v1.id, v1.context, v1.name, v1.sourcing, v1.git, v1.disk, v1.filename, v1.type,  v1.empty, v1.getNullable(), v1.ext);
        return v;
    }

    private static TaskParamsYaml.OutputVariable upOutputVariable(TaskParamsYamlV2.OutputVariableV2 v1) {
        TaskParamsYaml.OutputVariable v = new TaskParamsYaml.OutputVariable(
                v1.id, v1.context, v1.name, v1.sourcing, v1.git, v1.disk, v1.filename, v1.uploaded, v1.type, v1.empty, v1.getNullable(), v1.ext);
        return v;
    }

    private static TaskParamsYamlV1.InputVariableV1 downInputVariable(TaskParamsYaml.InputVariable v1) {
        TaskParamsYamlV1.InputVariableV1 v = new TaskParamsYamlV1.InputVariableV1(
                v1.id, v1.context, v1.name, v1.sourcing, v1.git, v1.disk, v1.filename, v1.type,  v1.empty, v1.getNullable(), v1.ext);
        return v;
    }

    private static TaskParamsYamlV1.OutputVariableV1 downOutputVariable(TaskParamsYaml.OutputVariable v1) {
        TaskParamsYamlV1.OutputVariableV1 v = new TaskParamsYamlV1.OutputVariableV1(
                v1.id, v1.context, v1.name, v1.sourcing, v1.git, v1.disk, v1.filename, v1.uploaded, v1.type, v1.empty, v1.getNullable(), v1.ext);
        return v;
    }

    @Nullable
    @Override
    public TaskParamsYamlV1 downgradeTo(TaskParamsYaml v2) {
        TaskParamsYamlV1 t = new TaskParamsYamlV1();
        BeanUtils.copyProperties(v2.task, t.task, "function", "preFunctions", "postFunctions", "inline", "inputs", "outputs", "metas", "cache");
        t.task.function = toDown(v2.task.function);
        v2.task.preFunctions.stream().map(TaskParamsYamlUtilsV2::toDown).collect(Collectors.toCollection(()->t.task.preFunctions));
        v2.task.postFunctions.stream().map(TaskParamsYamlUtilsV2::toDown).collect(Collectors.toCollection(()->t.task.postFunctions));

        t.task.inline = v2.task.inline;
        v2.task.inputs.stream().map(TaskParamsYamlUtilsV2::downInputVariable).collect(Collectors.toCollection(()->t.task.inputs));
        v2.task.outputs.stream().map(TaskParamsYamlUtilsV2::downOutputVariable).collect(Collectors.toCollection(()->t.task.outputs));
        t.task.metas.addAll(v2.task.metas);
        if (v2.task.cache!=null) {
            t.task.cache = new TaskParamsYamlV1.CacheV1(v2.task.cache.enabled, v2.task.cache.omitInline, v2.task.cache.cacheMeta);
        }
        if (v2.task.init!=null) {
            t.task.init = new TaskParamsYamlV1.InitV1(v2.task.init.parentTaskIds, v2.task.init.nextState);
        }
        return t;
    }

    private static TaskParamsYaml.FunctionConfig toUp(TaskParamsYamlV2.FunctionConfigV2 src) {
        TaskParamsYaml.FunctionConfig trg = new TaskParamsYaml.FunctionConfig(
                src.code, src.type, src.file, src.params, src.env, src.sourcing, src.checksumMap, src.git, src.src);

        trg.metas.addAll(src.metas);
        return trg;
    }

    private static TaskParamsYamlV1.FunctionConfigV1 toDown(TaskParamsYaml.FunctionConfig src) {
        TaskParamsYamlV1.FunctionConfigV1 trg = new TaskParamsYamlV1.FunctionConfigV1(
                src.code, src.type, src.file, src.params, src.env, src.sourcing, src.checksumMap, src.git, false, null);

        trg.metas.addAll(src.metas);
        return trg;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public TaskParamsYamlUtilsV1 prevUtil() {
        return (TaskParamsYamlUtilsV1) TaskParamsYamlUtils.UTILS.getForVersion(1);
    }

    @Override
    public String toString(@Nonnull TaskParamsYamlV2 params) {
        params.checkIntegrity();
        return getYaml().dump(params);
    }

    @Nonnull
    @Override
    public TaskParamsYamlV2 to(@Nonnull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final TaskParamsYamlV2 p = getYaml().load(yaml);
        return p;
    }

}
