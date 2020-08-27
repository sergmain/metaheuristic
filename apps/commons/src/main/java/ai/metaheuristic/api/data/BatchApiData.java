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

package ai.metaheuristic.api.data;

import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import lombok.Data;

import java.io.File;
import java.util.List;

/**
 * @author Serge
 * Date: 8/26/2020
 * Time: 8:05 PM
 */
public class BatchApiData {

    @Data
    public static class TaskVariables {
        public List<VariableArrayParamsYaml.Variable> inputVariables;
        public List<File> sourceFiles;

        public TaskFileParamsYaml.OutputVariable processedVar;
        public TaskFileParamsYaml.OutputVariable processingStatusVar;
        public TaskFileParamsYaml.OutputVariable mappingVar;

        public File processedFile;
        public File processingStatusFile;
        public File mappingFile;
    }
}
