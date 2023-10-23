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

package ai.metaheuristic.ai.yaml.task_file;

import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlV2;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * @author Sergio Lissner
 * Date: 8/23/2022
 * Time: 5:41 PM
 */
@Execution(CONCURRENT)
public class TaskFileParamsYamlTest {

    @Test
    public void test_0() {

        int lastVersion = TaskFileParamsYamlUtils.DEFAULT_UTILS.getVersion();

        for (int i = 1; i <= lastVersion; i++) {
            final AbstractParamsYamlUtils forVersion = TaskFileParamsYamlUtils.BASE_YAML_UTILS.getForVersion(i);
            assertNotNull(forVersion);
            assertEquals(i, forVersion.getVersion());
        }
    }

    @Test
    public void test_1() {

        TaskFileParamsYamlV2.InputVariableV2 inV2 = new TaskFileParamsYamlV2.InputVariableV2();
        inV2.array = true;
        TaskFileParamsYamlV2 v2 = new TaskFileParamsYamlV2();
        v2.task.inputs.add(inV2);

        String s = TaskFileParamsYamlUtils.BASE_YAML_UTILS.toString(v2);
        System.out.println(s);
        assertTrue(s.contains("version: 2"));

        TaskFileParamsYaml v = TaskFileParamsYamlUtils.BASE_YAML_UTILS.to(s);

        assertFalse(v.task.inputs.isEmpty());
        assertTrue(v.task.inputs.get(0).array);
    }
}
