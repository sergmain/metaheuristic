/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.Collections;

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

    public ConsoleOutputStoredToExperimentResult(String errorMessage) {
        this.errorMessages = Collections.singletonList(errorMessage);
    }

    public ConsoleOutputStoredToExperimentResult(File dumpOfConsoleOutputs) {
        this.dumpOfConsoleOutputs = dumpOfConsoleOutputs;
    }

    public File dumpOfConsoleOutputs;
}
