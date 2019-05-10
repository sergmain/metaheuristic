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
import aiai.api.v1.EnumsApi;
import aiai.api.v1.data.SnippetApiData;
import aiai.apps.commons.CommonConsts;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TestTaskParamYaml {

    @Test
    public void testTaskParamYaml() {
        TaskParamNewYaml seq = new TaskParamNewYaml();

        seq.inputResourceCodes.put("type1", Collections.singletonList(new TaskResource("1", Consts.SOURCING_LAUNCHPAD_PARAMS_STR)));
        seq.inputResourceCodes.put("type2", Collections.singletonList(new TaskResource("2", Consts.SOURCING_LAUNCHPAD_PARAMS_STR)));
        seq.inputResourceCodes.put("type3", Collections.singletonList(new TaskResource("3", Consts.SOURCING_LAUNCHPAD_PARAMS_STR)));
        seq.outputResourceCode= new TaskResource("4", Consts.SOURCING_LAUNCHPAD_PARAMS_STR);
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key1", "#1");
        map.put("key2", "#1");
        seq.setHyperParams(map);

        SnippetApiData.SnippetConfig sc = new SnippetApiData.SnippetConfig();
        sc.type = CommonConsts.FIT_TYPE;
        sc.code = "abc:1.0";
        sc.file = "file.txt";
        sc.checksum = "112233";
        sc.env = "python.exe";
        sc.metrics = false;
        sc.sourcing = EnumsApi.SnippetSourcing.launchpad;
        sc.params = " aaa bbb";
        seq.setSnippet(sc);

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
        trs = yaml.inputResourceCodes.get("type1");
        assertEquals(1, trs.size());
        //noinspection ConstantConditions
        assertFalse(trs.get(0) instanceof TaskResource);
/*
        TaskResource tr;
        tr = trs.get(0);
        assertEquals("1", tr.resource);
        assertEquals(Consts.LAUNCHPAD_STORAGE_URL, tr.storageUrl);
*/


        System.out.println(TaskParamYamlNewUtils.toString(yaml));
    }
}
