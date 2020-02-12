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

package ai.metaheuristic.ai.yaml.source_code;

import ai.metaheuristic.ai.preparing.PreparingPlan;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class TestPlanYamlYaml {

    @Test
    public void testVersion() {
        assertEquals( new SourceCodeParamsYaml().version, SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion() );
    }

    @Test
    public void testYaml() {
        String yaml = PreparingPlan.getPlanV8();
        System.out.println(yaml);
        assertFalse(yaml.startsWith("!!"));
        SourceCodeParamsYaml planParams = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        SourceCodeParamsYaml.SourceCodeYaml py = planParams.source;

        assertNotNull(py);
        assertNotNull(py.processes);
        assertFalse(py.processes.isEmpty());
    }

    @Test
    public void testYaml_2() {
        SourceCodeParamsYaml.SourceCodeYaml sourceCodeYaml = new SourceCodeParamsYaml.SourceCodeYaml();

        SourceCodeParamsYaml.Process p1 = new SourceCodeParamsYaml.Process();
        p1.name="experiment";

        sourceCodeYaml.processes = Collections.singletonList(p1);

        SourceCodeParamsYaml sourceCodeParamsYaml = new SourceCodeParamsYaml();
        sourceCodeParamsYaml.source = sourceCodeYaml;

        String s = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(sourceCodeParamsYaml);

        System.out.println(s);
    }
}
