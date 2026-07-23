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
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 8/08/2019
 * Time: 12:10 AM
 */
@SuppressWarnings("DuplicatedCode")
public class TaskParamsYamlUtilsV3
        extends AbstractParamsYamlUtils<TaskParamsYamlV3, TaskParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 3;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(TaskParamsYamlV3.class);
    }

    @NonNull
    @Override
    public TaskParamsYaml upgradeTo(@NonNull TaskParamsYamlV3 v1) {
        v1.checkIntegrity();
        TaskParamsYaml t = new TaskParamsYaml();
        t.task = new TaskParamsYaml.TaskYaml();
        BeanUtils.copyProperties(v1.task, t.task, "function", "preFunctions", "postFunctions", "inline", "inputs", "outputs", "metas", "cache");
        t.task.function = toUp(v1.task.function);
        v1.task.preFunctions.stream().map(TaskParamsYamlUtilsV3::toUp).collect(Collectors.toCollection(()->t.task.preFunctions));
        v1.task.postFunctions.stream().map(TaskParamsYamlUtilsV3::toUp).collect(Collectors.toCollection(()->t.task.postFunctions));

        t.task.inline = v1.task.inline;
        v1.task.inputs.stream().map(TaskParamsYamlUtilsV3::upInputVariable).collect(Collectors.toCollection(()->t.task.inputs));
        v1.task.outputs.stream().map(TaskParamsYamlUtilsV3::upOutputVariable).collect(Collectors.toCollection(()->t.task.outputs));
        t.task.metas.addAll(v1.task.metas);
        if (v1.task.cache!=null) {
            t.task.cache = new TaskParamsYaml.Cache(v1.task.cache.enabled, v1.task.cache.omitInline, v1.task.cache.cacheMeta);
        }
        if (v1.task.init!=null) {
            t.task.init = new TaskParamsYaml.Init(v1.task.init.parentTaskIds, v1.task.init.nextState);
        }

        // Stage 5: copy top-level companyId (greenfield exception — no bump).
        t.companyId = v1.companyId;

        t.checkIntegrity();

        return t;
    }

    private static TaskParamsYaml.InputVariable upInputVariable(TaskParamsYamlV3.InputVariableV3 v1) {
        return new TaskParamsYaml.InputVariable(
                v1.id, v1.context, v1.name, v1.sourcing, v1.git, v1.disk, v1.filename, v1.type, v1.empty, v1.getNullable(), v1.ext);
    }

    private static TaskParamsYaml.OutputVariable upOutputVariable(TaskParamsYamlV3.OutputVariableV3 v1) {
        return new TaskParamsYaml.OutputVariable(
                v1.id, v1.context, v1.name, v1.sourcing, v1.git, v1.disk, v1.filename, v1.uploaded, v1.type, v1.empty, v1.getNullable(), v1.ext);
    }

    private static TaskParamsYaml.FunctionConfig toUp(TaskParamsYamlV3.FunctionConfigV3 src) {
        TaskParamsYaml.FunctionConfig trg = new TaskParamsYaml.FunctionConfig();
        trg.code = src.code;
        trg.type = src.type;
        trg.params = src.params;
        trg.env = src.env;
        trg.sourcing = src.sourcing;
        trg.checksumMap = src.checksumMap;
        trg.git = src.git;
        trg.assetDir = src.assetDir;
        trg.cleaningPolicy = src.cleaningPolicy;
        if (src.api != null) {
            trg.api = new TaskParamsYaml.Api(src.api.keyCode);
        }
        for (Map.Entry<String, TaskParamsYamlV3.TargetV3> e : src.targets.entrySet()) {
            trg.targets.put(e.getKey(), new TaskParamsYaml.Target(e.getValue().src, e.getValue().file));
        }
        trg.metas.addAll(src.metas);
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        throw new DowngradeNotSupportedException();
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
    public String toString(@NonNull TaskParamsYamlV3 params) {
        params.checkIntegrity();
        return getYaml().dump(params);
    }

    @NonNull
    @Override
    public TaskParamsYamlV3 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final TaskParamsYamlV3 p = getYaml().load(yaml);
        return p;
    }

}
