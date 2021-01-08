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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionOutput;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * @author Serge
 * Date: 3/13/2020
 * Time: 9:03 PM
 */
public class InternalFunctionData {

    @Data
    @AllArgsConstructor
    public static class InternalFunctionProcessingResult {
        public Enums.InternalFunctionProcessing processing;
        public String error;
        public List<InternalFunctionOutput> outputs;

        public InternalFunctionProcessingResult(Enums.InternalFunctionProcessing processing) {
            this.processing = processing;
        }

        public InternalFunctionProcessingResult(Enums.InternalFunctionProcessing processing, String error) {
            this.processing = processing;
            this.error = error;
        }

        public InternalFunctionProcessingResult(List<InternalFunctionOutput> outputs) {
            this.processing = Enums.InternalFunctionProcessing.ok;
            this.outputs = outputs;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionContextData {
        public InternalFunctionProcessingResult internalFunctionProcessingResult;
        public List<ExecContextData.ProcessVertex> subProcesses;
        public ExecContextParamsYaml.Process process;
        public ExecContextParamsYaml execContextParamsYaml;
        public Set<ExecContextData.TaskVertex> descendants;

        public ExecutionContextData(InternalFunctionProcessingResult internalFunctionProcessingResult) {
            this.internalFunctionProcessingResult = internalFunctionProcessingResult;
        }
    }
}
