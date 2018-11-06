/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.yaml;

import aiai.ai.yaml.sequence.SimpleSnippet;
import aiai.ai.yaml.sequence.TaskParamYaml;
import aiai.ai.yaml.sequence.TaskParamYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestTaskParamYaml {

    @Autowired
    private TaskParamYamlUtils taskParamYamlUtils;

    @Test
    public void testSequenceYaml() {
        TaskParamYaml seq = new TaskParamYaml();
        seq.inputResourceCodes = Arrays.asList("1", "2", "3");
        Map<String, String> map = new HashMap<>();
        map.put("key1", "#1");
        map.put("key2", "#1");
        seq.setHyperParams(map);
        seq.setSnippet(new SimpleSnippet(SnippetType.fit.toString(), "123", "file.txt", "112233", "python.exe",  false));

        String s = taskParamYamlUtils.toString(seq);


        TaskParamYaml seq1 = taskParamYamlUtils.toTaskYaml(s);
        Assert.assertEquals(seq, seq1);
    }
}
