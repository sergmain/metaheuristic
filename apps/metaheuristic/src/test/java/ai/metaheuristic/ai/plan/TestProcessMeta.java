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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.api.v1.data.Meta;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.launchpad.Process;
import ai.metaheuristic.ai.yaml.plan.PlanYamlUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("launchpad")
public class TestProcessMeta {

    @Autowired
    public PlanYamlUtils planYamlUtils;

    @Test
    public void testProcessMeta() {
        PlanApiData.PlanYaml planYaml = new PlanApiData.PlanYaml();
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.EXPERIMENT;
            p.name = "experiment";
            p.code = "test-experiment-code-01";
            p.outputParams = Consts.SOURCING_LAUNCHPAD_PARAMS;

            p.metas.addAll(
                    Arrays.asList(
                            new Meta("assembled-raw", "assembled-raw", null),
                            new Meta("dataset", "dataset-processing", null),
                            new Meta("feature", "feature", null)
                    )
            );

            planYaml.processes.add(p);
        }
        String s  = planYamlUtils.toString(planYaml);

        System.out.println(s);

        PlanApiData.PlanYaml yaml1 = planYamlUtils.toPlanYaml(s);

        Assert.assertEquals(planYaml, yaml1);

    }

}
