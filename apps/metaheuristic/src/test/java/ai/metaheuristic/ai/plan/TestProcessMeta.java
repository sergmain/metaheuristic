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
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtilsFactory;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.Meta;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.process.Process;
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
        PlanApiData.PlanYaml planYaml = new PlanApiData.PlanYaml();
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
        PlanApiData.PlanParamsYaml planParamsYaml = new PlanApiData.PlanParamsYaml();
        planParamsYaml.planYaml = planYaml;
        planParamsYaml.version = PlanParamsYamlUtilsFactory.DEFAULT_UTILS.getVersion();


        String s = PlanParamsYamlUtils.toString(planParamsYaml);

        System.out.println(s);

        PlanApiData.PlanParamsYaml planParams = PlanParamsYamlUtils.to(s);
        PlanApiData.PlanYaml yaml1 = planParams.planYaml;

        Assert.assertEquals(planYaml, yaml1);

    }

}
