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

import aiai.ai.yaml.plan.PlanYaml;
import aiai.ai.yaml.plan.PlanYamlUtils;
import aiai.api.v1.launchpad.Process;
import aiai.api.v1.EnumsApi;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestPlanYamlYaml {

    @Autowired
    private PlanYamlUtils planYamlUtils;

    @Test
    public void testYaml() {
        PlanYaml planYaml = new PlanYaml();
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.snippetCodes = Collections.singletonList("snippet-01:1.1");
            p.collectResources = true;
            p.outputType = "assembled-raw";

            planYaml.processes.add(p);
        }
        // output resource code: plan-10-assembly-raw-file-snippet-01
        //
        // input resource:
        // - code: plan-10-assembly-raw-file-snippet-01
        //   type: assembled-raw
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippetCodes = Collections.singletonList("snippet-02:1.1");
            p.collectResources = true;
            p.outputType = "dataset-processing";

            planYaml.processes.add(p);
        }
        // output resource code: plan-10-dataset-processing-snippet-02
        //
        // input resource:
        // - code: plan-10-assembly-raw-file-snippet-01
        //   type: assembled-raw
        // - code: plan-10-dataset-processing-snippet-02
        //   type: dataset-processing
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "feature processing";
            p.code = "feature-processing";

            p.snippetCodes = Arrays.asList("snippet-03:1.1", "snippet-04:1.1", "snippet-05:1.1");
            p.parallelExec = true;
            p.collectResources = true;
            p.outputType = "feature";

            planYaml.processes.add(p);
        }
        // output resource code: plan-10-feature-processing-snippet-03
        // output resource code: plan-10-feature-processing-snippet-04
        // output resource code: plan-10-feature-processing-snippet-05
        //
        // input resource:
        // - code: plan-10-assembly-raw-file-snippet-01
        //   type: assembled-raw
        // - code: plan-10-dataset-processing-snippet-02
        //   type: dataset-processing
        // - code: plan-10-feature-processing-snippet-03
        //   type: feature
        // - code: plan-10-feature-processing-snippet-04
        //   type: feature
        // - code: plan-10-feature-processing-snippet-05
        //   type: feature
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.EXPERIMENT;
            p.name = "experiment";
            p.code = "experiment-code-01";

            planYaml.processes.add(p);
        }

        String yaml = planYamlUtils.toString(planYaml);
        System.out.println(yaml);

        PlanYaml f1 = planYamlUtils.toPlanYaml(yaml);
        assertNotNull(f1);
        assertNotNull(f1.processes);
        assertFalse(f1.processes.isEmpty());
    }

    @Test
    public void testYaml_2() {
        PlanYaml planYaml = new PlanYaml();

        Process p1 = new Process();
        p1.name="experiment";
        p1.collectResources = false;

        p1.type = EnumsApi.ProcessType.EXPERIMENT;

        planYaml.processes = Collections.singletonList(p1);

        String s = planYamlUtils.toString(planYaml);

        System.out.println(s);

//        TaskParamYaml seq1 = TaskParamYamlUtils.toTaskYaml(s);
//        Assert.assertEquals(plan, seq1);
    }
}
