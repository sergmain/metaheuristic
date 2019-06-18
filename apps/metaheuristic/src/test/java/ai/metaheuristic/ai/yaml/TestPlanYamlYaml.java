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

package ai.metaheuristic.ai.yaml;

import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.process.Process;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestPlanYamlYaml {

    @Test
    public void testYaml() {
        PlanParamsYaml.PlanYaml planYaml = new PlanParamsYaml.PlanYaml();
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.snippetCodes = Collections.singletonList("snippet-01:1.1");
            p.collectResources = true;
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            p.outputParams.storageType = "assembled-raw";

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
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            p.outputParams.storageType = "dataset-processing";

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
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            p.outputParams.storageType = "feature";

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
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);

            planYaml.processes.add(p);
        }

        PlanParamsYaml planParamsYaml = new PlanParamsYaml();
        planParamsYaml.planYaml = planYaml;

        String yaml = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(planParamsYaml);
        System.out.println(yaml);

        PlanParamsYaml planParams = PlanParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        PlanParamsYaml.PlanYaml py = planParams.planYaml;

        assertNotNull(py);
        assertNotNull(py.processes);
        assertFalse(py.processes.isEmpty());
    }

    @Test
    public void testYaml_2() {
        PlanParamsYaml.PlanYaml planYaml = new PlanParamsYaml.PlanYaml();

        Process p1 = new Process();
        p1.name="experiment";
        p1.collectResources = false;

        p1.type = EnumsApi.ProcessType.EXPERIMENT;

        planYaml.processes = Collections.singletonList(p1);

        PlanParamsYaml planParamsYaml = new PlanParamsYaml();
        planParamsYaml.planYaml = planYaml;

        String s = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(planParamsYaml);

        System.out.println(s);
    }
}
