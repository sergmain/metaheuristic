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

package ai.metaheuristic.commons.yaml.task_file;

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
 * Date: 2/16/2022
 * Time: 12:10 AM
 */
@SuppressWarnings("DuplicatedCode")
public class TaskFileParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<TaskFileParamsYamlV2, TaskFileParamsYaml, Void,
        TaskFileParamsYamlV1, TaskFileParamsYamlUtilsV1, TaskFileParamsYaml> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(TaskFileParamsYamlV2.class);
    }

    @NonNull
    @Override
    public TaskFileParamsYaml upgradeTo(@NonNull TaskFileParamsYamlV2 v1) {
        v1.checkIntegrity();
        TaskFileParamsYaml t = new TaskFileParamsYaml();
        t.task = new TaskFileParamsYaml.Task();
        BeanUtils.copyProperties(v1.task, t.task, "inline", "input", "output");
        t.task.inline = v1.task.inline;
        v1.task.inputs.stream().map(TaskFileParamsYamlUtilsV2::upInputVariable).collect(Collectors.toCollection(()->t.task.inputs));
        v1.task.outputs.stream().map(TaskFileParamsYamlUtilsV2::upOutputVariable).collect(Collectors.toCollection(()->t.task.outputs));

        t.checkIntegrity();
        return t;
    }

    public static TaskFileParamsYaml.InputVariable upInputVariable(TaskFileParamsYamlV2.InputVariableV2 v2) {
        TaskFileParamsYaml.InputVariable v = new TaskFileParamsYaml.InputVariable();
        v.id = v2.id;
        v.name = v2.name;
        v.dataType = v2.dataType;
        v.sourcing = v2.sourcing;
        v.git = v2.git;
        v.disk = v2.disk;
        v.filename = v2.filename;
        v.type = v2.type;
        v.empty = v2.empty;
        v.setNullable(v2.getNullable());
        v.array = v2.array;
        return v;
    }

    private static TaskFileParamsYaml.OutputVariable upOutputVariable(TaskFileParamsYamlV2.OutputVariableV2 v2) {
        TaskFileParamsYaml.OutputVariable v = new TaskFileParamsYaml.OutputVariable();
        v.id = v2.id;
        v.name = v2.name;
        v.dataType = v2.dataType;
        v.sourcing = v2.sourcing;
        v.git = v2.git;
        v.disk = v2.disk;
        v.filename = v2.filename;
        v.type = v2.type;
        v.empty = v2.empty;
        v.setNullable(v2.getNullable());
        return v;
    }

    @NonNull
    @Override
    public TaskFileParamsYamlV1 downgradeTo(@NonNull TaskFileParamsYaml v2) {
        v2.checkIntegrity();

        TaskFileParamsYamlV1 t1 = new TaskFileParamsYamlV1();
        t1.task = new TaskFileParamsYamlV1.TaskV1();
        BeanUtils.copyProperties(v2.task, t1.task, "inline", "input", "output");
        t1.task.inline = v2.task.inline;
        v2.task.inputs.stream().map(TaskFileParamsYamlUtilsV2::downInputVariable).collect(Collectors.toCollection(()->t1.task.inputs));
        v2.task.outputs.stream().map(TaskFileParamsYamlUtilsV2::downOutputVariable).collect(Collectors.toCollection(()->t1.task.outputs));

        t1.checkIntegrity();
        return t1;
    }

    private static TaskFileParamsYamlV1.InputVariableV1 downInputVariable(TaskFileParamsYaml.InputVariable v2) {
        TaskFileParamsYamlV1.InputVariableV1 v = new TaskFileParamsYamlV1.InputVariableV1();
        v.id = v2.id;
        v.name = v2.name;
        v.dataType = v2.dataType;
        v.sourcing = v2.sourcing;
        v.git = v2.git;
        v.disk = v2.disk;
        v.filename = v2.filename;
        v.type = v2.type;
        v.empty = v2.empty;
        v.setNullable(v2.getNullable());
        return v;
    }

    private static TaskFileParamsYamlV1.OutputVariableV1 downOutputVariable(TaskFileParamsYaml.OutputVariable v2) {
        TaskFileParamsYamlV1.OutputVariableV1 v = new TaskFileParamsYamlV1.OutputVariableV1();
        v.id = v2.id;
        v.name = v2.name;
        v.dataType = v2.dataType;
        v.sourcing = v2.sourcing;
        v.git = v2.git;
        v.disk = v2.disk;
        v.filename = v2.filename;
        v.type = v2.type;
        v.empty = v2.empty;
        v.setNullable(v2.getNullable());
        return v;
    }


    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public TaskFileParamsYamlUtilsV1 prevUtil() {
        return (TaskFileParamsYamlUtilsV1) TaskFileParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1);
    }

    @Override
    public String toString(@NonNull TaskFileParamsYamlV2 params) {
        return getYaml().dump(params);
    }

    @NonNull
    @Override
    public TaskFileParamsYamlV2 to(@NonNull String yaml) {
        if (S.b(yaml)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final TaskFileParamsYamlV2 p = getYaml().load(yaml);
        return p;
    }

}
