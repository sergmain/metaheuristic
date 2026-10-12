/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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
package ai.metaheuristic.ai.processor.variable_providers;

import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import lombok.SneakyThrows;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * The contract between an external Function and the Processor for an output Variable. The Function leaves the
 * Variable in the Task dir as file artifacts/&lt;variableId&gt;, and:
 * <ol>
 *     <li>a non-nullable output Variable - the file must exist;</li>
 *     <li>a nullable output Variable - the file may be absent, the Variable is then reported as null;</li>
 *     <li>an existing file must be longer than 0 bytes - a Function must never create a zero-byte output Variable,
 *     nullable or not.</li>
 * </ol>
 * A violation is the Function's error: the Processor fails the Task with it and uploads nothing for that Variable.
 * Before this check a zero-byte file was uploaded, the Dispatcher refused it, and the Task stayed IN_PROGRESS forever.
 *
 * <p>Error code prefix: {@code 01.330.} (unique to this class).
 */
public class OutputVariableContractUtils {

    /**
     * @param outputVariableFile the file the Function had to leave for the output Variable
     * @return the violation - the error the Task fails with - or null when the output Variable keeps the contract
     */
    @SneakyThrows
    @Nullable
    public static String findViolation(Path outputVariableFile, TaskParamsYaml.OutputVariable outputVariable) {
        if (!Files.exists(outputVariableFile)) {
            if (Boolean.TRUE.equals(outputVariable.getNullable())) {
                return null;
            }
            return "01.330.010 output Variable #" + outputVariable.id + " violates contract, variable '" + outputVariable.name + "': " +
                   "it isn't nullable, but the Function didn't create its file " + outputVariableFile.toAbsolutePath();
        }
        if (Files.size(outputVariableFile)==0) {
            return "01.330.020 output Variable #" + outputVariable.id + " violates contract, variable '" + outputVariable.name + "': " +
                   "the Function created its file " + outputVariableFile.toAbsolutePath() + " with zero bytes. " +
                   "An existing file must be longer than 0 bytes, a nullable output Variable is reported as null by not creating the file";
        }
        return null;
    }

    /**
     * Processes the output Variables in order and stops at the first failed result, so a contract violation of one
     * output Variable isn't overwritten by the result of the next one - the Task fails with it and the remaining
     * output Variables aren't uploaded. Production passes the {@link VariableProvider} of each Variable's sourcing.
     *
     * @return the first failed result; when nothing failed, the result of the last output Variable (null for none)
     */
    public static FunctionApiData.@Nullable SystemExecResult processOutputVariables(
            List<TaskParamsYaml.OutputVariable> outputVariables,
            Function<TaskParamsYaml.OutputVariable, FunctionApiData.@Nullable SystemExecResult> outputVariableProcessor) {
        FunctionApiData.SystemExecResult result = null;
        for (TaskParamsYaml.OutputVariable outputVariable : outputVariables) {
            result = outputVariableProcessor.apply(outputVariable);
            if (result!=null && !result.isOk) {
                return result;
            }
        }
        return result;
    }
}
