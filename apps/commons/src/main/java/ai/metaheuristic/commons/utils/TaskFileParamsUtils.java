/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.commons.utils;

import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 8/26/2020
 * Time: 7:45 PM
 */
@SuppressWarnings("DuplicatedCode")
public class TaskFileParamsUtils {

    public static TaskFileParamsYaml.OutputVariable getOutputVariableForType(TaskFileParamsYaml params, String type) {
        Map<String, List<TaskFileParamsYaml.OutputVariable>> list = getOutputVariableForType(params, List.of(type));
        if (list.isEmpty()) {
            throw new RuntimeException("Output variable with type " + type+" wasn't found");
        }
        List<TaskFileParamsYaml.OutputVariable> vs = list.get(type);
        if (vs==null || vs.isEmpty()) {
            throw new RuntimeException("Output variable with type " + type+" wasn't found");
        }
        return vs.get(0);
    }

    public static Map<String, List<TaskFileParamsYaml.OutputVariable>> getOutputVariableForType(TaskFileParamsYaml params, List<String> type) {
        return params.task.outputs
                .stream()
                .filter(o-> type.contains(o.type))
                .collect(Collectors.groupingBy(TaskFileParamsYaml.OutputVariable::getType));
    }

    public static VariableArrayParamsYaml getInputVariablesAsArray(TaskFileParamsYaml params, TaskFileParamsYaml.InputVariable arrayVariable) throws IOException {
        Path arrayVariableFile = Path.of(
                params.task.workingPath, arrayVariable.dataType.toString(), arrayVariable.id);

        String arrayVariableContent = Files.readString(arrayVariableFile);
        VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(arrayVariableContent);
        return vapy;
    }


}
