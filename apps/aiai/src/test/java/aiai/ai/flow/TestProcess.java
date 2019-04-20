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

package aiai.ai.flow;

import aiai.api.v1.launchpad.Process;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.flow.FlowYamlUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestProcess {

    @Autowired
    public FlowYamlUtils flowYamlUtils;

    @Test
    public void testProcessMeta() {
        Process p = new Process();

        p.metas.addAll(
                Arrays.asList(
                        new Process.Meta("assembled-raw", "assembled-raw", null),
                        new Process.Meta("dataset", "dataset-processing", null),
                        new Process.Meta("feature", "feature", null)
                )
        );
        FlowYaml flowYaml = new FlowYaml();
        flowYaml.processes.add(p);

        String s = flowYamlUtils.toString(flowYaml);

        FlowYaml flowYaml1 = flowYamlUtils.toFlowYaml(s);

        Process p1 = flowYaml1.getProcesses().get(0);

        assertNotNull(p.getMeta("dataset"));
        assertEquals("dataset-processing", p.getMeta("dataset").getValue());

        assertNotNull(p.getMeta("assembled-raw"));
        assertEquals("assembled-raw", p.getMeta("assembled-raw").getValue());

        assertNotNull(p.getMeta("feature"));
        assertEquals("feature", p.getMeta("feature").getValue());
    }

}
