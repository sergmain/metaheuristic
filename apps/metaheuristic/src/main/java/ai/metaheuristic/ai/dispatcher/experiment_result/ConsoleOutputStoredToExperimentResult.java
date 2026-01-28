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

package ai.metaheuristic.ai.dispatcher.experiment_result;

import ai.metaheuristic.api.data.BaseDataClass;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class ConsoleOutputStoredToExperimentResult extends BaseDataClass {

    @Data
    @NoArgsConstructor
    public static class TaskOutput {
        public long taskId;
        public String console;
    }

    @JsonCreator
    public ConsoleOutputStoredToExperimentResult(
        @JsonProperty("errorMessages") @Nullable List<String> errorMessages,
        @JsonProperty("infoMessages") @Nullable List<String> infoMessages) {
        this.errorMessages = errorMessages;
        this.infoMessages = infoMessages;
    }

    public ConsoleOutputStoredToExperimentResult(String errorMessage) {
        this.errorMessages = Collections.singletonList(errorMessage);
    }

    public ConsoleOutputStoredToExperimentResult(Path dumpOfConsoleOutputs) {
        this.dumpOfConsoleOutputs = dumpOfConsoleOutputs;
    }

    public Path dumpOfConsoleOutputs;
}
