/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package aiai.ai.yaml;

import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import aiai.api.v1.EnumsApi;
import aiai.apps.commons.CommonConsts;
import aiai.apps.commons.yaml.snippet.SnippetConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestTaskParamYaml {

    @Test
    public void testSequenceYaml() {
        TaskParamYaml seq = new TaskParamYaml();

        seq.inputResourceCodes.put("type1", Collections.singletonList("1"));
        seq.inputResourceCodes.put("type2", Collections.singletonList("2"));
        seq.inputResourceCodes.put("type3", Collections.singletonList("3"));
        Map<String, String> map = new HashMap<>();
        map.put("key1", "#1");
        map.put("key2", "#1");
        seq.setHyperParams(map);
        seq.setSnippet(new SnippetConfig(
                "123:1.0",
                CommonConsts.FIT_TYPE,
                "file.txt",
                "112233",
                "python.exe",
                EnumsApi.SnippetSourcing.launchpad,
                true,
                null,
                null,
                null,
                null,
                false
        ));

        String s = TaskParamYamlUtils.toString(seq);
        System.out.println(s);

        TaskParamYaml seq1 = TaskParamYamlUtils.toTaskYaml(s);
        Assert.assertEquals(seq, seq1);
    }
}
