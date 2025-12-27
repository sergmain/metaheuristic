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

package ai.metaheuristic.commons.yaml;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlV1;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 8/8/2019
 * Time: 6:35 PM
 */
public class TestTaskParamsYaml {

    @Test
    public void test() {
        TaskParamsYamlV1 v1 = new TaskParamsYamlV1();
        final TaskParamsYamlV1.TaskYamlV1 ty = new TaskParamsYamlV1.TaskYamlV1();
        v1.task = ty;
        v1.task.taskContextId = "42";
/*
        // I left it here only for helping with GlobalVariable later
        TaskParamsYamlV1.InputVariableV1 input = new TaskParamsYamlV1.InputVariableV1("code-1", EnumsApi.VariableContext.local, EnumsApi.DataSourcing.dispatcher, null, null);
        input.resources.add(new TaskParamsYamlV1.ResourceV1(EnumsApi.VariableContext.local, "value-1-1", null));
        input.resources.add(new TaskParamsYamlV1.ResourceV1(EnumsApi.VariableContext.local, "value-1-2", null));
*/

        TaskParamsYamlV1.InputVariableV1 input = new TaskParamsYamlV1.InputVariableV1(
                42L, EnumsApi.VariableContext.local, "code-1", EnumsApi.DataSourcing.dispatcher, null, null, null, null, true, true, null);
        ty.inputs.add(input);

        TaskParamsYamlV1.OutputVariableV1 output = new TaskParamsYamlV1.OutputVariableV1(
                43L, EnumsApi.VariableContext.local, "output-code-1", EnumsApi.DataSourcing.dispatcher, null, null, null, true, null, false, false, null);
        ty.outputs.add(output);

        ty.clean = true;
        ty.inline = Map.of( ConstsApi.MH_HYPER_PARAMS, Map.of("hyper-param-key-01", "hyper-param-value-01"));
        ty.workingPath = "working-path";
        ty.timeoutBeforeTerminate = 42L;
        ty.triesAfterError = 3;


        final TaskParamsYamlV1.FunctionConfigV1 preFunction = new TaskParamsYamlV1.FunctionConfigV1();
        preFunction.code = "pre-function-code";
        preFunction.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        ty.preFunctions.add(preFunction);

        final TaskParamsYamlV1.FunctionConfigV1 function = new TaskParamsYamlV1.FunctionConfigV1();
        function.code = "function-code";
        function.sourcing = EnumsApi.FunctionSourcing.git;
        ty.function = function;

        final TaskParamsYamlV1.FunctionConfigV1 postFunction = new TaskParamsYamlV1.FunctionConfigV1();
        postFunction.code = "post-function-code";
        postFunction.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        ty.postFunctions.add(postFunction);
        ty.execContextId = 1L;
        ty.context = EnumsApi.FunctionExecContext.external;
        ty.function.file = "exec file";
        ty.processCode = "test-process-01";

        String s = TaskParamsYamlUtils.UTILS.toString(v1);
        TaskParamsYaml tpy = TaskParamsYamlUtils.UTILS.to(s);

        assertNotNull(tpy);
        assertEquals(2, tpy.version);
        assertNotNull(tpy.task);
        assertNotNull(tpy.task.preFunctions);
        assertNotNull(tpy.task.function);
        assertNotNull(tpy.task.postFunctions);
        assertNotNull(tpy.task.inline);
        assertNotNull(tpy.task.inline.get(ConstsApi.MH_HYPER_PARAMS));
        assertNotNull(tpy.task.workingPath);

        assertNotNull(tpy.task.taskContextId);
        assertEquals("42", tpy.task.taskContextId);

        assertTrue(tpy.task.clean);
        assertEquals("working-path", tpy.task.workingPath);
        assertEquals(Long.valueOf(42L), tpy.task.timeoutBeforeTerminate);
        assertEquals(3, tpy.task.triesAfterError);
        assertNotNull(tpy.task.inputs);
        assertNotNull(tpy.task.outputs);


        TaskParamsYaml.InputVariable inputVariable = tpy.task.inputs.stream().filter(o -> o.name.equals("code-1")).findFirst().orElseThrow();
        assertNotNull(inputVariable);
        assertEquals(42L, inputVariable.id);

        Map<String, String> hyperParams = tpy.task.inline.get(ConstsApi.MH_HYPER_PARAMS);
        assertEquals(1, hyperParams.size());
        assertTrue(hyperParams.containsKey("hyper-param-key-01"));
        assertEquals("hyper-param-value-01", hyperParams.get("hyper-param-key-01"));

        // test functions

        assertEquals(1, tpy.task.preFunctions.size());
        assertEquals("pre-function-code", tpy.task.preFunctions.get(0).code);
        assertEquals(EnumsApi.FunctionSourcing.dispatcher, tpy.task.preFunctions.get(0).sourcing);

        assertEquals("function-code", tpy.task.function.code);
        assertEquals(EnumsApi.FunctionSourcing.git, tpy.task.function.sourcing);

        assertEquals(1, tpy.task.postFunctions.size());
        assertEquals("post-function-code", tpy.task.postFunctions.get(0).code);
        assertEquals(EnumsApi.FunctionSourcing.dispatcher, tpy.task.postFunctions.get(0).sourcing);

    }
}
