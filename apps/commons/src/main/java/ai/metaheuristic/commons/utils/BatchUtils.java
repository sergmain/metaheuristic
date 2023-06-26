/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.BatchApiData;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import lombok.SneakyThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.commons.utils.TaskFileParamsUtils.getOutputVariableForType;

/**
 * @author Serge
 * Date: 8/26/2020
 * Time: 8:04 PM
 */
@SuppressWarnings("unused")
public class BatchUtils {

    public static BatchApiData.TaskVariables getTaskVariables(TaskFileParamsYaml params, List<String> processedType, String statusType, String mappingType) {
        return getTaskVariables(params, processedType, statusType, mappingType, false);
    }

    @SneakyThrows
    public static BatchApiData.TaskVariables getTaskVariables(TaskFileParamsYaml params, List<String> processedType, String statusType, String mappingType, boolean processAllInputs) {
        BatchApiData.TaskVariables taskVariables = new BatchApiData.TaskVariables();

        if (params.task.inputs.isEmpty()) {
            throw new IllegalStateException("(params.task.inputs.isEmpty())");
        }

        for (TaskFileParamsYaml.InputVariable input : params.task.inputs) {
            if (input.array) {
                taskVariables.inputVariables.addAll(TaskFileParamsUtils.getInputVariablesAsArray(params, input).array);
            }
            else {
                taskVariables.inputVariables.add(toInputVariable(input));
            }
            if (!processAllInputs) {
                break;
            }
        }

        taskVariables.sourceFiles = taskVariables.inputVariables.stream()
                .map(o->Path.of(params.task.workingPath, o.dataType.toString(), o.id).toFile())
                .collect(Collectors.toList());

        taskVariables.processedVars = getOutputVariableForType(params, processedType);
        taskVariables.processingStatusVar = getOutputVariableForType(params, statusType);
        String processingStatusFilename = taskVariables.processingStatusVar.id;
        taskVariables.mappingVar = getOutputVariableForType(params, mappingType);
        String mappingFilename = taskVariables.mappingVar.id;

        File artifactDir = Path.of(params.task.workingPath, ConstsApi.ARTIFACTS_DIR).toFile();

        taskVariables.processingStatusFile = new File(artifactDir, processingStatusFilename);
        taskVariables.mappingFile = new File(artifactDir, mappingFilename);

        return taskVariables;
    }

    public static VariableArrayParamsYaml.Variable toInputVariable(TaskFileParamsYaml.InputVariable v1) {
        VariableArrayParamsYaml.Variable  v = new VariableArrayParamsYaml.Variable();
        v.id = v1.id;
        v.dataType = v1.dataType;
        v.name = v1.name;
        v.disk = v1.disk;
        v.git = v1.git;
        v.sourcing = v1.sourcing;
        v.filename = v1.filename;
        return v;
    }


}
