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

import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class TestPlanYamlYaml {

    @Test
    public void testVersion() {
        assertEquals( new PlanParamsYaml().version, PlanParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion() );
    }

    @Test
    public void testYaml() {
        String yaml = PreparingPlan.getPlanV8();
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

        planYaml.processes = Collections.singletonList(p1);

        PlanParamsYaml planParamsYaml = new PlanParamsYaml();
        planParamsYaml.plan = planYaml;

        String s = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(planParamsYaml);

        System.out.println(s);
    }
}
