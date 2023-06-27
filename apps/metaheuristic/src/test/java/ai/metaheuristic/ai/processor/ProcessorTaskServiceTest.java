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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.yaml.processor_task.ProcessorCoreTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author Sergio Lissner
 * Date: 6/26/2023
 * Time: 8:27 PM
 */
public class ProcessorTaskServiceTest {

    @Test
    public void test_actualSave(@TempDir Path temp) {
        ProcessorCoreTask task = new ProcessorCoreTask();
        Path taskDir = temp;

        Path taskYaml = temp.resolve("taskYaml.yaml");
        assertDoesNotThrow(()->ProcessorTaskService.actualSave(task, taskDir, taskYaml));
    }
}
