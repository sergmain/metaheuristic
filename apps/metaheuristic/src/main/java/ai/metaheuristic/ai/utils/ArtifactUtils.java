/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.utils.FileSystemUtils;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 1/27/2021
 * Time: 1:28 AM
 */
@Slf4j
public class ArtifactUtils {

    /**
     *
     * @param artifactDir
     * @param taskDir
     * @param taskParamYaml
     * @param versions
     * @return boolean - true if Ok
     */
    public static boolean prepareParamsFileForTask(File artifactDir, String taskDir, TaskParamsYaml taskParamYaml, Set<Integer> versions) {
        TaskFileParamsYaml taskFileParamYaml = toTaskFileParamsYaml(taskParamYaml);
        taskFileParamYaml.task.workingPath = taskDir;
        for (Integer version : versions) {

            final String params = TaskFileParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(taskFileParamYaml, version);

            // persist params.yaml file
            File paramFile = new File(artifactDir, String.format(Consts.PARAMS_YAML_MASK, version));
            if (paramFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                paramFile.delete();
            }

            try {
                FileSystemUtils.writeStringToFileWithSync(paramFile, params, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("#103.020 Error with writing to " + paramFile.getAbsolutePath() + " file", e);
                return false;
            }
        }
        return true;
    }

    private static TaskFileParamsYaml toTaskFileParamsYaml(TaskParamsYaml v1) {
        TaskFileParamsYaml t = new TaskFileParamsYaml();
        t.task = new TaskFileParamsYaml.Task();
        t.task.execContextId = v1.task.execContextId;
        t.task.clean = v1.task.clean;
        t.task.workingPath = v1.task.workingPath;

        t.task.inline = v1.task.inline;
        v1.task.inputs.stream().map(ArtifactUtils::upInputVariable).collect(Collectors.toCollection(()->t.task.inputs));
        v1.task.outputs.stream().map(ArtifactUtils::upOutputVariable).collect(Collectors.toCollection(()->t.task.outputs));

        t.checkIntegrity();
        return t;
    }

    private static EnumsApi.DataType toType(EnumsApi.VariableContext context) {
        switch(context) {
            case global:
                return EnumsApi.DataType.global_variable;
            case local:
            case array:
                return EnumsApi.DataType.variable;
            default:
                throw new IllegalStateException("#103.040 wrong context: " + context);
        }
    }

    private static TaskFileParamsYaml.InputVariable upInputVariable(TaskParamsYaml.InputVariable v1) {
        TaskFileParamsYaml.InputVariable  v = new TaskFileParamsYaml.InputVariable();
        v.id = v1.id.toString();
        v.dataType = toType(v1.context);
        v.name = v1.name;
        v.disk = v1.disk;
        v.git = v1.git;
        v.sourcing = v1.sourcing;
        v.filename = v1.filename;
        v.type = v1.type;
        v.empty = v1.empty;
        v.setNullable(v1.getNullable());
        return v;
    }

    private static TaskFileParamsYaml.OutputVariable upOutputVariable(TaskParamsYaml.OutputVariable v1) {
        TaskFileParamsYaml.OutputVariable v = new TaskFileParamsYaml.OutputVariable();
        v.id = v1.id.toString();
        v.name = v1.name;
        v.dataType = toType(v1.context);
        v.disk = v1.disk;
        v.git = v1.git;
        v.sourcing = v1.sourcing;
        v.filename = v1.filename;
        v.type = v1.type;
        v.empty = v1.empty;
        v.setNullable(v1.getNullable());
        return v;
    }
}
