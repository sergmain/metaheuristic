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

import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 8/08/2019
 * Time: 12:10 AM
 */
@SuppressWarnings("DuplicatedCode")
public class TaskParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<TaskParamsYamlV1, TaskParamsYamlV2, TaskParamsYamlUtilsV2, Void, Void, Void> {

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
    public TaskParamsYamlV2 upgradeTo(@NonNull TaskParamsYamlV1 v1) {
        v1.checkIntegrity();
        TaskParamsYamlV2 t = new TaskParamsYamlV2();
        t.task = new TaskParamsYamlV2.TaskYamlV2();
        BeanUtils.copyProperties(v1.task, t.task, "function", "preFunctions", "postFunctions", "inline", "inputs", "outputs", "metas", "cache");
        t.task.function = toUp(v1.task.function);
        v1.task.preFunctions.stream().map(TaskParamsYamlUtilsV1::toUp).collect(Collectors.toCollection(()->t.task.preFunctions));
        v1.task.postFunctions.stream().map(TaskParamsYamlUtilsV1::toUp).collect(Collectors.toCollection(()->t.task.postFunctions));

        t.task.inline = v1.task.inline;
        v1.task.inputs.stream().map(TaskParamsYamlUtilsV1::upInputVariable).collect(Collectors.toCollection(()->t.task.inputs));
        v1.task.outputs.stream().map(TaskParamsYamlUtilsV1::upOutputVariable).collect(Collectors.toCollection(()->t.task.outputs));
        t.task.metas.addAll(v1.task.metas);
        if (v1.task.cache!=null) {
            t.task.cache = new TaskParamsYamlV2.CacheV2(v1.task.cache.enabled, v1.task.cache.omitInline, v1.task.cache.cacheMeta);
        }
        if (v1.task.init!=null) {
            t.task.init = new TaskParamsYamlV2.InitV2(v1.task.init.parentTaskIds, v1.task.init.nextState);
        }

        t.checkIntegrity();

        return t;
    }

    private static TaskParamsYamlV2.InputVariableV2 upInputVariable(TaskParamsYamlV1.InputVariableV1 v1) {
        TaskParamsYamlV2.InputVariableV2 v = new TaskParamsYamlV2.InputVariableV2(
                v1.id, v1.context, v1.name, v1.sourcing, v1.git, v1.disk, v1.filename, v1.type,  v1.empty, v1.getNullable(), null);
        return v;
    }

    private static TaskParamsYamlV2.OutputVariableV2 upOutputVariable(TaskParamsYamlV1.OutputVariableV1 v1) {
        TaskParamsYamlV2.OutputVariableV2 v = new TaskParamsYamlV2.OutputVariableV2(
                v1.id, v1.context, v1.name, v1.sourcing, v1.git, v1.disk, v1.filename, v1.uploaded, v1.type, v1.empty, v1.getNullable(), v1.ext);
        return v;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
    }

    private static TaskParamsYamlV2.FunctionConfigV2 toUp(TaskParamsYamlV1.FunctionConfigV1 src) {
        TaskParamsYamlV2.FunctionConfigV2 trg = new TaskParamsYamlV2.FunctionConfigV2(
                src.code, src.type, src.file, src.params, src.env, src.sourcing, src.checksumMap, src.git, CommonConsts.DEFAULT_FUNCTION_SRC_DIR, null);

        trg.metas.addAll(src.metas);
        return trg;
    }

    @Override
    public TaskParamsYamlUtilsV2 nextUtil() {
        return (TaskParamsYamlUtilsV2) TaskParamsYamlUtils.UTILS.getForVersion(2);
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
