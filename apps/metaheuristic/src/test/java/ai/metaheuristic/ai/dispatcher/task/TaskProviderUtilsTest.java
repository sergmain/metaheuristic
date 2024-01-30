/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Sergio Lissner
 * Date: 5/26/2023
 * Time: 1:22 PM
 */
public class TaskProviderUtilsTest {

    @Test
    public void test_initEmptiness() {
        TaskParamsYaml tpy = new TaskParamsYaml();
        final TaskParamsYaml.InputVariable iv = new TaskParamsYaml.InputVariable();
        iv.context= EnumsApi.VariableContext.global;
        iv.name = "global-test-variable";
        tpy.task.processCode = "assembly-raw-file";
        tpy.task.execContextId = 41L;
        tpy.task.function = new TaskParamsYaml.FunctionConfig();
        tpy.task.function.code = "function-01:1.1";

/*
    - code: assembly-raw-file
      name: assembly raw file
      function:
        code: function-01:1.1
      inputs:
        - name: global-test-variable
      outputs:
        - name: assembled-raw-output
*/
        tpy.task.inputs.add(iv);
        String taskParams = TaskParamsYamlUtils.UTILS.toString(tpy);
        final List<String> events = new ArrayList<>();

        final String params = TaskProviderUtils.initEmptiness(2L, new TaskParamsYaml().version, taskParams, 42L,
                (id)->null, (event) -> {events.add(event.toString());});
        assertNotNull(params);
        assertTrue(events.isEmpty());
    }
}
