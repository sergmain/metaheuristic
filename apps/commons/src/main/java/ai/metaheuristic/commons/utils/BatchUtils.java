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

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.data.BatchApiData;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import lombok.SneakyThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static ai.metaheuristic.commons.utils.TaskFileParamsUtils.getOutputVariableForType;

/**
 * @author Serge
 * Date: 8/26/2020
 * Time: 8:04 PM
 */
@SuppressWarnings("unused")
public class BatchUtils {

    @SneakyThrows
    public static BatchApiData.TaskVariables getTaskVariables(TaskFileParamsYaml params, String processedType, String statusType, String mappingType) {
        BatchApiData.TaskVariables taskVariables = new BatchApiData.TaskVariables();

        TaskFileParamsYaml.InputVariable arrayVariable = params.task.inputs.get(0);
        taskVariables.inputVariables = TaskFileParamsUtils.getInputVariablesAsArray(params, arrayVariable).array;

        taskVariables.sourceFiles = taskVariables.inputVariables.stream()
                .map(o->Path.of(params.task.workingPath, o.dataType.toString(), o.id).toFile())
                .collect(Collectors.toList());

        taskVariables.processedVar = getOutputVariableForType(params, processedType);
        String processedFilename = taskVariables.processedVar.id;
        taskVariables.processingStatusVar = getOutputVariableForType(params, statusType);
        String processingStatusFilename = taskVariables.processingStatusVar.id;
        taskVariables.mappingVar = getOutputVariableForType(params, mappingType);
        String mappingFilename = taskVariables.mappingVar.id;

        File artifactDir = Path.of(params.task.workingPath, ConstsApi.ARTIFACTS_DIR).toFile();

        taskVariables.processedFile = new File(artifactDir, processedFilename);
        taskVariables.processingStatusFile = new File(artifactDir, processingStatusFilename);
        taskVariables.mappingFile = new File(artifactDir, mappingFilename);

        return taskVariables;
    }
}
