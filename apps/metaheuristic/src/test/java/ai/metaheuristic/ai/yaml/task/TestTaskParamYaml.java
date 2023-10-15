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
package ai.metaheuristic.ai.yaml.task;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.task.TaskParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

//@ExtendWith(SpringExtension.class)
//@SpringBootTest
public class TestTaskParamYaml {

    @Test
    public void testVersion() {
        assertEquals( new TaskParamsYaml().version, TaskParamsYamlUtils.UTILS.getDefault().getVersion() );
    }

    @Test
    public void testSequenceYaml_v1() {
/*
        TaskParamsYamlV1 seq = new TaskParamsYamlV1();

        seq.inputResourceCodes.put("type1", Collections.singletonList("1"));
        seq.inputResourceCodes.put("type2", Collections.singletonList("2"));
        seq.inputResourceCodes.put("type3", Collections.singletonList("3"));
        Map<String, String> map = new HashMap<>();
        map.put("key1", "#1");
        map.put("key2", "#1");
        seq.setHyperParams(map);
        seq.setFunction(new FunctionApiData.FunctionConfig(
                "123:1.0",
                CommonConsts.FIT_TYPE,
                "file.txt",
                "112233",
                "python.exe",
                EnumsApi.FunctionSourcing.dispatcher,
                true,
                null,
                new FunctionApiData.FunctionConfig.FunctionInfo(),
                null,
                null,
                false
        ));

        String s = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(seq);
        System.out.println(s);

        TaskParamsYaml seq1 = TaskParamsYamlUtils.BASE_YAML_UTILS.to(s);
        Assert.assertEquals(seq, seq1);
*/
    }

    private TaskParamsYaml.InputVariable newVariable(String name, Long variableId) {
        TaskParamsYaml.InputVariable v1 = new TaskParamsYaml.InputVariable(
                variableId, EnumsApi.VariableContext.local, name, EnumsApi.DataSourcing.dispatcher, null, null, null, null, false, false);
        return v1;
    }

    @Test
    public void testTaskParamsYaml() {
        TaskParamsYaml tpy = new TaskParamsYaml();
        tpy.task.execContextId = 42L;
        tpy.task.taskContextId = "4242";
        tpy.task.processCode = "test-process";
        tpy.task.context = EnumsApi.FunctionExecContext.external;
        tpy.task.inputs.add(newVariable("type1", 1L));
        tpy.task.inputs.add(newVariable("type2", 2L));
        tpy.task.inputs.add(newVariable("type3", 3L));
        tpy.task.inline = Map.of(ConstsApi.MH_HYPER_PARAMS, Map.of("key1", "#1", "key2", "#1"));

        tpy.task.setFunction(TaskParamsUtils.toFunctionConfig(
            new FunctionConfigYaml(
                new FunctionConfigYaml.FunctionConfig(
                    "123:1.0",
                    CommonConsts.FIT_TYPE,
                    "file.txt",
                    "112233",
                    "python",
                    EnumsApi.FunctionSourcing.dispatcher,
                    null,
                    List.of()
                ),
                null)
            )
        );

        String s = TaskParamsYamlUtils.UTILS.toString(tpy);
        System.out.println(s);

        assertFalse(s.startsWith("!!"));

        TaskParamsYaml seq1 = TaskParamsYamlUtils.UTILS.to(s);
        assertEquals(tpy, seq1);
    }
}
