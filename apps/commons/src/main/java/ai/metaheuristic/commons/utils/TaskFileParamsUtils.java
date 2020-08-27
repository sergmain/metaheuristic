/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * @author Serge
 * Date: 8/26/2020
 * Time: 7:45 PM
 */
@SuppressWarnings("DuplicatedCode")
public class TaskFileParamsUtils {

    public static TaskFileParamsYaml.OutputVariable getOutputVariableForType(TaskFileParamsYaml params, String type) {
        return params.task.outputs
                .stream()
                .filter(o-> type.equals(o.type))
                .findFirst()
                .orElseThrow();
    }

    public static VariableArrayParamsYaml getInputVariablesAsArray(TaskFileParamsYaml params, TaskFileParamsYaml.InputVariable arrayVariable) throws IOException {
        File arrayVariableFile = Path.of(
                params.task.workingPath, arrayVariable.dataType.toString(), arrayVariable.id).toFile();

        String arrayVariableContent = FileUtils.readFileToString(arrayVariableFile, StandardCharsets.UTF_8);
        VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(arrayVariableContent);
        return vapy;
    }


}
