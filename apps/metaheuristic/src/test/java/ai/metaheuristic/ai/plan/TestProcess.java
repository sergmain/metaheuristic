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
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;

import static ai.metaheuristic.api.data.plan.PlanParamsYaml.PlanYaml;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestProcess {

    @Test
    public void testProcessMeta() {
        PlanParamsYaml.Process p = new PlanParamsYaml.Process();

        p.metas.addAll(
                Arrays.asList(
                        new Meta("assembled-raw", "assembled-raw", null),
                        new Meta("dataset", "dataset-processing", null),
                        new Meta("feature", "feature", null)
                )
        );
        PlanParamsYaml planParamsYaml = new PlanParamsYaml();
        PlanYaml planYaml = new PlanYaml();
        planYaml.code = "test-process-for-meta";
        planYaml.processes.add(p);
        planParamsYaml.plan = planYaml;

        String s = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(planParamsYaml);
        PlanParamsYaml planParams = PlanParamsYamlUtils.BASE_YAML_UTILS.to(s);
        PlanYaml planYamlV21 = planParams.plan;

        PlanParamsYaml.Process p1 = planYamlV21.getProcesses().get(0);

        assertNotNull(p.getMeta("dataset"));
        assertEquals("dataset-processing", p.getMeta("dataset").getValue());

        assertNotNull(p.getMeta("assembled-raw"));
        assertEquals("assembled-raw", p.getMeta("assembled-raw").getValue());

        assertNotNull(p.getMeta("feature"));
        assertEquals("feature", p.getMeta("feature").getValue());
    }

}
