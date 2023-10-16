/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

import ai.metaheuristic.ai.preparing.PreparingSourceCode;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.commons.yaml.source_code.SourceCodeParamsYamlUtils;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TestPlanYamlYaml {

    @Test
    public void testVersion() {
        assertEquals( new SourceCodeParamsYaml().version, SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion() );
    }

    @Test
    public void testYaml() {
        String yaml = PreparingSourceCode.getSourceCodeV1();
        System.out.println(yaml);
        assertFalse(yaml.startsWith("!!"));
        SourceCodeParamsYaml planParams = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        SourceCodeParamsYaml.SourceCode py = planParams.source;

        assertNotNull(py);
        assertNotNull(py.processes);
        assertFalse(py.processes.isEmpty());
    }

    @Test
    public void testYaml_2() {
        SourceCodeParamsYaml.SourceCode sourceCodeYaml = new SourceCodeParamsYaml.SourceCode();
        sourceCodeYaml.uid = UUID.randomUUID().toString();

        SourceCodeParamsYaml.Process p1 = new SourceCodeParamsYaml.Process();
        p1.name="experiment";
        p1.function = new SourceCodeParamsYaml.FunctionDefForSourceCode();
        p1.function.code = "func-code";
        p1.function.context = EnumsApi.FunctionExecContext.external;

        sourceCodeYaml.processes = Collections.singletonList(p1);

        SourceCodeParamsYaml sourceCodeParamsYaml = new SourceCodeParamsYaml();
        sourceCodeParamsYaml.source = sourceCodeYaml;

        // (source != null && !S.b(source.uid) && source.processes != null)
        String s = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(sourceCodeParamsYaml);

        System.out.println(s);
    }
}
