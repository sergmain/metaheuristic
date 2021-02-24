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

package ai.metaheuristic.commons.yaml.task_file;

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
public class TaskFileParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<TaskFileParamsYamlV1, TaskFileParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(TaskFileParamsYamlV1.class);
    }

    @NonNull
    @Override
    public TaskFileParamsYaml upgradeTo(@NonNull TaskFileParamsYamlV1 v1) {
        v1.checkIntegrity();
        TaskFileParamsYaml t = new TaskFileParamsYaml();
        t.task = new TaskFileParamsYaml.Task();
        BeanUtils.copyProperties(v1.task, t.task, "inline", "input", "output");
        t.task.inline = v1.task.inline;
        v1.task.inputs.stream().map(TaskFileParamsYamlUtilsV1::upInputVariable).collect(Collectors.toCollection(()->t.task.inputs));
        v1.task.outputs.stream().map(TaskFileParamsYamlUtilsV1::upOutputVariable).collect(Collectors.toCollection(()->t.task.outputs));

        t.checkIntegrity();
        return t;
    }

    private static TaskFileParamsYaml.InputVariable upInputVariable(TaskFileParamsYamlV1.InputVariableV1 v1) {
        TaskFileParamsYaml.InputVariable v = new TaskFileParamsYaml.InputVariable();
        v.id = v1.id;
        v.name = v1.name;
        v.dataType = v1.dataType;
        v.sourcing = v1.sourcing;
        v.git = v1.git;
        v.disk = v1.disk;
        v.filename = v1.filename;
        v.type = v1.type;
        v.empty = v1.empty;
        v.setNullable(v1.getNullable());
        return v;
    }

    private static TaskFileParamsYaml.OutputVariable upOutputVariable(TaskFileParamsYamlV1.OutputVariableV1 v1) {
        TaskFileParamsYaml.OutputVariable v = new TaskFileParamsYaml.OutputVariable();
        v.id = v1.id;
        v.name = v1.name;
        v.dataType = v1.dataType;
        v.sourcing = v1.sourcing;
        v.git = v1.git;
        v.disk = v1.disk;
        v.filename = v1.filename;
        v.type = v1.type;
        v.empty = v1.empty;
        v.setNullable(v1.getNullable());
        return v;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
        return null;
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
    public String toString(@NonNull TaskFileParamsYamlV1 params) {
        return getYaml().dump(params);
    }

    @NonNull
    @Override
    public TaskFileParamsYamlV1 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final TaskFileParamsYamlV1 p = getYaml().load(yaml);
        return p;
    }

}
