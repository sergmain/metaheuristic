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

package aiai.ai.metaheuristic.commons.yaml;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYamlV1;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Serge
 * Date: 8/8/2019
 * Time: 6:35 PM
 */
public class TestTaskParamsYaml {

    @Test
    public void test() {
        TaskParamsYamlV1 v2 = new TaskParamsYamlV1();
        final TaskParamsYamlV1.TaskYamlV1 ty = new TaskParamsYamlV1.TaskYamlV1();
        v2.taskYaml = ty;
        ty.inputResourceIds = Map.of("code-1", List.of("value-1-1", "value-1-2"));
        ty.outputResourceIds = Map.of("output-code-1", "1");
        ty.resourceStorageUrls = Map.of(
                "value-1-1", new DataStorageParams(EnumsApi.DataSourcing.dispatcher),
                "value-1-2", new DataStorageParams(EnumsApi.DataSourcing.disk)
        );
        ty.clean = true;
        ty.taskMl = new TaskParamsYamlV1.TaskMachineLearningV1(Map.of("hyper-param-key-01", "hyper-param-value-01"));
        ty.workingPath = "working-path";
        ty.timeoutBeforeTerminate = 42L;


        final TaskParamsYamlV1.FunctionConfigV1 preFunction = new TaskParamsYamlV1.FunctionConfigV1();
        preFunction.code = "pre-function-code";
        preFunction.sourcing = EnumsApi.FunctionSourcing.processor;
        ty.preFunctions = List.of(preFunction);

        final TaskParamsYamlV1.FunctionConfigV1 function = new TaskParamsYamlV1.FunctionConfigV1();
        function.code = "function-code";
        function.sourcing = EnumsApi.FunctionSourcing.git;
        ty.function = function;

        final TaskParamsYamlV1.FunctionConfigV1 postFunction = new TaskParamsYamlV1.FunctionConfigV1();
        postFunction.code = "post-function-code";
        postFunction.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        ty.postFunctions = List.of(postFunction);

        String s = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(v2);
        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(s);

        assertNotNull(tpy);
        assertEquals(5, tpy.version);
        assertNotNull(tpy.taskYaml);
        assertNotNull(tpy.taskYaml.preFunctions);
        assertNotNull(tpy.taskYaml.function);
        assertNotNull(tpy.taskYaml.postFunctions);
        assertNotNull(tpy.taskYaml.taskMl);
        assertNotNull(tpy.taskYaml.taskMl.hyperParams);
        assertNotNull(tpy.taskYaml.workingPath);

        assertTrue(tpy.taskYaml.clean);
        assertEquals("working-path", tpy.taskYaml.workingPath);
        assertEquals(Long.valueOf(42L), tpy.taskYaml.timeoutBeforeTerminate);
        assertNotNull(tpy.taskYaml.inputResourceIds);
        assertNotNull(tpy.taskYaml.outputResourceIds);
        assertNotNull(tpy.taskYaml.resourceStorageUrls);


        assertNotNull(tpy.taskYaml.inputResourceIds.get("code-1"));
        assertTrue(tpy.taskYaml.inputResourceIds.get("code-1").contains("value-1-1"));
        assertTrue(tpy.taskYaml.inputResourceIds.get("code-1").contains("value-1-2"));
        ty.outputResourceIds = Map.of("output-code-1", "1");
        ty.resourceStorageUrls = Map.of(
                "value-1-1", new DataStorageParams(EnumsApi.DataSourcing.dispatcher),
                "value-1-2", new DataStorageParams(EnumsApi.DataSourcing.disk)
        );

        assertEquals(1, tpy.taskYaml.taskMl.hyperParams.size());
        assertTrue(tpy.taskYaml.taskMl.hyperParams.containsKey("hyper-param-key-01"));
        assertEquals("hyper-param-value-01", tpy.taskYaml.taskMl.hyperParams.get("hyper-param-key-01"));

        // test functions

        assertEquals(1, tpy.taskYaml.preFunctions.size());
        assertEquals("pre-function-code", tpy.taskYaml.preFunctions.get(0).code);
        assertEquals(EnumsApi.FunctionSourcing.processor, tpy.taskYaml.preFunctions.get(0).sourcing);

        assertEquals("function-code", tpy.taskYaml.function.code);
        assertEquals(EnumsApi.FunctionSourcing.git, tpy.taskYaml.function.sourcing);

        assertEquals(1, tpy.taskYaml.postFunctions.size());
        assertEquals("post-function-code", tpy.taskYaml.postFunctions.get(0).code);
        assertEquals(EnumsApi.FunctionSourcing.dispatcher, tpy.taskYaml.postFunctions.get(0).sourcing);

    }
}
