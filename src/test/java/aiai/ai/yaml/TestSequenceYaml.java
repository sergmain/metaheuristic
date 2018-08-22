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

import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.snippet.SnippetType;
import aiai.ai.yaml.sequence.SequenceYaml;
import aiai.ai.yaml.sequence.SequenceYamlUtils;
import aiai.ai.yaml.sequence.SimpleSnippet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

public class TestSequenceYaml {

    @Test
    public void testSequenceYaml() {
        SequenceYaml seq = new SequenceYaml();
        seq.setDatasetId(1L);
        seq.setExperimentId(2L);
        Map<String, String> map = new HashMap<>();
        map.put("key1", "#1");
        map.put("key2", "#1");
        seq.setHyperParams(map);
        List<SimpleSnippet> list = new ArrayList<>();
        list.add(new SimpleSnippet(SnippetType.fit, "123", "file.txt", "112233"));
        list.add(new SimpleSnippet(SnippetType.predict, "456", "file.txt", "112233"));
        seq.setSnippets(list);

        String s = SequenceYamlUtils.toString(seq);


        SequenceYaml seq1 = SequenceYamlUtils.toSequenceYaml(s);
        Assert.assertEquals(seq, seq1);
    }
}
