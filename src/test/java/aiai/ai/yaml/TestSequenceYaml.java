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

import aiai.ai.beans.StationExperimentSequence;
import aiai.ai.core.JsonUtils;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.snippet.SnippetType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestSequenceYaml {

    @Autowired
    private ExperimentService experimentService;

    @Test
    public void testSequenceYaml() {
        ExperimentService.SequenceYaml seq = new ExperimentService.SequenceYaml();
        seq.setDatasetId(1L);
        seq.setExperimentId(2L);
        Map<String, String> map = new HashMap<>();
        map.put("key1", "#1");
        map.put("key2", "#1");
        seq.setHyperParams(map);
        List<ExperimentService.SimpleSnippet> list = new ArrayList<>();
        list.add(new ExperimentService.SimpleSnippet(SnippetType.fit.toString(), "123", "file.txt", "112233"));
        list.add(new ExperimentService.SimpleSnippet(SnippetType.predict.toString(), "456", "file.txt", "112233"));
        seq.setSnippets(list);

        String s = experimentService.toString(seq);


        ExperimentService.SequenceYaml seq1 = experimentService.toSequenceYaml(s);
        Assert.assertEquals(seq, seq1);
    }
}
