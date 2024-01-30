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
package ai.metaheuristic.ai.yaml.task_params_new;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlV1;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
public class TestTaskParamYamlWithoutRef {

    @Test
    public void testTaskFileParamYaml() throws IOException {
        String yaml = IOUtils.resourceToString("/yaml/task_file_params/params-v1.yaml", StandardCharsets.UTF_8);

        final AbstractParamsYamlUtils forVersion = TaskFileParamsYamlUtils.BASE_YAML_UTILS.getForVersion(1);
        assertNotNull(forVersion);

        TaskFileParamsYamlV1 taskParam = (TaskFileParamsYamlV1)forVersion.to(yaml);

        String s = TaskFileParamsYamlUtils.BASE_YAML_UTILS.toString(taskParam);

        System.out.println("s = \n" + s);
        assertFalse(s.contains("&id001"));

    }

    @Test
    public void createTaskFileParamYamlAndCheckDowngrading() {
        TaskFileParamsYaml params = new TaskFileParamsYaml();
        assertNotEquals(1, params.version);

        params.task = new TaskFileParamsYaml.Task();
        TaskFileParamsYaml.Task t = params.task;
        t.clean = true;
        t.execContextId = 42L;
        t.inline = Map.of(ConstsApi.MH_HYPER_PARAMS, Map.of("k1", "vInput1", "k2", "v2"));
        TaskFileParamsYaml.InputVariable vInput1 = new TaskFileParamsYaml.InputVariable(1122L, "vInput1", EnumsApi.DataSourcing.dispatcher);
        t.inputs.add(vInput1);

        TaskFileParamsYaml.OutputVariable vOutput1 =
                new TaskFileParamsYaml.OutputVariable(1133L, "vOutput1", EnumsApi.DataSourcing.dispatcher);

        t.outputs.add(vOutput1);

        String yaml = TaskFileParamsYamlUtils.BASE_YAML_UTILS.toString(params);

        System.out.println(yaml);

        assertDoesNotThrow(()->TaskFileParamsYamlUtils.BASE_YAML_UTILS.toStringAsVersion(params, 1));
    }

}
