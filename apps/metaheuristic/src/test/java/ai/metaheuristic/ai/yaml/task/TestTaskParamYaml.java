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
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.CommonConsts;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;

//@RunWith(SpringRunner.class)
//@SpringBootTest
public class TestTaskParamYaml {

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
        seq.setSnippet(new SnippetApiData.SnippetConfig(
                "123:1.0",
                CommonConsts.FIT_TYPE,
                "file.txt",
                "112233",
                "python.exe",
                EnumsApi.SnippetSourcing.launchpad,
                true,
                null,
                new SnippetApiData.SnippetConfig.SnippetInfo(),
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
        TaskParamsYaml seq = new TaskParamsYaml();

        seq.taskYaml.inputResourceCodes.put("type1", Collections.singletonList("1"));
        seq.taskYaml.inputResourceCodes.put("type2", Collections.singletonList("2"));
        seq.taskYaml.inputResourceCodes.put("type3", Collections.singletonList("3"));
        Map<String, String> map = new HashMap<>();
        map.put("key1", "#1");
        map.put("key2", "#1");
        seq.taskYaml.setHyperParams(map);
        seq.taskYaml.setSnippet(new SnippetApiData.SnippetConfig(
                "123:1.0",
                CommonConsts.FIT_TYPE,
                "file.txt",
                "112233",
                "python.exe",
                EnumsApi.SnippetSourcing.launchpad,
                true,
                null,
                new SnippetApiData.SnippetConfig.SnippetInfo(),
                null,
                null,
                false,
                new ArrayList<>()
        ));

        String s = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(seq);
        System.out.println(s);

        assertFalse(s.startsWith("!!"));

        TaskParamsYaml seq1 = TaskParamsYamlUtils.BASE_YAML_UTILS.to(s);
        Assert.assertEquals(seq, seq1);
    }
}
