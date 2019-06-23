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

package ai.metaheuristic.ai.plan;

import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.process.Process;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("launchpad")
public class TestProcessMeta {

    @Test
    public void testProcessMeta() {
        PlanParamsYaml.PlanYaml planYaml = new PlanParamsYaml.PlanYaml();
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.EXPERIMENT;
            p.name = "experiment";
            p.code = "test-experiment-code-01";
            p.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);

            p.metas.addAll(
                    Arrays.asList(
                            new Meta("assembled-raw", "assembled-raw", null),
                            new Meta("dataset", "dataset-processing", null),
                            new Meta("feature", "feature", null)
                    )
            );

            planYaml.processes.add(p);
        }
        PlanParamsYaml planParamsYaml = new PlanParamsYaml();
        planParamsYaml.planYaml = planYaml;

        String s = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(planParamsYaml);

        System.out.println(s);

        PlanParamsYaml planParams = PlanParamsYamlUtils.BASE_YAML_UTILS.to(s);
        PlanParamsYaml.PlanYaml yaml1 = planParams.planYaml;

        Assert.assertEquals(planYaml, yaml1);

    }

}
