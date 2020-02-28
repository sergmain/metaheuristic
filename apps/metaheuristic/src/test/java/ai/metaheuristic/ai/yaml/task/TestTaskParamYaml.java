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
package ai.metaheuristic.ai.yaml.task;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.TaskParamsUtils;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

//@RunWith(SpringRunner.class)
//@SpringBootTest
public class TestTaskParamYaml {

    @Test
    public void testVersion() {
        assertEquals( new TaskParamsYaml().version, TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion() );
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

    @Test
    public void testSequenceYaml() {
        TaskParamsYaml tpy = new TaskParamsYaml();

        tpy.task.inputResourceIds.put("type1", Collections.singletonList("1"));
        tpy.task.inputResourceIds.put("type2", Collections.singletonList("2"));
        tpy.task.inputResourceIds.put("type3", Collections.singletonList("3"));
        Map<String, String> map = new HashMap<>();
        map.put("key1", "#1");
        map.put("key2", "#1");
        tpy.task.taskMl = new TaskParamsYaml.TaskMachineLearning();
        tpy.task.taskMl.setHyperParams(map);
        tpy.task.setFunction(TaskParamsUtils.toFunctionConfig(new FunctionConfigYaml(
                "123:1.0",
                CommonConsts.FIT_TYPE,
                "file.txt",
                "112233",
                "python.exe",
                EnumsApi.FunctionSourcing.dispatcher,
                null,
                new FunctionConfigYaml.FunctionInfo(),
                null,
                null,
                false,
                new ArrayList<>(),
                new FunctionConfigYaml.MachineLearning(true, false)
        )));

        String s = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);
        System.out.println(s);

        assertFalse(s.startsWith("!!"));

        TaskParamsYaml seq1 = TaskParamsYamlUtils.BASE_YAML_UTILS.to(s);
        Assert.assertEquals(tpy, seq1);
    }
}
