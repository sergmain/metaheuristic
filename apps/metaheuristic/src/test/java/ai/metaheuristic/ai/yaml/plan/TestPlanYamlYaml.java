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

package ai.metaheuristic.ai.yaml.plan;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class TestPlanYamlYaml {

    @Test
    public void testVersion() {
        assertEquals( new PlanParamsYaml().version, PlanParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion() );
    }

    @Test
    public void testYaml() {
        PlanParamsYaml.PlanYaml planYaml = new PlanParamsYaml.PlanYaml();
        {
            PlanParamsYaml.Process p = new PlanParamsYaml.Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.snippets = Collections.singletonList(new PlanParamsYaml.SnippetDefForPlan("snippet-01:1.1"));
            p.output.add(new PlanParamsYaml.Variable(EnumsApi.DataSourcing.launchpad, "assembled-raw"));

            planYaml.processes.add(p);
        }
        // output resource code: plan-10-assembly-raw-file-snippet-01
        //
        // input resource:
        // - code: plan-10-assembly-raw-file-snippet-01
        //   type: assembled-raw
        {
            PlanParamsYaml.Process p = new PlanParamsYaml.Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippets = Collections.singletonList(new PlanParamsYaml.SnippetDefForPlan("snippet-02:1.1"));
            p.output.add(new PlanParamsYaml.Variable(EnumsApi.DataSourcing.launchpad, "dataset-processing"));

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
            PlanParamsYaml.Process p = new PlanParamsYaml.Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "feature processing";
            p.code = "feature-processing";

            p.snippets = Arrays.asList(new PlanParamsYaml.SnippetDefForPlan("snippet-03:1.1"), new PlanParamsYaml.SnippetDefForPlan("snippet-04:1.1"), new PlanParamsYaml.SnippetDefForPlan("snippet-05:1.1"));
            p.parallelExec = true;
            p.output.add(new PlanParamsYaml.Variable(EnumsApi.DataSourcing.launchpad, "feature"));

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
            PlanParamsYaml.Process p = new PlanParamsYaml.Process();
            p.type = EnumsApi.ProcessType.EXPERIMENT;
            p.name = "experiment";
            p.code = "experiment-code-01";
            p.output.add(new PlanParamsYaml.Variable(EnumsApi.DataSourcing.launchpad, "model"));

            planYaml.processes.add(p);
        }

        PlanParamsYaml planParamsYaml = new PlanParamsYaml();
        planYaml.code = "test-processes";
        planParamsYaml.plan = planYaml;

        String yaml = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(planParamsYaml);
        System.out.println(yaml);

        assertFalse(yaml.startsWith("!!"));


        PlanParamsYaml planParams = PlanParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        PlanParamsYaml.PlanYaml py = planParams.plan;

        assertNotNull(py);
        assertNotNull(py.processes);
        assertFalse(py.processes.isEmpty());
    }

    @Test
    public void testYaml_2() {
        PlanParamsYaml.PlanYaml planYaml = new PlanParamsYaml.PlanYaml();

        PlanParamsYaml.Process p1 = new PlanParamsYaml.Process();
        p1.name="experiment";

        p1.type = EnumsApi.ProcessType.EXPERIMENT;

        planYaml.processes = Collections.singletonList(p1);

        PlanParamsYaml planParamsYaml = new PlanParamsYaml();
        planParamsYaml.plan = planYaml;

        String s = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(planParamsYaml);

        System.out.println(s);
    }
}
