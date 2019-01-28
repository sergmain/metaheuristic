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
package aiai.ai.yaml.task_params_new;

import aiai.ai.Consts;
import aiai.ai.yaml.TestYamlParser;
import aiai.ai.yaml.env.EnvYaml;
import aiai.ai.yaml.env.EnvYamlUtils;
import aiai.ai.yaml.task.SimpleSnippet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.Assert.*;

public class TestTaskParamYaml {

    private TaskParamYamlNewUtils taskParamYamlUtils = new TaskParamYamlNewUtils();

    @Test
    public void testSequenceYaml() {
        TaskParamNewYaml seq = new TaskParamNewYaml();

        seq.inputResourceCodes.put("type1", Collections.singletonList(new TaskResource("1", Consts.LAUNCHPAD_STORAGE_URL)));
        seq.inputResourceCodes.put("type2", Collections.singletonList(new TaskResource("2", Consts.LAUNCHPAD_STORAGE_URL)));
        seq.inputResourceCodes.put("type3", Collections.singletonList(new TaskResource("3", Consts.LAUNCHPAD_STORAGE_URL)));
        seq.outputResourceCode= new TaskResource("4", Consts.LAUNCHPAD_STORAGE_URL);
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key1", "#1");
        map.put("key2", "#1");
        seq.setHyperParams(map);
        seq.setSnippet(new SimpleSnippet("fit", "123", "file.txt", "112233", "python.exe",  false, false, " aaa bbb"));

        String s = TaskParamYamlNewUtils.toString(seq);
        System.out.println(s);

        TaskParamNewYaml seq1 = TaskParamYamlNewUtils.toTaskYamlNew(s);
        Assert.assertEquals(seq, seq1);
    }

    @Test
    public void testEnvYaml() throws IOException {

        TaskParamNewYaml yaml;
        try(InputStream is = TestYamlParser.class.getResourceAsStream("/yaml/task_params_new/params-new.yaml")) {
            yaml = TaskParamYamlNewUtils.to(is);
        }
        assertNotNull(yaml.inputResourceCodes);
        assertNotNull(yaml.outputResourceCode);
        assertFalse(yaml.inputResourceCodes.isEmpty());
        assertEquals(3, yaml.inputResourceCodes.size());

        List<TaskResource> trs;
        TaskResource tr;
        trs = yaml.inputResourceCodes.get("type1");
        assertEquals(1, trs.size());
        //noinspection ConstantConditions
        assertFalse(trs.get(0) instanceof TaskResource);
/*
        tr = trs.get(0);
        assertEquals("1", tr.resource);
        assertEquals(Consts.LAUNCHPAD_STORAGE_URL, tr.storageUrl);
*/


        System.out.println(TaskParamYamlNewUtils.toString(yaml));
    }
}
