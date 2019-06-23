/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
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

package ai.metaheuristic.ai.launchpad.atlas;

import ai.metaheuristic.api.data.BaseDataClass;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.Collections;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class ConsoleOutputStoredToAtlas extends BaseDataClass {

    @Data
    @NoArgsConstructor
    public static class TaskOutput {
        public long taskId;
        public String console;
    }

    public ConsoleOutputStoredToAtlas(String errorMessage) {
        this.errorMessages = Collections.singletonList(errorMessage);
    }

    public ConsoleOutputStoredToAtlas(File dumpOfConsoleOutputs) {
        this.dumpOfConsoleOutputs = dumpOfConsoleOutputs;
    }

    public File dumpOfConsoleOutputs;
}
